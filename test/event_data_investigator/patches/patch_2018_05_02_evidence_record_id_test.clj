(ns event-data-investigator.patches.patch-2018-05-02-evidence-record-id-test
  (:require [clojure.test :refer :all]
            [event-data-investigator.patches.patch-2018-05-02-evidence-record-id :as patch]
            [clj-time.core :as clj-time]))

(deftest valid-evidence-record-value
  (testing "valid-evidence-record-value returns true for valid URLs"
    (is (patch/valid-evidence-record-value
          "https://evidence.eventdata.crossref.org/evidence/20171128-twitter-bcdddbb5-4910-4705-a9dc-38cf2121d741")))

    (testing "valid-evidence-record-value returns true for nil"
      (is (patch/valid-evidence-record-value nil)))

    (testing "valid-evidence-record-value returns false when only id is supplied"
      (is
        (not
          (patch/valid-evidence-record-value
            "20171128-twitter-bcdddbb5-4910-4705-a9dc-38cf2121d741"))))

    (testing "valid-evidence-record-value returns false when path is supplied"
      (is
        (not
          (patch/valid-evidence-record-value
            "evidence/20171128-twitter-bcdddbb5-4910-4705-a9dc-38cf2121d741"))))

    (testing "valid-evidence-record-value returns false when path is supplied"
      (is
        (not
          (patch/valid-evidence-record-value
            "/evidence/20171128-twitter-bcdddbb5-4910-4705-a9dc-38cf2121d741")))))

(deftest normalize-evidence-record-value
  (testing "normalize-evidence-record-value should leave valid URLs unaffected"
    (is (= (patch/normalize-evidence-record-value
            "https://evidence.eventdata.crossref.org/evidence/20171128-twitter-bcdddbb5-4910-4705-a9dc-38cf2121d741")
           "https://evidence.eventdata.crossref.org/evidence/20171128-twitter-bcdddbb5-4910-4705-a9dc-38cf2121d741")))

  (testing "normalize-evidence-record-value normalize when there's path only"
    (is (= (patch/normalize-evidence-record-value
            "/evidence/20171128-twitter-bcdddbb5-4910-4705-a9dc-38cf2121d741")
           "https://evidence.eventdata.crossref.org/evidence/20171128-twitter-bcdddbb5-4910-4705-a9dc-38cf2121d741")))

  (testing "normalize-evidence-record-value normalize when there's path only no leading slash"
    (is (= (patch/normalize-evidence-record-value
            "evidence/20171128-twitter-bcdddbb5-4910-4705-a9dc-38cf2121d741")
           "https://evidence.eventdata.crossref.org/evidence/20171128-twitter-bcdddbb5-4910-4705-a9dc-38cf2121d741")))

  (testing "normalize-evidence-record-value normalize when there's an ID only"
    (is (= (patch/normalize-evidence-record-value
            "20171128-twitter-bcdddbb5-4910-4705-a9dc-38cf2121d741")
           "https://evidence.eventdata.crossref.org/evidence/20171128-twitter-bcdddbb5-4910-4705-a9dc-38cf2121d741"))))


(deftest update-event
  (testing "update-event should return nil for nil input"
    (is (nil? (patch/update-event nil))))

  (testing "update-event should return nil on an Event that doesn't need updating"
    (is
      (nil?
       (patch/update-event
         {:evidence_record
          "https://evidence.eventdata.crossref.org/evidence/20171128-twitter-bcdddbb5-4910-4705-a9dc-38cf2121d741"}))))

  (testing "update-event should update Event to set updated, updated_reason, updated_date and correct new Evidence Record format"
    (clj-time/do-at (clj-time/date-time 2018 01 01)
      (is
        (=
         (patch/update-event
           {:evidence_record "20171128-twitter-bcdddbb5-4910-4705-a9dc-38cf2121d741"})
          {:evidence_record "https://evidence.eventdata.crossref.org/evidence/20171128-twitter-bcdddbb5-4910-4705-a9dc-38cf2121d741"
           :updated_reason patch/reason
           :updated "edited"
           :updated_date "2018-01-01T00:00:00Z"})))))
