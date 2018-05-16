(ns event-data-investigator.checks
  (:require [event-data-investigator.checks.archive-query-integrity :as archive-query-integrity]
            [event-data-investigator.checks.evidence-log :as evidence-log]
            [event-data-investigator.checks.evidence-record-snapshot :as evidence-record-snapshot]
            [event-data-investigator.checks.twitter-compliance :as twitter-compliance]
            [overtone.at-at :as at-at]
            [clojure.tools.logging :as log]
            [clj-time.core :as clj-time]))

(def manifests
  {:archive-query-integrity archive-query-integrity/manifest
   :evidence-log evidence-log/manifest
   :evidence-record-snapshot evidence-record-snapshot/manifest
   :twitter-compliance twitter-compliance/manifest})

(def schedule-pool (at-at/mk-pool))

(defn start-schedule
  "Run the schedule for the given string Agent names.
   at-at starts a daemon thread which will block exit."
  []
  (log/info "Start scheduler...")

  (doseq [[check-name check-manifest] manifests]
    (log/info "Adding schedule for" check-name)
    (doseq [[function delay-period] (:schedule check-manifest)]
      (log/info "Add schedule" function "with delay" (str delay-period))
      (at-at/interspaced
       (clj-time/in-millis delay-period)
       #(try
         (function)
         (catch Exception e
          (do
            (log/error "Exception running " check-name)
            (.printStackTrace e))))
        schedule-pool)))

  (log/info "Finished setting up scheduler."))

