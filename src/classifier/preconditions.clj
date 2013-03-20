(ns classifier.preconditions
  (:require [dire.core :refer [with-precondition! with-handler!]]
            [classifier.session :refer :all]))

(defn graph-contains-id? [graph id]
  (= (count (filter (fn [element] (= (uuid id) (:id element))) @graph)) 1))

(defn report-bad-id-failure
  ([] (report-bad-id-failure :bad-id))
  ([reason] {:success false :why reason}))

(with-precondition! #'delete-box
  :legal-id (fn [graph {:keys [id]} _] (graph-contains-id? graph id)))

(with-precondition! #'rename-box
  :legal-id (fn [graph {:keys [id]} _] (graph-contains-id? graph id)))

(with-precondition! #'move-box
  :legal-id (fn [graph {:keys [body]} _] (graph-contains-id? graph (:id body))))

(with-precondition! #'create-connection
  :legal-from-id (fn [graph {:keys [body]} _] (graph-contains-id? graph (:from body))))

(with-precondition! #'create-connection
  :legal-to-id (fn [graph {:keys [body]} _] (graph-contains-id? graph (:to body))))

(with-handler! #'delete-box
  {:precondition :legal-id}
  (fn [_ _ _ c] (report-bad-id-failure)))

(with-handler! #'rename-box
  {:precondition :legal-id}
  (fn [_ _ _ c] (report-bad-id-failure)))

(with-handler! #'move-box
  {:precondition :legal-id}
  (fn [_ _ _ c] (report-bad-id-failure)))

(with-handler! #'create-connection
  {:precondition :legal-from-id}
  (fn [_ _ _ c] (report-bad-id-failure :bad-src-id)))

(with-handler! #'create-connection
  {:precondition :legal-to-id}
  (fn [_ _ _ c] (report-bad-id-failure :bad-dst-id)))

