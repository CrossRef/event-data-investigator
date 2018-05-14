(ns event-data-investigator.patches.patch-2018-05-09-evidence-record-twitter-compliance
  "For Twitter Events that have been deleted in the legacy compliance framework,
   ensure that Evidence Records have also been cleaned up."
  (:require [clojure.tools.logging :as log]
            [event-data-investigator.evidence-record :as evidence-record]
            [event-data-common.query-api :as query]
            [clj-time.core :as clj-time]
            [clojure.string :as string]))

(def reason
  "A static URL that describes the Twitter compliance activity"
  "https://evidence.eventdata.crossref.org/announcements/2017-05-08T08-41-00Z-CED-9.json")

(defn run
  "Retrieve already deleted (i.e. already patched) Events.
   Prior to the patch, all Events *might* have had an un-updated Evidence Record.
   So run these patched Events through the second half of the compliance process 
   (i.e. update the Evidence Record to include the new Event)"
  []
  (let [counter (atom 0)
        events (query/fetch-query-api
                 "/v1/events/deleted"
                 {:source "twitter"
                  :from-collected-date "2017-01-01"} :events)

        ; Early experimental Events may not have one. In which case there's no problem.
        events-with-evidence-record (filter :evidence_record events)

        ; Double-check that we're getting only deleted Events.
        deleted (filter #(= "deleted" (:updated %)) events-with-evidence-record)]

    (doseq [patched-event deleted]
      (swap! counter inc)
      (log/info (:id patched-event))
      (evidence-record/patch-evidence-record-in-storage! patched-event)
      (Thread/sleep 100)
      (when (zero? (rem @counter 100))
        (log/info "Patched" @counter "Events' Evidence Records"))
        
    (log/info "Finished updating" @counter "Events"))))

