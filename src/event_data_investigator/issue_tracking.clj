(ns event-data-investigator.issue-tracking
  "Raise and interact with issues on GitHub.
   Currently stubbed out"
  (:require [clojure.tools.logging :as log]))


(defn raise
  "Raise a GitHub issue.
   By default locate a ticket with the same title and append."
   ([title body labels]
     (raise title body labels false))

   ([title body labels force-new?]
     ; TODO this is stubbed out for now.
     (log/info "Issue:\nTitle:" title "\nLabels:" labels "\nBody:" body)))