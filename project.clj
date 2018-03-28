(defproject event-data-investigator "0.1.2"
  :description "Event Data Investigator"
  :url "https://www.crossref.org/services/event-data"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [event-data-common "0.1.52"]
                 [robert/bruce "0.8.0"]
                 [http-kit "2.3.0-alpha5"]
                 [org.clojure/data.json "0.2.6"]
                 [clj-http-fake "1.0.3"]
                 [throttler "1.0.0"]]
  :main ^:skip-aot event-data-investigator.core
  :target-path "target/%s"
  ; On 2017-11-11, with 6.7 million Events, 3.5G was used with no pressure.
  :jvm-opts ["-Duser.timezone=UTC" "-Xmx4G"]
  :profiles {:uberjar {:aot :all}})
