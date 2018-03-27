(ns event-data-investigator.core
  (:require [event-data-investigator.checks :as checks]
            [clojure.tools.logging :as log]))
  
(defn -main
  [& args]
  (let [command (first args)]
    (condp = command
      "scheduled-checks" (checks/start-schedule)

      (log/error "Didn't recognise command:" command))))


