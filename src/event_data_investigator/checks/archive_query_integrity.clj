(ns event-data-investigator.checks.archive-query-integrity
  "Compare events in the Query API with the Event Bus Archive for integrity.
   Runs two checks:
    - Missing from Query compared to Archive, i.e. the indexing missed some.
    - Missing from Archive compared to Query, i.e. the Event Bus failed somehow."
  (:require [event-data-investigator.util :as util]
            [event-data-investigator.issue-tracking :as issue-tracking]

            [clojure.tools.logging :as log]
            [clojure.string :as string]
            [event-data-common.query-api :as query]
            [event-data-common.event-bus :as bus]
            [event-data-common.date :as date]
            [event-data-common.checkpoint :as checkpoint]
            [event-data-common.whitelist :as whitelist]
            [clj-time.core :as clj-time]
            [event-data-common.evidence-log :as evidence-log]

            [clojurewerkz.quartzite.triggers :as t]
            [clojurewerkz.quartzite.jobs :as j]
            [clojurewerkz.quartzite.jobs :refer [defjob]]
            [clojurewerkz.quartzite.schedule.daily-interval
             :refer
             [schedule starting-daily-at time-of-day on-every-day]]))

(defn check
  "Check the integrity for a given day. Return missing Event IDs."
  [the-date]
  (let [; Retrieve the individual Event IDs from the two locations.
        bus-ids (set (bus/event-ids-for-day the-date))
        query-ids (set (query/event-ids-for-day the-date))

        ; Everything in the Query API should be in the Archive.
        bus-missing (clojure.set/difference query-ids bus-ids)

        ; But the Query API doesn't serve up everything. 
        ; Run the whitelist to make sure missing Events not present in the Query API
        ; really should be missing.
        bus-not-query (clojure.set/difference bus-ids query-ids)

        ; Retrieve each Event.
        events-from-bus (pmap bus/get-event bus-not-query)

        ; Check whether, under today's rules, it should be present.
        whitelisted (whitelist/filter-events events-from-bus)

        query-missing (map :id whitelisted)]

    {:query-missing query-missing
     :bus-missing bus-missing}))

(defn check-and-report
  "Check integrity, report in the Evidence Log."
  [the-date]
  (evidence-log/log!
   {:i "q0001"
    :s "quality"
    :c "archive-query-integrity"
    :f "start"
    :p (date/->yyyy-mm-dd the-date)})

  (let [{query-missing :query-missing
         bus-missing :bus-missing} (check the-date)]

    ; Log to say we finished the day's scan.
    (evidence-log/log!
      {:i "q0002"
       :s "quality"
       :c "archive-query-integrity"
       :f "finish"
       :p (date/->yyyy-mm-dd the-date)})

    ; Log to record number missing from the Query API.
    (evidence-log/log!
      {:i "q0003"
       :s "quality"
       :c "archive-query-integrity"
       :f "missing-from-query"
       :p (date/->yyyy-mm-dd the-date)
       :v (count query-missing)
       :e (if (seq query-missing) "f" "t")})

    ; Log to record number missing from the Bus Archive.
    (evidence-log/log!
      {:i "q0004"
       :s "quality"
       :c "archive-query-integrity"
       :f "missing-from-archive"
       :p (date/->yyyy-mm-dd the-date)
       :v (count bus-missing)
       :e (if (seq bus-missing) "f" "t")})

    ; In addition to this, raise issues if there are problems.
    (when (seq query-missing)
      (issue-tracking/raise
        (format "Query Integrity for %s" (date/->yyyy-mm-dd the-date))
        (format "When checking the Query API index for %s on %s there were %s Events missing from the API. \n Here's the first 50: \n%s"
                (date/->yyyy-mm-dd the-date)
                (date/->yyyy-mm-dd (clj-time/now))
                (count query-missing)
                (string/join "\n" (take 50 query-missing)))
        [:data-integrity]))

    (when (seq bus-missing)
      (issue-tracking/raise
        (format "Bus Integrity for %s" (date/->yyyy-mm-dd the-date))
        (format "When checking the Bus Archive for %s on %s there were %s Events missing from the Archive. \n Here's the first 50: \n%s"
                (date/->yyyy-mm-dd the-date)
                (date/->yyyy-mm-dd (clj-time/now))
                (count bus-missing)
                (string/join "\n" (take 50 bus-missing)))
        [:data-integrity]))))

(defn run
  []
  (doseq [day (util/all-days-since-collected-epoch)]
    (let [day-str (date/->yyyy-mm-dd day)]
      (checkpoint/run-once-checkpointed!
        ["investigator" "archive-query-integrity" day-str]
        (fn []
          (check-and-report day))))))

(def manifest
  {:schedule [[run (clj-time/hours 1)]]})
