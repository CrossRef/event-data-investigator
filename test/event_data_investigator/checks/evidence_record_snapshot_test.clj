(ns event-data-investigator.checks.evidence-record-snapshot-test
  (:require [clojure.test :refer :all]
            [event-data-investigator.issue-tracking :as issue-tracking]
            [event-data-investigator.checks.evidence-record-snapshot :as evidence-record-snapshot]
            [clj-time.core :as clj-time]
            [clj-http.fake :as fake]))

(deftest check-and-report
  (testing "check-and-report should not report issue if there are no missing log items."
    (let [issues (atom [])]
      (with-redefs [evidence-record-snapshot/check
                    (fn [_]
                      {:snapshot-present true})
                    
                    issue-tracking/raise
                    (fn [title _ _]
                      (swap! issues #(conj % title)))]

        (evidence-record-snapshot/check-and-report (clj-time/date-time 2018 1 1))

        (is (empty? @issues) "No issues should have been raised."))))

  (testing "check-and-report report issue if there is missing snapshot."
    (let [issues (atom [])]
      (with-redefs [evidence-record-snapshot/check
                    (fn [_]
                      {:snapshot-present false})
                    
                    issue-tracking/raise
                    (fn [title _ _]
                      (swap! issues #(conj % title)))]

        (evidence-record-snapshot/check-and-report (clj-time/date-time 2018 1 1))

        (is (= @issues ["Evidence Record Snapshot dump missing for 2018-01-01"]) "One issue should have been raised.")))))

