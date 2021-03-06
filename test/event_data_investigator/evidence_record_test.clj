(ns event-data-investigator.evidence-record-test
  (:require [clojure.test :refer :all]
            [event-data-investigator.evidence-record :as evidence-record]
            [clj-time.core :as clj-time]
            [clojure.data.json :as json]
            [event-data-common.storage.memory :as memory]
            [event-data-common.storage.store :as store]
            [clojure.java.io :as io]))

(def original-action
  "An action with a selection of placeholder-filled fields, an Event we want to patch, and a few other Events."
  {:occurred-at "2017-11-28T09:19:51Z"
   :processed-observations ["test"]
   :events [
      {:license "https://creativecommons.org/publicdomain/zero/1.0/"
       :obj_id "https://doi.org/10.1038/nbt.4024"
       :source_token "45a1ef76-4f43-4cdc-9ba8-5a6ad01cc231"
       :occurred_at "2017-11-28T09:19:51Z"
       :subj_id "http://twitter.com/MMMMM/statuses/LLLLLLLLLLLLLLLLLL"
       :id "48186b9c-e10e-45c3-af51-dbfb7a5f9c03"
       :evidence_record "https://evidence.eventdata.crossref.org/evidence/20171128-twitter-bcdddbb5-4910-4705-a9dc-38cf2121d741"
       :action "add"
       :subj {
         :pid "http://twitter.com/MMMMM/statuses/LLLLLLLLLLLLLLLLLL"
         :url "http://twitter.com/MMMMM/statuses/LLLLLLLLLLLLLLLLLL"
         :title "Tweet LLLLLLLLLLLLLLLLLL"
         :issued "2017-11-28T09:19:51.000Z"
         :author {
           :url "http://www.twitter.com/MMMMM"
         }
         :original-tweet-url "http://twitter.com/NNNNN/statuses/MMMMMMMMMMMMMMMMMM"
         :original-tweet-author "http://www.twitter.com/NNNNN"
         :alternative-id "LLLLLLLLLLLLLLLLLL"
       }
       :source_id "twitter"
       :obj {
         :pid "https://doi.org/10.1038/nbt.4024"
         :url "https://www.nature.com/articles/nbt.4024?error=cookies_not_supported&code=6a7f88fb-4fbc-40ca-b6b4-973f93c6d262"
       }
       :relation_type_id "discusses"
     }
     {
       :id "OTHER_EVENT_1"
     }
     {
       :id "OTHER_EVENT_2"
     }
   ]
   :extra {"TEST" "TEST"}
   :matches ["TEST"]
   :id "3d42119b1cdd6a273ec83fb15a37aa29f16c81ce"
   :url "http://twitter.com/MMMMM/statuses/LLLLLLLLLLLLLLLLLL"
   :subj {
     :title "Tweet LLLLLLLLLLLLLLLLLL"
     :issued "2017-11-28T09:19:51.000Z"
     :author {
       :url "http://www.twitter.com/MMMMM"
     }
     :original-tweet-url "http://twitter.com/NNNNN/statuses/MMMMMMMMMMMMMMMMMM"
     :original-tweet-author "http://www.twitter.com/NNNNN"
     :alternative-id "LLLLLLLLLLLLLLLLLL"
   }
   :relation-type-id "discusses"})


(deftest patch-action
  (testing "patch-action should replace Event, remove sensitive info and leave other Events intact"
    ; Patch in a minimal Event. The id should match, the subj_id should be used.
    (let [patched-event {:id "48186b9c-e10e-45c3-af51-dbfb7a5f9c03"
                         :subj_id "https://twitter.com"}

          result (evidence-record/patch-action original-action patched-event)]

      (is (= result
             { :occurred-at "2017-11-28T09:19:51Z"
               :processed-observations ["test"]
               :events [
                  ; This Event has been patched.
                  {:id "48186b9c-e10e-45c3-af51-dbfb7a5f9c03"
                   :subj_id "https://twitter.com"
                  }
                 {
                   :id "OTHER_EVENT_1"
                 }
                 {
                   :id "OTHER_EVENT_2"
                 }
               ]
               :extra {"TEST" "TEST"}
               :matches ["TEST"]
               :id "3d42119b1cdd6a273ec83fb15a37aa29f16c81ce"

               ; URL has been replaced with that taken from Event.
               :url "https://twitter.com"
               
               ; Subj metadata has been removed.
               :subj {}
               :relation-type-id "discusses"}))

      (is (not (.contains (print-str result) "NNNNN")) "Sensitive author name should not be present anywhere in the Evidence Record now.")
      (is (not (.contains (print-str result) "MMMMMMMMMMMMMMMMMM")) "Sensitive tweet ID should not be present anywhere in the Evidence Record now."))))

