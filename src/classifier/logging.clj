(ns classifier.logging
  (:require [clojure.tools.logging :refer [info error]]
            [dire.core :refer [with-pre-hook!]]
            [classifier.session :refer :all]))

(with-pre-hook! #'register-connection
  (fn [session-id connection]
    (info "Connection " connection " registered on " session-id)))

(with-pre-hook! #'unregister-connection
  (fn [session-id connection]
    (info "Connection " connection " unregistered on " session-id)))

(with-pre-hook! #'add-session!
  (fn [session-id graph-name _]
    (info "Creating a new graph named " graph-name " on " session-id)))

(with-pre-hook! #'create-box
  (fn [{:keys [session-id] :as message} connection]
    (info "Creating box on session " session-id " with " message)))

(with-pre-hook! #'rename-box
  (fn [{:keys [session-id] :as message} connection]
    (info "Renaming a box on " session-id " with " message)))

(with-pre-hook! #'move-box
  (fn [{:keys [session-id] :as message} connection]
    (info "Moving a box on " session-id " with " message)))

(with-pre-hook! #'delete-box
  (fn [graph {:keys [session-id] :as message} me]
    (info "Deleting a box on " session-id " with " message)))

(with-pre-hook! #'create-connection
  (fn [graph {:keys [session-id] :as message} connection]
    (info "Creating a connection on " session-id " with " message)))

(with-pre-hook! #'delete-connection
  (fn [{:keys [session-id] :as message}]
    (info "Deleting a connection on " session-id " with " message)))

(with-pre-hook! #'number-of-connections
  (fn [{:keys [session-id] :as message} connection]
    (info "Number of connections requested by " connection " with" message)))

(with-pre-hook! #'graph-revisions
  (fn [{:keys [session-id] :as message} connection]
    (info "Listing the revisions for " session-id " with " message)))

(with-pre-hook! #'spec-revision
  (fn [{:keys [session-id] :as message} connection]
    (info "Requesting a revision snapshot on " session-id " with " message)))

(with-pre-hook! #'revert
  (fn [{:keys [session-id transaction-id] :as message}]
    (info "Reverting the spec on " session-id " with " message)))

(with-pre-hook! #'list-user-session-keys
  (fn [connection]
    (info "Listing the session keys for " connection)))

(with-pre-hook! #'persist-empty-graph
  (fn [session-id graph-name]
    (info "Creating a new, blank spec for " session-id " named " graph-name)))

(with-pre-hook! #'unknown-api-call
  (fn [message connection]
    (error "Received an unknown spec API call from " connection " with " message)))

(with-pre-hook! #'unknown-session-call
  (fn [connection message]
    (error "Received an unkown session API call from " connection " with " message)))

