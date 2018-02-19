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
  ([filter-str cursor] (fetch-query-api filter-str cursor 0))
  ([filter-str cursor cnt]
    (log/info "Fetch Query API:" filter-str "cursor" cursor)
    (let [body (try-try-again {:sleep 60000 :tries 10}
                                  (fn []

                                    (let [response @(client/get url 
                                         {:query-params
                                          { :mailto "investigator+labs@crossref.org"
                                            :filter filter-str
                                           :cursor cursor}
                                          :as :stream
                                          :timeout 900000})]
                                      (when-not (= 200 (:status response))
                                        (log/error response)
                                        (throw (Exception.)))

                                      (-> response :body io/reader (json/read :key-fn keyword)))))

          events (-> body :message :events)
          next-cursor (-> body :message :next-cursor)
          cnt (+ cnt (count events))
          total (-> body :message :total-results)]

      (when total
        (log/info "Retrieved" cnt "/" total "=" (int (* 100 (float (/ cnt total)))) "%"))

      (if next-cursor
        (lazy-cat events (fetch-query-api filter-str next-cursor cnt))
        events))))
