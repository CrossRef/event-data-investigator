(ns event-data-investigator.checks.twitter-compliance
  "Check Events for Twitter compliance. Delete Events if the Tweet has been deleted.
   Each date's worth of Twitter Events are checked in one block. The check happens three times:
   - immediate - the day after
   - midterm - after a week
   - longterm - after a year

   Each day / check-type combination is checkpointed. This check runs once a day and ensures that 
   all checks that should have run, have run (to allow for missed days to be covered reliably).

   When an Event is found for which the Tweet has been deleted, the subj-id (tweet id) is replaced, 
   removing the tweet id. The subj metadata is also removed. The Evdience Record is also updated."

  (:require [clj-http.client :as client]
            [clj-time.core :as clj-time]
            [clj-time.format :as clj-time-format]
            [clojure.tools.logging :as log]
            [config.core :refer [env]]
            [event-data-common.checkpoint :as checkpoint]
            [event-data-common.date :as date]
            [event-data-common.event-bus :as bus]
            [event-data-common.evidence-log :as evidence-log]
            [event-data-common.query-api :as query-api]
            [event-data-investigator.evidence-record :as evidence-record]
            [robert.bruce :refer [try-try-again]]
            [throttler.core :refer [throttle-fn]]
            [twitter.api.restful :as restful]
            [twitter.oauth :as oauth]
            [event-data-investigator.util :as util]))


(def credentials
  (oauth/make-oauth-creds
    (:investigator-twitter-api-key env)
    (:investigator-twitter-api-secret env)
    (:investigator-twitter-access-token env)
    (:investigator-twitter-access-token-secret env)))

(def throttle-per-minute
  "Throttle maximum for /statuses/lookup API endpoint.
   This is stipulated in an elevated access agreement.
   Don't run this without confirming the rate limit."
   80)

(def announcement-url
  "A static URL that describes the Twitter compliance activity"
  "https://evidence.eventdata.crossref.org/announcements/2017-05-08T08-41-00Z-CED-9.json")

(def twitter-jwt (event-data-common.jwt/sign @bus/jwt-verifier {"sub" "twitter"}))

; https://dev.twitter.com/rest/reference/get/statuses/lookup
(def ids-per-request 100)

(defn check-deleted-tweet-ids
  "For a batch of numerical tweet IDs, return those that no longer exist.
   Use the 'map' parameter to query for IDs that have explicitly been deleted, rather than those that are no longer present.
   This is, in a small way, a failsafe."
  [tweet-ids]
  {:pre [(<= (count tweet-ids) ids-per-request)]}
  (let [response (restful/statuses-lookup :oauth-creds credentials :params {:id (clojure.string/join "," tweet-ids) :map true :include_entities false :trim_user true})
        ; The :map option returns a map of ID to content, and nil content when the tweet no longer exists. Filter for this nil value.
        deleted-tweet-ids (->> response :body :id (filter (fn [[id v]] (nil? v))) (map first) (map name) set)]
    (log/debug "In" (count tweet-ids) "ids found" (count deleted-tweet-ids) "deleted")
    deleted-tweet-ids))

