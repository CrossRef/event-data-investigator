(ns event-data-investigator.core
  (:require [event-data-investigator.checks :as checks]
            [taoensso.timbre :as timbre]
            [clojure.tools.logging :as log]))

(timbre/merge-config!
    {:ns-blacklist [
       ; Twitter client.
       "com.ning.http.client.providers.netty.*"]
     :level :info})


(defn -main
  [& args]
  (let [command (first args)]
    (condp = command
      "scheduled-checks" (checks/start-schedule)

      (log/error "Didn't recognise command:" command))))


