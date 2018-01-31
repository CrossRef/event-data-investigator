(ns event-data-investigator.bus
  "Interface with the Event Bus."
  (:require [event-data-common.jwt :as jwt]
            [org.httpkit.client :as http]
            [config.core :refer [env]]
            [clojure.data.json :as json]))

(def jwt-verifier
  (delay (jwt/build (:global-jwt-secrets env))))

(defn build-jwt
  "Build a JWT for the given source."
  [source-id]
  (jwt/sign @jwt-verifier {"sub" source-id}))

(defn send-event
  [event jwt]
  @(http/put (str "https://bus.eventdata.crossref.org/events/" (:id event))
             {:body (json/write-str event)
              :headers {"Authorization" (str "Bearer " jwt)}}))

