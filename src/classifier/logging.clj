(ns classifier.logging
  (:require [clojure.tools.logging :refer [info error]]
            [dire.core :refer [with-pre-hook!]]
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
    (info "Creating box on session " session-id " with message " message)))

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
  (fn [graph {:keys [session-id] :as message} connection]
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

