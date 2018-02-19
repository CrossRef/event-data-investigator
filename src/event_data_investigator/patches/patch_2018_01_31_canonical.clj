(ns event-data-investigator.patches.patch-2018-01-31-canonical
  "Update all old Wikipedia Events to use the canonical URL as the subj-id. Swap the subj_id and subj.url."
  (:require [clojure.tools.logging :as log]
            [event-data-investigator.query :as query]
            [event-data-investigator.bus :as bus]))

(def reason "https://evidence.eventdata.crossref.org/announcements/2018-01-19T00-00-00Z-ED-15.json")
(def updated-date "2018-01-31T17:30:00Z")

(defn update-event
  [event]
  ; Only old-style events with the version number in the subj-id apply.
  ; Newer ones, as of approx 2018-01-16, are correct.
  (when-not (:updated event)
    (if-not
      (.contains (-> event :subj_id) "w/index.php")
      (do
        (log/info "Skip" (:id event))
        nil)
      (do
        (log/info "subj_id is" (-> event :subj_id) (.contains (-> event :subj_id) "w/index.php"))
        (assoc event
          :subj_id (-> event :subj :url)
          :subj (assoc
                 (:subj event)
                 :pid (-> event :subj :url)
                 :url (-> event :subj :pid))
          :updated "edited"
          :updated_reason reason
          :updated_date updated-date)))))

(defn run
  []
  (let [jwt (bus/build-jwt "wikipedia")
        events (query/fetch-query-api "source:wikipedia,relation-type:references")
        ; update-event may return nil if there's no change.
        ; Skip these.
        updated-events (keep update-event events)]
    (dorun
      (pmap
        (fn [event]
          (log/debug "Send event ID:" (:id event))
          (bus/send-event event jwt))
        updated-events))))
