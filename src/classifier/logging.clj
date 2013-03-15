(ns classifier.logging
  (:require [dire.core :refer [with-pre-hook! with-precondition! with-handler!]]
            [cheshire.core :refer [generate-string]]
            [classifier.session :refer :all]))

(with-pre-hook! #'register-connection
  (fn [id connection]
    (println "Registered connection on:" id)
    (println "\t" connection)
    (println "-------------------------------------------------")))

(with-pre-hook! #'unregister-connection
  (fn [id connection]
    (println "Unregistered connection on:" id)
    (println "\t" connection)
    (println "-------------------------------------------------")))

(with-pre-hook! #'add-session!
  (fn [session-id graph-name _]
    (println "Creating new graph session:" session-id)
    (println "\tNamed" graph-name)
    (println "-------------------------------------------------")))

(with-pre-hook! #'create-box
  (fn [{:keys [session-id] :as message} connection]
    (println "Received box creation event on:" session-id)
    (println "\t" message)
    (println "-------------------------------------------------")))

(with-pre-hook! #'rename-box
  (fn [{:keys [session-id] :as message} connection]
    (println "Received rename box event on:" session-id)
    (println "\t" message)
    (println "-------------------------------------------------")))

(with-pre-hook! #'move-box
  (fn [{:keys [session-id] :as message} connection]
    (println "Received move box event on:" session-id)
    (println "\t" message)
    (println "-------------------------------------------------")))

(with-pre-hook! #'delete-box
  (fn [graph {:keys [session-id] :as message} me]
    (println "Received delete box event on:" session-id)
    (println "\t" message)
    (println "-------------------------------------------------")))

(with-pre-hook! #'create-connection
  (fn [{:keys [session-id] :as message} connection]
    (println "Received create connection event on:" session-id)
    (println "\t" message)
    (println "-------------------------------------------------")))

(with-pre-hook! #'delete-connection
  (fn [{:keys [session-id] :as message}]
    (println "Received delete connection event on:" session-id)
    (println "\t" message)
    (println "-------------------------------------------------")))

(with-pre-hook! #'number-of-connections
  (fn [{:keys [session-id] :as message} connection]
    (println "Received n-connection event on:" session-id)
    (println "\t" message)
    (println "-------------------------------------------------")))

(with-pre-hook! #'graph-revisions
  (fn [{:keys [session-id] :as message} connection]
    (println "Received revision listing event on:" session-id)
    (println "\t" message)
    (println "-------------------------------------------------")))

(with-pre-hook! #'spec-revision
  (fn [{:keys [session-id] :as message} connection]
    (println "Getting revision on:" session-id)
    (println "\t" message)
    (println "-------------------------------------------------")))

(with-pre-hook! #'revert
  (fn [{:keys [session-id transaction-id] :as message}]
    (println "Reverting spec on:" session-id)
    (println "\t:" message)
    (println "-------------------------------------------------")))

(with-pre-hook! #'unknown-api-call
  (fn [message connection]
    (println "Received an unrecognized message:")
    (println "\t" message)
    (println "-------------------------------------------------")))

(with-pre-hook! #'list-user-session-keys
  (fn [connection]
    (println "Listing session keys for:")
    (println "\t" connection)
    (println "-------------------------------------------------")))

(with-pre-hook! #'unknown-session-call
  (fn [connection message]
    (println "Received an unrecognized session message:")
    (println "\t" message)
    (println "-------------------------------------------------")))

(with-pre-hook! #'persist-empty-graph
  (fn [session-id graph-name]
    (println "Saving a new graph via Datomic for:")
    (println "\t" session-id)
    (println "-------------------------------------------------")))

(defn graph-contains-id? [graph id]
  (= (count (filter (fn [element] (= (uuid id) (:id element))) @graph)) 1))

(defn report-bad-id-failure
  ([socket] (report-bad-id-failure socket :bad-id))
  ([socket reason] {:success false :why reason}))

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
  (fn [e _ _ c] (report-bad-id-failure c)))

(with-handler! #'rename-box
  {:precondition :legal-id}
  (fn [e _ _ c] (report-bad-id-failure)))

(with-handler! #'move-box
  {:precondition :legal-id}
  (fn [e _ _ c] (report-bad-id-failure)))

(with-handler! #'create-connection
  {:precondition :legal-from-id}
  (fn [e _ _ c] (report-bad-id-failure :bad-src-id)))

(with-handler! #'create-connection
  {:precondition :legal-to-id}
  (fn [e _ _ c] (report-bad-id-failure :bad-dst-id)))

