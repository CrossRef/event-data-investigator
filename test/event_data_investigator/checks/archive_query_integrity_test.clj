(ns event-data-investigator.checks.archive-query-integrity-test
  (:require [clojure.test :refer :all]
            [event-data-investigator.issue-tracking :as issue-tracking]
            [event-data-investigator.checks.archive-query-integrity :as archive-query-integrity]
            [event-data-common.query-api :as query]
            [event-data-common.event-bus :as bus]
            [event-data-common.whitelist :as whitelist]
            [clj-time.core :as clj-time]))

(deftest check
  (testing "check should return no differences if both days have the same IDs."
    (with-redefs [query/event-ids-for-day (fn [_] #{"1234" "5678"})
                  bus/event-ids-for-day (fn [_] #{"1234" "5678"})
                  bus/get-event (fn [_] nil)
                  whitelist/filter-events identity]

      (let [{query-missing :query-missing
             bus-missing :bus-missing} (archive-query-integrity/check (clj-time/date-time 2018 11 1))]
        (is (empty? query-missing) "No Events should be missing from the query API.")
        (is (empty? bus-missing) "No Events should be missing from the bus archive."))))

  (testing "check should return missing Events for Query API when they are in the archive but not the Query API."
    (with-redefs [query/event-ids-for-day (fn [_] #{"1234" "5678"})
                  bus/event-ids-for-day (fn [_] #{"1234" "5678" "8888" "9999"})
                  ; For this test, the only relevant field of the Event is the ID.
                  bus/get-event {"8888" {:id "8888"} "9999" {:id "9999"}}
                  whitelist/filter-events identity]

      (let [{query-missing :query-missing
             bus-missing :bus-missing} (archive-query-integrity/check (clj-time/date-time 2018 11 1))]
        (is (= (set query-missing) #{"8888" "9999"}) "Event IDs missing from Query should be reported.")
        (is (empty? bus-missing) "No Events should be missing from the archive."))))


  (testing "check should return missing Events for Bus API when they are missing."
    (with-redefs [query/event-ids-for-day (fn [_] #{"1234" "5678" "8888" "9999"})
                  bus/event-ids-for-day (fn [_] #{"1234" "5678"})
                  ; For this test, the only relevant field of the Event is the ID.
                  bus/get-event {"8888" {:id "8888"} "9999" {:id "9999"}}
                  whitelist/filter-events identity]

      (let [{query-missing :query-missing
             bus-missing :bus-missing} (archive-query-integrity/check (clj-time/date-time 2018 11 1))]
        (is (empty? query-missing) "No Events should be missing from the Query API.")
        (is (= (set bus-missing) #{"8888" "9999"}) "Event IDs missing from Archive should be reported."))))


  (testing "check should not return missing Events for Query API if they don't match the whitelist."
    (with-redefs [query/event-ids-for-day (fn [_] #{"1234" "5678"})
                  bus/event-ids-for-day (fn [_] #{"1234" "5678" "8888" "9999"})
                  bus/get-event {"8888" {:id "8888" :source "good"} "9999" {:id "9999" :source "bad"}}
                  whitelist/filter-events (fn [events] (filter #(= (:source %) "good") events))]

      (let [{query-missing :query-missing
            bus-missing :bus-missing} (archive-query-integrity/check (clj-time/date-time 2018 11 1))]
        (is (= (set query-missing) #{"8888"}) "Only Events that match the whitelist should be reported as missing.")
        (is (empty? bus-missing) "No Events should be missing from the archive."))))

  (testing "check should not return missing Events for Query API if they're Experimental (i.e. not Production ready)."
    (with-redefs [query/event-ids-for-day (fn [_] #{"1234" "5678"})
                  bus/event-ids-for-day (fn [_] #{"1234" "5678" "XXXX" "YYYY"})
                  bus/get-event {"XXXX" {:id "XXXX"} "YYYY" {:id "YYYY" :experimental true}}
                  whitelist/filter-events identity]

      (let [{query-missing :query-missing
            bus-missing :bus-missing} (archive-query-integrity/check (clj-time/date-time 2018 11 1))]
        (is (= (set query-missing) #{"XXXX"}) "Experimental Events should be ignored.")
        (is (empty? bus-missing) "No Events should be missing from the archive.")))))

(deftest check-and-report
  (testing "check-and-report should not report issue if there are no missing Events."
    (let [issues (atom [])]
      (with-redefs [archive-query-integrity/check
                    (fn [_]
                      {:query-missing []
                       :bus-missing []})
                    
                    issue-tracking/raise
                    (fn [title _ _]
                      (swap! issues #(conj % title)))]

        (archive-query-integrity/check-and-report (clj-time/date-time 2018 1 1))

        (is (empty? @issues) "No issues should have been raised."))))

  (testing "check-and-report should report issue if there are Events missing for Query API."
    (let [issues (atom [])]
      (with-redefs [archive-query-integrity/check
                    (fn [_]
                      {:query-missing ["1234"]
                       :bus-missing []})
                    
                    issue-tracking/raise
                    (fn [title _ _]
                      (swap! issues #(conj % title)))]

        (archive-query-integrity/check-and-report (clj-time/date-time 2018 1 1))

        (is (= @issues ["Query Integrity for 2018-01-01"]) "One issue should have been raised."))))

  (testing "check-and-report should report issue if there are Events missing for Bus."
    (let [issues (atom [])]
      (with-redefs [archive-query-integrity/check
                    (fn [_]
                      {:query-missing []
                       :bus-missing ["5678"]})
                    
                    issue-tracking/raise
                    (fn [title _ _]
                      (swap! issues #(conj % title)))]

        (archive-query-integrity/check-and-report (clj-time/date-time 2018 1 1))

        (is (= @issues ["Bus Integrity for 2018-01-01"]) "One issue should have been raised.")))))
  