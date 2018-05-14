(ns event-data-investigator.patches.patch-2018-05-02-evidence-record-id
  "Update incorrect Evidence Record fields in some Twitter Events to complete URLs."
  (:require [clojure.tools.logging :as log]
            [event-data-common.query-api :as query]
            [event-data-common.event-bus :as bus]
            [event-data-investigator.util :as util]
            [clj-time.core :as clj-time]
            [clojure.string :as string]))

(def reason "https://evidence.eventdata.crossref.org/announcements/2018-04-26T00-00-00Z-ED-16.json")

(def evidence-base "https://evidence.eventdata.crossref.org/evidence/")

(defn valid-evidence-record-value
  "Identify an Evidence Record value that needs fixing."
  [value]
    ; If not present, that's not an error, as it's not compulsory.
    (if-not
      value
      true
      (try (java.net.URL. value)
        true
        (catch java.net.MalformedURLException _
          false))))

(defn normalize-evidence-record-value
  [value]
  (str evidence-base
       (-> value (string/split #"/") last)))

(defn update-event
  "Patch an Event if it needs it."
  [event]
  (when-let [value (:evidence_record event)]
    (when (-> value valid-evidence-record-value not)
      (assoc event
        :evidence_record (normalize-evidence-record-value value)
        :updated "edited"
        :updated_reason reason
        :updated_date (util/->yyyy-mm-dd-hh-mm-ss (clj-time/now))))))


(def twitter-jwt (event-data-common.jwt/sign @bus/jwt-verifier {"sub" "twitter"}))

(defn run
  []
  (let [jwt twitter-jwt
        normal-events (query/fetch-query-api "/v1/events" {:source "twitter" :until-collected-date "2017-02-25"} :events)
        deleted-events (query/fetch-query-api "/v1/events/deleted" {:source "twitter"  :until-collected-date "2017-02-25"} :events)
        events (concat normal-events deleted-events)
        updated-events (keep update-event events)
        counter (atom 0)]

    (dorun
      (pmap
        (fn [event]
          (log/debug "Send event ID:" (:id event))
          (bus/send-event event jwt)
          (swap! counter inc)
          (when (zero? (rem @counter 100))
            (log/info "Done" @counter "...")))
        updated-events))

    (log/info "Finished updating" @counter "Events")))

