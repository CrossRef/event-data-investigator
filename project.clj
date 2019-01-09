(defproject event-data-investigator "0.1.5"
  :description "Event Data Investigator"
  :url "https://www.crossref.org/services/event-data"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [event-data-common "0.1.54"]
                 [robert/bruce "0.8.0"]
                 [http-kit "2.3.0-alpha5"]
                 [org.clojure/data.json "0.2.6"]
                 [clj-http-fake "1.0.3"]
                 [throttler "1.0.0"]
                 [twitter-api "1.8.0"]
                 [org.apache.kafka/kafka-clients "1.1.1"]
                 [org.apache.kafka/kafka_2.12 "1.1.1"]
                 [com.climate/claypoole "1.1.4"]
                 [org.clojure/core.async "0.4.474"]]
  :main ^:skip-aot event-data-investigator.core
  :target-path "target/%s"
  ; On 2017-11-11, with 6.7 million Events, 3.5G was used with no pressure.
  :jvm-opts ["-Duser.timezone=UTC" "-Xmx4G"]
  :profiles {:uberjar {:aot :all}})