(deftest patch-evidence-record
  (testing "patch-evidence-record should apply a function to every Action in the Evidence Record and not not change anything else"
    (let [input {:test-1 1
                 :test-2 2
                 :pages [{:page-number 1
                          :this "that"
                          :actions [{:id "ACTION_ID_1"

                                     ; url will be replaced with subj_id of the patched Event, as it's probably sensitive.
                                     :url "SENSITIVE_SUBJECT_ID"
                          
                                     ; subj metadata will be removed altogether, as it's probably sensitive.
                                     :subj {:id "ACTION_SUBJ_ID_1"}

                                     :events [; First Event won't be touched as it doesn't match the patched Event ID.
                                              {:id "1" :subj_id "SUBJECT_ID_1" :subj {:test "METADATA_1"}}
                                              ; This Event should be replaced.
                                              {:id "2" :subj_id "SENSITIVE_SUBJECT_ID_2" :subj {:test "SENSITIVE_METADATA_2"}}]}

                                    ; This action shouldn't be touched.
                                    {:id "ACTION_ID_2" :events [{:id "3" :subj_id "SUBJECT_ID_3" :subj {:test "METADATA_3"}}
                                                                {:id "4" :subj_id "SUBJECT_ID_4" :subj {:test "METADATA_4"}}
                                                                {:id "5" :subj_id "SUBJECT_ID_5" :subj {:test "METADATA_5"}}]}]}
                         
                         ; Actions on another page should also not be touched.
                         {:page-number 2
                          :this "that"
                          :actions [{:id "ACTION_ID_3" :events [{:id "6" :subj_id "SUBJECT_ID_6" :subj {:test "METADATA_1"}}
                                                                {:id "7" :subj_id "SUBJECT_ID_7" :subj {:test "METADATA_2"}}]}
                                    {:id "ACTION_ID_4" :events [{:id "8" :subj_id "SUBJECT_ID_8" :subj {:test "METADATA_8"}}
                                                                {:id "9" :subj_id "SUBJECT_ID_9" :subj {:test "METADATA_9"}}
                                                                {:id "0" :subj_id "SUBJECT_ID_0" :subj {:test "METADATA_0"}}]}]}]
                 :test-3 3
                 :updates [{:id "previous-update"}]}]

      (is (= (evidence-record/patch-evidence-record
               input 
               {:id "2"
                :subj_id "REDACTED_SUBJ_ID"
                :subj {:test "REDACTED_SUBJECT_METADATA"}
                :updated_reason "https://evidence.eventdata.crossref.org/announcements/2017-05-08T08-41-00Z-CED-9.json"
                :updated "deleted"
                :updated_date "2017-05-08T17:41:34Z"})

               {:test-1 1
                :test-2 2
                :pages [{:page-number 1
                         :this "that"
                         :actions [{:id "ACTION_ID_1"

                                    ; url will be replaced with subj_id of the patched Event, as it's probably sensitive.
                                    :url "REDACTED_SUBJ_ID"
                          
                                    ; subj metadata will be replaced with that found in the Event.
                                    :subj {:test "REDACTED_SUBJECT_METADATA"}

                                    :events [; First Event won't be touched as it doesn't match the patched Event ID.
                                             {:id "1" :subj_id "SUBJECT_ID_1" :subj {:test "METADATA_1"}}
                                             
                                             ; This Event should be replaced.
                                             {:id "2"
                                              :subj_id "REDACTED_SUBJ_ID"
                                              :subj {:test "REDACTED_SUBJECT_METADATA"}
                                              :updated_reason "https://evidence.eventdata.crossref.org/announcements/2017-05-08T08-41-00Z-CED-9.json"
                                              :updated "deleted"
                                              :updated_date "2017-05-08T17:41:34Z"}]}

                                   ; This action shouldn't be touched.
                                   {:id "ACTION_ID_2" :events [{:id "3" :subj_id "SUBJECT_ID_3" :subj {:test "METADATA_3"}}
                                                               {:id "4" :subj_id "SUBJECT_ID_4" :subj {:test "METADATA_4"}}
                                                               {:id "5" :subj_id "SUBJECT_ID_5" :subj {:test "METADATA_5"}}]}]}
                         
                        ; Actions on another page should also not be touched.
                        {:page-number 2
                         :this "that"
                         :actions [{:id "ACTION_ID_3" :events [{:id "6" :subj_id "SUBJECT_ID_6" :subj {:test "METADATA_1"}}
                                                               {:id "7" :subj_id "SUBJECT_ID_7" :subj {:test "METADATA_2"}}]}
                                   {:id "ACTION_ID_4" :events [{:id "8" :subj_id "SUBJECT_ID_8" :subj {:test "METADATA_8"}}
                                                               {:id "9" :subj_id "SUBJECT_ID_9" :subj {:test "METADATA_9"}}
                                                               {:id "0" :subj_id "SUBJECT_ID_0" :subj {:test "METADATA_0"}}]}]}]
                :test-3 3
                :updates [{:id "previous-update"}
                          {:id "2" :updated_date "2017-05-08T17:41:34Z" :updated "deleted"}]})))))