(defn check-deleted-tweet-ids-robust
  [tweet-ids]
  (try-try-again
    {:sleep 6000 :tries 5}
    #(check-deleted-tweet-ids tweet-ids)))

(def check-deleted-tweet-ids-throttled
  (throttle-fn check-deleted-tweet-ids-robust throttle-per-minute :minute))

(defn url->tweet-id
  [url]
  (when url
    (let [url (java.net.URL. url)
          url-path (.getPath url)
          number (second (re-find #"/(\d+)$" url-path))]
      
      ; Quick sanity check to guard against non-twitter URLs matching.
      (when (.endsWith (.getHost url) "twitter.com")
        number))))

(defn event->tweet-id
  [event]
  (-> event :subj_id url->tweet-id))

(def date-format
  (:date-time-no-ms clj-time-format/formatters))

(defn patch-event
  "Patch Event to remove sensitive material and set update fields."
  [event]
  (-> event
    ; Remove the whole subj field.
    (assoc :subj {:pid "https://twitter.com"})
    (assoc :subj_id "https://twitter.com")
    (assoc :updated "deleted")
    (assoc :updated_reason announcement-url)
    (assoc :updated_date (util/->yyyy-mm-dd-hh-mm-ss (clj-time/now)))))

(defn filter-deleted-events-batch
  "For a small batch of Events that may need patching, return those 
   Events that have references deleted Tweets."
  [events]
  {:pre [(<= (count events) ids-per-request)]}
  (let [tweet-ids (map event->tweet-id events)
        ; Multiple Events can be recorded against a tweet.
        unique-tweet-ids (set tweet-ids)
        tweet-ids-to-delete (set (check-deleted-tweet-ids-throttled unique-tweet-ids))]
    (log/info "From" (count events) "events got" (count unique-tweet-ids) "unique tweet ids, of which" (count tweet-ids-to-delete) "deleted")

    (filter
      #(-> % event->tweet-id tweet-ids-to-delete) 
      events)))

(defn filter-deleted-events
  "For any sized seq of Events that may need patching, return those 
  Events that have references deleted Tweets."
  [events]
  (mapcat
    filter-deleted-events-batch
    (partition-all ids-per-request events)))

(defn patch-events
  "For a seq of Events, filter out those that need patching and patch them,
   returning a seq of patched Events"
  [events]
  (->> events filter-deleted-events (map patch-event)))

(defn query-events
  "Retrieve a lazy sequence of Events for the given date"
  [date-str]
  (query-api/fetch-query-api
    "/v1/events"
    {:from-collected-date date-str 
     :until-collected-date date-str
     :source "twitter"}
     :events))

(defn run-for-date!
  "Query Events for the date, return those that need patching."
  [yyyy-mm-dd]
  (log/info "Run for date" yyyy-mm-dd)
  (evidence-log/log!
      {:i "q0009"
       :s "quality"
       :c "twitter-compliance-scan"
       :f "start"
       :p yyyy-mm-dd})

  (let [events (query-events yyyy-mm-dd)
        patched-events (patch-events events)
        updated-count (atom 0)
        time-before (System/currentTimeMillis)]

    (doseq [patched-event patched-events]
      (prn (:id patched-event))
      (swap! updated-count inc)
      (bus/send-event patched-event twitter-jwt)
      (evidence-record/patch-evidence-record-in-storage! patched-event))

    (log/info "Done! Updated" @updated-count "Events! Took" (- (System/currentTimeMillis) time-before) "ms")
      (evidence-log/log!
      {:i "q000a"
       :s "quality"
       :c "twitter-compliance-scan"
       :f "finish"
       :p yyyy-mm-dd
       :v @updated-count})))

(defn run-short-term
  "Perform the immediate (after 1 day) check (ensure it's run for all days)."
  []
  (let [days (util/all-days-since-collected-epoch
               (clj-time/minus (clj-time/now) (clj-time/days 1)))]
    (doseq [day days]
      (let [day-str (date/->yyyy-mm-dd day)]
        (checkpoint/run-once-checkpointed!
          ["investigator" "twitter-compliance" "short-term" day-str]
          (fn []
            (run-for-date! day-str)))))))

(defn run-mid-term
  "Perform the midterm (after 1 month) check (ensure it's run for all days)."
  []
  (let [days (util/all-days-since-collected-epoch
               (clj-time/minus (clj-time/now) (clj-time/months 1)))]
    (doseq [day days]
      (let [day-str (date/->yyyy-mm-dd day)]
        (checkpoint/run-once-checkpointed!
          ["investigator" "twitter-compliance" "mid-term" day-str]
          (fn []
            (run-for-date! day-str)))))))

(defn run-long-term
  "Perform the long-term (after 1 year) check (ensure it's run for all days)."
  []
  (let [days (util/all-days-since-collected-epoch
               (clj-time/minus (clj-time/now) (clj-time/years 1)))]
    (doseq [day days]
      (let [day-str (date/->yyyy-mm-dd day)]
        (checkpoint/run-once-checkpointed!
          ["investigator" "twitter-compliance" "long-term" day-str]
          (fn []
            (run-for-date! day-str)))))))

(def manifest
  {:schedule [[run-short-term (clj-time/days 1)]
              [run-mid-term (clj-time/days 1)]
              [run-long-term (clj-time/days 1)]]})

