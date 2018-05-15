(ns event-data-investigator.evidence-record
  "Work with Evidence Records."
  (:require [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [event-data-common.storage.s3 :as s3]
            [event-data-common.storage.store :as store]
            [config.core :refer [env]])
  (:import [java.net URL]))


(def evidence-record-store
  (delay
    ; Use the credentials config for the Percolator, as we need the same permissions.
    (s3/build (:percolator-s3-key env)
              (:percolator-s3-secret env)
              (:percolator-evidence-region-name env)
              (:percolator-evidence-bucket-name env))))


(defn map-actions
  "Map over actions within an Input Evidence Record, leaving the rest intact."
  ; This is simpler than the map-actions found in the Percolator.
  [f evidence-record]
  (assoc evidence-record
    :pages (doall
             (map
               (fn [page]
                 (assoc page
                   :actions (doall
                              (map f (:actions page)))))
                 (:pages evidence-record)))))

(defn patch-action
  "Patch a given Action to remove all sensitive information 
   when it corresponds to the Event."
  [action patched-event]
  (let [patched-event-id (:id patched-event)
        contains-event-id (some #(= (:id %) patched-event-id) (:events action))]
    
    ; The action may not contain the Event id. In this case, don't change it. 
    (if-not contains-event-id
      action
      ; If it does, we want to change a few things.
      (assoc action
        ; Update the list of Events to replace the patched one, leave the rest intact.
        :events (map (fn [this-event]
                        (if-not (= (:id this-event) (:id patched-event))
                                this-event
                                patched-event))
                      (:events action))

        ; Set subj metadata to the updated metadata in the Event.
        :subj (:subj patched-event {})

        ; The URL of the action is usually the subj_id. 
        ; If it's been modified in the patched Event, update it.
        ; If it hasn't, this will result in no change.
        :url (:subj_id patched-event)))))

(def update-event-keys
  "Which keys are carried from the Event into the Evidence Record updates list.
   The outcome of this selection of keys is used to decide whether or not a given update has been made to the Evidence Record.
   Ergo this is used both by patch-evidence-record and evidence-record-already-updated, and these should not be changed without taking account of this."
  [:id :updated_date :updated])

(defn patch-evidence-record
  "Patch an Evidence Record to replace the given Event 
   with the patched version and remove other sensitive info.
   Append info to the 'updates' field too."
  [evidence-record patched-event]
  (assoc evidence-record
    :pages (map (fn [page] 
                  (assoc page :actions (map #(patch-action % patched-event) (:actions page))))
                (:pages evidence-record))
    
    ; Append the relevant fields from the Event here.
    :updates (conj (:updates evidence-record)
                   (select-keys patched-event update-event-keys))))

(defn key-from-url
  "Given an Evidence Record URL (as found in an Event), return the storage path in S3."
  [url]
  (try
    ; Drop leading slash from the path.
    (clojure.string/replace (.getPath (URL. url)) #"^/" "")

    (catch java.net.MalformedURLException ex
      (do
        (log/error "Failed to extract storage key for Evidence Record ID" url)
        nil))))

(defn evidence-record-already-updated
  "Has the given Evidence Record already been updated with the given Event? 
   Used to dedupe scans."
   [evidence-record event]
   
   ((->> evidence-record
        :updates
        (map #(select-keys % update-event-keys))
        set)
     (select-keys event update-event-keys)))

(defn patch-evidence-record-in-storage!
  "For an already-patched Event, look up its Evience Record and patch it in storage."
  [patched-event]
  ; The Evidence Record may not exist (e.g. has been deleted). If so, pass.
  (when-let [evidence-record-key (-> patched-event :evidence_record key-from-url)]

    ; Retrieve, patch, save.
    (when-let [old-content-json (store/get-string @evidence-record-store evidence-record-key)]
      (let [old-content (json/read-str old-content-json :key-fn keyword)
            patched-content (patch-evidence-record old-content patched-event)
            patched-content-json (json/write-str patched-content)]

        (if (evidence-record-already-updated old-content patched-event)

            (log/info "Skip updating for Event " (:id patched-event ))

            (store/set-string 
              @evidence-record-store
              evidence-record-key
              patched-content-json))))))

