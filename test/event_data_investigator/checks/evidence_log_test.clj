(ns event-data-investigator.checks.evidence-log-test
  (:require [clojure.test :refer :all]
            [event-data-investigator.issue-tracking :as issue-tracking]
            [event-data-investigator.checks.evidence-log :as evidence-log]
            [clj-time.core :as clj-time]
            [clj-http.fake :as fake]))

(deftest check
  (testing "check should report OK if both CSV and JSON are present for day."
    (fake/with-fake-routes-in-isolation
      {"https://evidence.eventdata.crossref.org/log/2018-01-01.csv"
        {:head (fn [req] {:status 200})}

        "https://evidence.eventdata.crossref.org/log/2018-01-01.txt"
        {:head (fn [req] {:status 200})}}

      (let [result (evidence-log/check (clj-time/date-time 2018 1 1))]
        (is (:csv-present result) "csv should be reported present")
        (is (:json-present result) "json should be reported present"))))

  (testing "check should report missing CSV"
    (fake/with-fake-routes-in-isolation
      {"https://evidence.eventdata.crossref.org/log/2018-01-01.csv"
        {:head (fn [req] {:status 404})}

        "https://evidence.eventdata.crossref.org/log/2018-01-01.txt"
        {:head (fn [req] {:status 200})}}

      (let [result (evidence-log/check (clj-time/date-time 2018 1 1))]
        (is (not (:csv-present result)) "csv should be reported missing")
        (is (:json-present result) "json should be reported present"))))


  (testing "check should report OK if both CSV and JSON are present for day."
    (fake/with-fake-routes-in-isolation
      {"https://evidence.eventdata.crossref.org/log/2018-01-01.csv"
        {:head (fn [req] {:status 200})}

        "https://evidence.eventdata.crossref.org/log/2018-01-01.txt"
        {:head (fn [req] {:status 404})}}

      (let [result (evidence-log/check (clj-time/date-time 2018 1 1))]
        (is (:csv-present result) "csv should be reported present")
        (is (not (:json-present result)) "json should be reported missing")))))


