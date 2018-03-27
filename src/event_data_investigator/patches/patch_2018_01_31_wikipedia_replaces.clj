(ns event-data-investigator.patches.patch-2018-01-31-wikipedia-replaces
  "Remove all of the 'replaces' Wikipedia Events as they don't fit the new model."
  (:require [clojure.tools.logging :as log]
            [event-data-investigator.query :as query]
            [event-data-investigator.bus :as bus]))

(def reason "https://evidence.eventdata.crossref.org/announcements/2018-01-19T00-00-00Z-ED-15.json")
(def updated-date "2018-01-31T15:30:00Z")

(defn update-event
  [event]
  (assoc event
    :updated :deleted
    :updated_reason reason
    :updated_date updated-date))

(defn run
  []
  (let [jwt (bus/build-jwt "wikipedia")
        events (query/fetch-query-api "/v1/events" {:source "wikipedia" :relation-type "replaces"} :events)
        updated-events (keep update-event events)]
    (dorun
      (pmap
        (fn [event]
          (log/debug "Send event ID:" (:id event))
          (bus/send-event event jwt))
        updated-events))))

