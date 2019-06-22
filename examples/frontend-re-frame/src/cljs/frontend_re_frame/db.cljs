(ns frontend-re-frame.db
  (:require 
   [re-frame.core :as re-frame]))

(def ls-key "app-db")

(defn db->local-store
  "Puts db into localStorage"
  [db]
  (.setItem js/localStorage ls-key (prn-str db)))
