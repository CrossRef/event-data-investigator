(ns event-data-investigator.checks.evidence-record-snapshot
  "Check that the Evidence Record input snapshot is in place."
  (:require [event-data-investigator.util :as util]
            [event-data-investigator.issue-tracking :as issue-tracking]
            [clj-http.client :as client]
            [clojure.tools.logging :as log]
            [event-data-common.date :as date]
            [event-data-common.checkpoint :as checkpoint]
            [event-data-common.storage.s3 :as s3]
            ; [event-data-common.storage.store :as store]
            [config.core :refer [env]]
            [clj-time.core :as clj-time]
            [event-data-common.evidence-log :as evidence-log]))

(def connection
  (delay
    (s3/build (:status-snapshot-s3-key env)
              (:status-snapshot-s3-secret env)
              (:status-snapshot-s3-region-name env)
              (:status-snapshot-s3-bucket-name env))))

(defn check
  "Check the presence of both Evidence Logs."
  [the-date]
  (let [client (:client @connection)
        bucket-name (:status-snapshot-s3-bucket-name env)
        txt-s3-file-path (str "evidence-input/" (date/->yyyy-mm-dd the-date) ".txt")]
    {:snapshot-present (.doesObjectExist client bucket-name txt-s3-file-path)}))

(defn check-and-report
  "Check integrity, report in the Evidence Log."
  [the-date]

  (let [{snapshot-present :snapshot-present} (check the-date)]
    (evidence-log/log!
      {:i "q0008"
       :s "quality"
       :c "evidence-record-snapshot"
       :f "snapshot-present"
       :p (date/->yyyy-mm-dd the-date)
       :e (if snapshot-present "t" "f")})

    ; In addition to this, raise issues if there are problems.
    (when-not snapshot-present
      (issue-tracking/raise
        (format "Evidence Record Snapshot dump missing for %s" (date/->yyyy-mm-dd the-date))
        (format "Couldn't find the Evidence Record Snapshot dump for %s on %s"
                (date/->yyyy-mm-dd the-date)
                (date/->yyyy-mm-dd (clj-time/now)))
        [:data-integrity]))))

(defn run
  []
  (doseq [day (util/all-days-since-collected-epoch)]
    (let [day-str (date/->yyyy-mm-dd day)]
      (checkpoint/run-once-checkpointed!
        ["investigator" "evidence-record-snapshot" day-str]
        (fn []
          (check-and-report day))))))

(def manifest
  {:schedule [[run (clj-time/hours 1)]]})
