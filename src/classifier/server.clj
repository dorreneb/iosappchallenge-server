(ns classifier.server
  (:require [cheshire.core :refer [generate-string parse-string]]))

(defn encode [data]
  (generate-string data))

(defn decode [data]
  (parse-string data true))

(defn respond [connection message]
  (.send connection message))

(defn broadcast
  "Sends message out to all connections in listener."
  [listeners message]
  (doseq [connection listeners]
    (respond connection message)))

(defn microphone
  "Sends message out to all listeners except speaker, on behalf of speaker."
  [listeners speaker message]
  (broadcast (disj listeners speaker) message))

