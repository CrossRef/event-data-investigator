(ns event-data-investigator.evidence-record-test
  (:require [clojure.test :refer :all]
            [event-data-investigator.evidence-record :as evidence-record]
            [clj-time.core :as clj-time]
            [clojure.data.json :as json]
            [event-data-common.storage.memory :as memory]
            [event-data-common.storage.store :as store]))

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
                         :subj_id "https://twitter.com#deleted"}

          result (evidence-record/patch-action original-action patched-event)]

      (is (= result
             { :occurred-at "2017-11-28T09:19:51Z"
               :processed-observations ["test"]
               :events [
                  ; This Event has been patched.
                  {:id "48186b9c-e10e-45c3-af51-dbfb7a5f9c03"
                   :subj_id "https://twitter.com#deleted"
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
               :url "https://twitter.com#deleted"
               
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
                 :test-3 3}]

      (is (= (evidence-record/patch-evidence-record
               input 
               {:id "2" :subj_id "REDACTED_SUBJ_ID" :subj {:test "REDACTED_SUBJECT_METADATA"}})

               {:test-1 1
                :test-2 2
                :pages [{:page-number 1
                         :this "that"
                         :actions [{:id "ACTION_ID_1"

                                    ; url will be replaced with subj_id of the patched Event, as it's probably sensitive.
                                    :url "REDACTED_SUBJ_ID"
                          
                                    ; subj metadata will be removed altogether, as it's probably sensitive.
                                    :subj {}

                                    :events [; First Event won't be touched as it doesn't match the patched Event ID.
                                             {:id "1" :subj_id "SUBJECT_ID_1" :subj {:test "METADATA_1"}}
                                             ; This Event should be replaced.
                                             {:id "2" :subj_id "REDACTED_SUBJ_ID" :subj {:test "REDACTED_SUBJECT_METADATA"}}]}

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
                :test-3 3})))))

(deftest key-from-url
  (testing "key-from-url should return the S3 storage path from an Evidence Record URL"

    (is (= (evidence-record/key-from-url "https://evidence.eventdata.crossref.org/evidence/20171128-twitter-bcdddbb5-4910-4705-a9dc-38cf2121d741")
           "evidence/20171128-twitter-bcdddbb5-4910-4705-a9dc-38cf2121d741")
        "Path should be returned from URL")

    (is (= (evidence-record/key-from-url "20171128-twitter-bcdddbb5-4910-4705-a9dc-38cf2121d741")
           nil)
        "When there's no URL, should return nil.")))

; (deftest patch-evidence-record-in-storage!
;   (testing "patch-evidence-record-in-storage! should should log an error
;             if the Evidence Record doesn't exist"
    

;     (evidence-record/patch-evidence-record-in-storage! )
;     )

;   (testing "patch-evidence-record-in-storage! should should log an error
;             if the Evidence Record doesn't exist")

;   (testing "patch-evidence-record-in-storage! should set the result of applying
;             patch-evidence-record to the input when the given Evidence Record exists")

;   )




