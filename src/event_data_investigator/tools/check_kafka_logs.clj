(ns event-data-investigator.tools.check-kafka-logs
  "Look in a dump of Kafka logs. For use in emergencies, when Kafka has been re-started from a cold configuration."
  (:require [config.core :refer [env]]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [org.httpkit.client :as http]
            [com.climate.claypoole :refer [pdoseq]]
            [event-data-investigator.evidence-record :refer [evidence-record-store]]
            [event-data-common.storage.s3 :refer [get-aws-client]]
            [clojure.core.async :refer [>!! <!! chan thread close!]] )
  (:import [java.io File]
           [javax.net.ssl SNIHostName SNIServerName SSLEngine SSLParameters]
           [java.net URI]))

(def s3-client
  (delay (.client @evidence-record-store)))

(defn evidence-record-exists
  [id]
  (.doesObjectExist @s3-client (:percolator-evidence-bucket-name env) (str "evidence/" id)))

(def parallelism 512)

(defn scan-topic-messages
  "Given a locally accessible Kafka 'data' directory, callback for each messsage in the given topic"
  [kafka-data-dir-str topic-name callback]

  (let [all-topic-dirs (file-seq (File. kafka-data-dir-str))

        ; Each partition is a directory with the topic prefix and the partition number suffix.
        topic-dirs (filter #(.startsWith (.getName %) topic-name) all-topic-dirs)

        log-files (->> topic-dirs
                       (mapcat file-seq)
                       (filter #(.endsWith (.getName %) ".log")))]

    (log/info "Looking in data dir:" kafka-data-dir-str)
    (log/info "Found" (count topic-dirs) "topic partitions")
    (log/info "Found" (count log-files) "log files")

    ; Each log file in parallel. This gives better performance than parallelizing within the log file.
    ; It does mean that if log files are unequally long, processing will appear to slow toward the end.
    (pdoseq parallelism [file log-files]
      (let [records (org.apache.kafka.common.record.FileRecords/open file false)
            batches (.batches records)]
        (log/info "Process" (.getAbsolutePath file))
        (doseq [batch batches]
          (doseq [message batch]
            (let [message-bytes (org.apache.kafka.common.utils.Utils/readBytes (.value message))]
              (callback message-bytes))))))))

   

(defn evidence-records
  "Given a locally accessible Kafka 'data' directory, an output directory, and a topic that has Evidence Records,
   write out one file per Evidence Record that hasn't already been processed."
  [kafka-data-dir-str output-dir-str topic-name]

  (when-not kafka-data-dir-str
    (log/fatal "Expected kafka data directory as first argument."))

  (when-not output-dir-str
    (log/fatal "Expected output directory as second argument."))

  (when-not topic-name
    (log/fatal "Expected Evidence Record Input Kafka topic name as third argument."))

  (log/info "Writing to output dir:" output-dir-str)

  (let [exists-count (atom 0)
        not-exists-count (atom 0)
        total-count (atom 0)
        output-dir (File. output-dir-str)
        callback
        (fn [message-bytes]

          ; JSON is defined to use UTF-8 so we always know the encoding.
          (let [parsed (json/read (io/reader message-bytes) :key-fn keyword)
                id (:id parsed)

                exists (evidence-record-exists id)]

                (swap! total-count inc)
                (swap! (if exists exists-count not-exists-count) inc)

                (when (zero? (rem @total-count 100))
                  (log/info "Ok:" @exists-count "Not ok:" @not-exists-count "total:" @total-count))

                (when-not exists
                  (with-open [out (io/writer (File. output-dir id))]
                    (io/copy (io/reader message-bytes) out)))))]
      (scan-topic-messages kafka-data-dir-str topic-name callback)))

(defn evidence-log
  "Write all Evidence Log lines out to a newline-delimited JSON file."
  [kafka-data-dir-str output-filepath-str topic-name]
  (when-not kafka-data-dir-str
    (log/fatal "Expected kafka data directory as first argument."))

  (when-not output-filepath-str
    (log/fatal "Expected output filepath as second argument."))

  (when-not topic-name
    (log/fatal "Expected Evidence Record Input Kafka topic name as third argument."))

  (log/info "Writing to output file:" output-filepath-str)

  (let [total-count (atom 0)
        channel (chan 1024)]

    (thread
      (scan-topic-messages
        kafka-data-dir-str
        topic-name
        (partial >!! channel))
      (close! channel))

    (with-open [out (io/writer (File. output-filepath-str))]
      (loop [message-bytes (<!! channel)]
        (when message-bytes
          (swap! total-count inc)
          (when (zero? (rem @total-count 10000))
          (log/info "Written" @total-count "lines"))

          (io/copy (io/reader message-bytes) out)
          (.write out "\n")

          (recur (<!! channel)))))))
