(ns event-data-investigator.core
  (:require [event-data-investigator.checks :as checks]
            [taoensso.timbre :as timbre]
            [event-data-investigator.tools.check-kafka-logs :as check-kafka-logs]
            [clojure.tools.logging :as log]))

(timbre/merge-config!
    {:ns-blacklist [
       ; Twitter client.
       "com.ning.http.client.providers.netty.*"]
     :level :info})


(defn -main
  [& args]
  (let [command (first args)]
    (condp = command
      "scheduled-checks" (checks/start-schedule)

      ;; Manual tools
      ; lein run check-kafka-logs-evidence-records /path/to/data-dir /path/to/output topic-name
      "check-kafka-logs-evidence-records" (apply check-kafka-logs/evidence-records (rest args))

      ; lein run check-kafka-logs-evidence-log /path/to/data-dir /path/to/output topic-name
      "check-kafka-logs-evidence-log" (apply check-kafka-logs/evidence-log (rest args))

      (log/error "Didn't recognise command:" command))))


