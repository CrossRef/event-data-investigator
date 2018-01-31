(ns event-data-investigator.query
  "Access the Query API"
  (:require [org.httpkit.client :as client]
            [clojure.tools.logging :as log]
            [robert.bruce :refer [try-try-again]]
            [clojure.data.json :as json]
            [clojure.java.io :as io])
  (:gen-class))

(def url "https://query.eventdata.crossref.org/events")

(defn fetch-query-api
  "Fetch a lazy seq of Events from the Query API that match the filter."
  ([filter-str] (fetch-query-api filter-str "" 0))
  ([filter-str cursor cnt]
    (log/info "Fetch Query API:" filter-str "cursor" cursor)
    (let [response (try-try-again {:sleep 30000 :tries 10}
                                  #(deref (client/get url
                                               {:query-params
                                                {:filter filter-str
                                                 :cursor cursor}
                                                :as :stream
                                                :timeout 900000})))
          body (json/read (io/reader (:body response)) :key-fn keyword)
          events (-> body :message :events)
          next-cursor (-> body :message :next-cursor)
          cnt (+ cnt (count events))
          total (-> body :message :total-results)]

      (when total
        (log/info "Retrieved" cnt "/" total "=" (int (* 100 (float (/ cnt total)))) "%"))

      (if next-cursor
        (lazy-cat events (fetch-query-api filter-str next-cursor cnt))
        events))))
