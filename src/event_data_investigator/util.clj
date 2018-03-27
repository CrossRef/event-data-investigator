(ns event-data-investigator.util
  (:require [clj-time.core :as clj-time]
            [clj-time.periodic :as periodic]))

(def collected-epoch
  "The earliest date on which any Event Data took place."
  (clj-time/date-time 2016 1 1))

(defn yesterday
  []
  (clj-time/minus (clj-time/now) (clj-time/days 1)))

(defn all-days-since-collected-epoch
  "Sequence of all days since the epoch, up to yesterday."
  []
  (let [stop (yesterday)]
    (take-while
      #(clj-time/before? % stop)
      (periodic/periodic-seq collected-epoch (clj-time/days 1)))))
