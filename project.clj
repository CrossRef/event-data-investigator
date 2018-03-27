(defproject event-data-investigator "0.1.0-SNAPSHOT"
  :description "Event Data Investigator"
  :url "https://www.crossref.org/services/event-data"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [event-data-common "0.1.51"]
                 [robert/bruce "0.8.0"]
                 [http-kit "2.3.0-alpha5"]
                 [org.clojure/data.json "0.2.6"]
                 [clojurewerkz/quartzite "2.1.0"]]
  :main ^:skip-aot event-data-investigator.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