(deftest key-from-url
  (testing "key-from-url should return the S3 storage path from an Evidence Record URL"
    (is (= (evidence-record/key-from-url "https://evidence.eventdata.crossref.org/evidence/20171128-twitter-bcdddbb5-4910-4705-a9dc-38cf2121d741")
           "evidence/20171128-twitter-bcdddbb5-4910-4705-a9dc-38cf2121d741")
        "Path should be returned from URL")))

(deftest patch-evidence-record-in-storage!-missing
  (testing "End-to-end test. patch-evidence-record-in-storage! should should log an error
            if the Evidence Record doesn't exist, but return nil."
    (with-redefs [evidence-record/evidence-record-store (delay (memory/build))]
      (is (nil? (evidence-record/patch-evidence-record-in-storage!
                  {:id "1234" :evidence_record ""})))

      (is (nil? (evidence-record/patch-evidence-record-in-storage!
                  {:id "1234" :evidence_record nil})))

      (is (nil? (evidence-record/patch-evidence-record-in-storage!
                  {:id "1234" :evidence_record "https://evidence.eventdata.crossref.org/evidence/DOES_NOT_EXIST"}))))))

(deftest patch-evidence-record-in-storage!
  (testing "patch-evidence-record-in-storage! should retrieve the Evidence Record from the Event ID"
    (let [mock-store (delay (memory/build))

          patched-event {
            :evidence_record "https://evidence.eventdata.crossref.org/evidence/20170217CCCCCCCC-DDDD-4320-a927-ee514d41600a"
            :subj {:wiped "clean"}
            :subj_id "http://example.com/a"
            :obj_id "http://example.com/b"}
          ]
      
      (store/set-string
        @mock-store
        
        "evidence/20170217CCCCCCCC-DDDD-4320-a927-ee514d41600a"
        (slurp (io/resource "test/evidence-record/evidence/20170217CCCCCCCC-DDDD-4320-a927-ee514d41600a.json")))

      ; Mock out the evidence store.
      (with-redefs [evidence-record/evidence-record-store mock-store]
        (let [new-event (->
                          "test/evidence-record/events/AAAAAAAA-BBBB-4955-a9b4-6f5e18421348.json"
                          io/resource
                          slurp
                          (json/read-str :key-fn keyword))]

          ; Now do the patch, using the evidence_record field in the Event.
          (evidence-record/patch-evidence-record-in-storage! new-event))

        (let [parsed (-> @mock-store
                         (store/get-string "evidence/20170217CCCCCCCC-DDDD-4320-a927-ee514d41600a")
                         (json/read-str :key-fn keyword))]

          (is (= (-> parsed :pages first :actions first :events first :subj_id)
              "http://twitter.com/")
              "Subject URL should be picked up from Event")

          (is (= (-> parsed :pages first :actions first :events first :subj)
              {:pid "http://twitter.com/"})
              "Subject metadata should be picked up from Event")

          (is (= (-> parsed :pages first :actions first :url)
              "http://twitter.com/")
              "Action URL should be picked up from Event"))))))



(deftest evidence-record-already-updated
  (let [new-event {:id "2"
                   :subj_id "REDACTED_SUBJ_ID"
                   :subj {:test "REDACTED_SUBJECT_METADATA"}
                   :updated_reason "https://evidence.eventdata.crossref.org/announcements/2017-05-08T08-41-00Z-CED-9.json"
                   :updated "deleted"
                   :updated_date "2017-05-08T17:41:34Z"}

        blank-evidence-record {:some :data}]

    (testing "evidence-record-already-updated false when no updates"
      (is (not (evidence-record/evidence-record-already-updated blank-evidence-record new-event))))

    (testing "evidence-record-already-updated true when update made"
      ; Do the update, then try to determine if it was made.
      (let [updated-evidence-record (evidence-record/patch-evidence-record blank-evidence-record new-event)]
        (is (evidence-record/evidence-record-already-updated updated-evidence-record new-event))))))


