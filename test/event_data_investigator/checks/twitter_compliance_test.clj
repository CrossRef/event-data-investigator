(ns event-data-investigator.checks.twitter-compliance-test
  (:require [clojure.test :refer :all]
            [event-data-investigator.checks.twitter-compliance :as twitter-compliance]
            [event-data-common.query-api :as query]
            [clj-time.core :as clj-time]))

(deftest url->tweet-id
  (testing "tweet-id-from-url should retrieve the numerical tweet ID, from a range of URLs"
    (is (= (twitter-compliance/uri->tweet-id "http://twitter.com/USERNAME/statuses/123456")
           (twitter-compliance/uri->tweet-id "https://twitter.com/USERNAME/statuses/123456")
           (twitter-compliance/uri->tweet-id "https://twitter.com/statuses/123456")
           (twitter-compliance/uri->tweet-id "https://m.twitter.com/statuses/123456")
           "123456")))

  (testing "tweet-id-from-url should the tweet ID from new 'app url' format."
           (is (= (twitter-compliance/uri->tweet-id "twitter://status?id=123456")
                  "123456"))

           ; Any random query parameters should be discarded
           (is (= (twitter-compliance/uri->tweet-id "twitter://status?id=123456&2+2=5")
                  "123456")))

  (testing "tweet-id-from-url shouldn't return when this isn't a twitter URL (by mistake)"
    (is (= (twitter-compliance/uri->tweet-id "http://example.com/USERNAME/statuses/123456")
           (twitter-compliance/uri->tweet-id "https://doi.org/10.5555/12345678")
           nil))))


(deftest check-deleted-tweet-ids
  (testing "check-deleted-tweet-ids should return those IDs that have been deleted.")
    (let [api-response
          {:headers
            {:content-type "application/json;charset=utf-8"
             :status "200 OK"}
          :status {:code 200 :msg "OK"}
          :body
            {:id
             ; ID of an extant tweet.
             {:994497218693517314
              {:in_reply_to_screen_name nil,
               :is_quote_status false,
               :coordinates nil,
               :in_reply_to_status_id_str nil,
               :place nil,
               :possibly_sensitive false,
               :geo nil,
               :in_reply_to_status_id nil,
               :lang "en",
               :in_reply_to_user_id_str nil,
               :id 994497218693517314,
               :text "Perhaps the plaintive numbers flow / For old, unhappy, far-off things, / And battles long ago"}
              ; ID of a deleted tweet.
              :12345 nil}}}]

        ; Check for one that exists and one that doesn't, mocking out the api call.
        (with-redefs [twitter.api.restful/statuses-lookup (constantly api-response)]
          (is (= (twitter-compliance/check-deleted-tweet-ids ["994497218693517314" "12345"])
                 #{"12345"})
              "Deleted tweet id should be returned. Extant one should not be."))

        ; Again with the throttled function to ensure that it works the same.
        (with-redefs [twitter.api.restful/statuses-lookup (constantly api-response)]
          (is (= (twitter-compliance/check-deleted-tweet-ids-throttled ["994497218693517314" "12345"])
                 #{"12345"})
              "Deleted tweet id should be returned. Extant one should not be."))

        ; If the API returns nil for some reason...
        (with-redefs [twitter.api.restful/statuses-lookup (constantly nil)]
          (is (= (twitter-compliance/check-deleted-tweet-ids ["994497218693517314" "12345"])
                 #{})
              "When there's an unexpected error in the API, fail-safe and don't indicate that tweets have been deleted."))))

