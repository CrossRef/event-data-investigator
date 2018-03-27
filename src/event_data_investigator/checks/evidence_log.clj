(ns event-data-investigator.checks.evidence-log
  "Check that the Evidence Log is in place."
  (:require [event-data-investigator.util :as util]
            [event-data-investigator.issue-tracking :as issue-tracking]
            [clj-http.client :as client]

            [clojure.tools.logging :as log]
            [event-data-common.date :as date]
            [event-data-common.checkpoint :as checkpoint]
            [clj-time.core :as clj-time]
            [event-data-common.evidence-log :as evidence-log]))

(def evidence-log-base
  "Public URL for Evidence Registry."
  "https://evidence.eventdata.crossref.org")

(defn check
  "Check the presence of both Evidence Logs."
  [the-date]
  (let [csv-present (-> the-date
                        date/->yyyy-mm-dd
                        (#(str evidence-log-base "/log/" % ".csv"))
                        (client/head {:throw-exceptions false})
                        :status #{200})

        json-present (-> the-date
                        date/->yyyy-mm-dd
                        (#(str evidence-log-base "/log/" % ".txt"))
                        (client/head {:throw-exceptions false})
                        :status #{200})]

    {:csv-present csv-present
     :json-present json-present}))

(defn check-and-report
  "Check integrity, report in the Evidence Log."
  [the-date]
  (evidence-log/log!
   {:i "q0005"
    :s "quality"
    :c "evidence-log"
    :f "check"
    :p (date/->yyyy-mm-dd the-date)})

  (let [{csv-present :csv-present
         json-present :json-present} (check the-date)]

    (evidence-log/log!
      {:i "q0006"
       :s "quality"
       :c "evidence-log"
       :f "csv-present"
       :p (date/->yyyy-mm-dd the-date)
       :e (if csv-present "t" "f")})

    (evidence-log/log!
      {:i "q0007"
       :s "quality"
       :c "evidence-log"
       :f "json-present"
       :p (date/->yyyy-mm-dd the-date)
       :e (if json-present "t" "f")})

    ; In addition to this, raise issues if there are problems.
    (when-not csv-present
      (issue-tracking/raise
        (format "Evidence Log CSV dump missing for %s" (date/->yyyy-mm-dd the-date))
        (format "Couldn't find the Evidence Log CSV dump for %s on %s"
                (date/->yyyy-mm-dd the-date)
                (date/->yyyy-mm-dd (clj-time/now)))
        [:data-integrity]))

    (when-not json-present
      (issue-tracking/raise
        (format "Evidence Log JSON dump missing for %s" (date/->yyyy-mm-dd the-date))
        (format "Couldn't find the Evidence Log JSON dump for %s on %s"
                (date/->yyyy-mm-dd the-date)
                (date/->yyyy-mm-dd (clj-time/now)))
        [:data-integrity]))))

(defn run
  []
  (doseq [day (util/all-days-since-collected-epoch)]
    (let [day-str (date/->yyyy-mm-dd day)]
      (checkpoint/run-once-checkpointed!
        ["investigator" "evidence-log" day-str]
        (fn []
          (check-and-report day))))))

(def manifest
  {:schedule [[run (clj-time/hours 1)]]})
