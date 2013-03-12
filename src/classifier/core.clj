(ns classifier.core
  (:require [cheshire.core :refer [generate-string parse-string]]
            [datomic.api :refer [q db] :as d]
            [dire.core :refer [with-pre-hook!]])
  (:import [org.webbitserver WebServer WebServers WebSocketHandler HttpHandler]
           [org.webbitserver.handler StaticFileHandler]))

(declare add-session-route)
(declare add-session!)

(defn uuid []
  (java.util.UUID/randomUUID))

;;;;;;;;;;;; Datomic persistence ;;;;;;;;;;;;;;;;;

(def uri "datomic:free://localhost:4334/graphs")
(d/create-database uri)

(defonce connection (d/connect uri))
(def schema (read-string (slurp "database/schema.edn")))

@(d/transact connection schema)

(defn- update-session [session-id data]
  (d/transact
   connection
   [{:graphs/session-id session-id
     :db/id #db/id [:db.part/user]
     :graphs/graph (pr-str data)}]))

(defn all-sessions []
  (map
   (fn [[id]]
     (-> connection db (d/entity id)))
   (q '[:find ?e :where [?e :graphs/session-id]] (db connection))))

(defn persist-session [session-id]
  (fn [_ _ _ state]
    (println "Updating via Datomic.")
    (if (string? session-id)
      (update-session (java.util.UUID/fromString session-id) state)
      (update-session session-id state))))

(defn generate-routes []
  (doseq [session (all-sessions)]
    (add-session-route (str (:graphs/session-id session)))
    (add-session! (str (:graphs/session-id session)) (read-string (:graphs/graph session)))))

;;;;;;;;;;;;;;; In Memory Sessions ;;;;;;;;;;;;;;;;;

(def sessions (ref {}))

(defn graph-agent [session-id]
  (get-in @sessions [session-id :graph]))

(defn connections-ref [session-id]
  (get-in @sessions [session-id :connections]))

(defonce server (WebServers/createWebServer 8080))

(defn register-connection [session-id connection]
  (dosync
   (commute (connections-ref session-id) conj connection))
  (.send connection (generate-string {:type :init :body @(graph-agent session-id)})))

(defn unregister-connection [session-id connection]
  (dosync
   (commute (connections-ref session-id) disj connection)))

(defn unknown-api-call [message requester])

(defn exclude-client [session-id excluder]
  (disj @(connections-ref session-id) excluder))

(defn strip-locals [coll]
  (dissoc coll :locals))

(defn dispatch-create-box-to-client [who body]
  (.send who (generate-string (merge {:type :create-box} {:body body}))))

(defn exclusive-broadcast-create-box [session-id excluder body]
  (doseq [c (exclude-client session-id excluder)]
    (dispatch-create-box-to-client c (strip-locals body))))

(defn create-box [{:keys [session-id body] :as message} me]
  (let [body (assoc body :id (uuid))]
    (dosync
     (send (graph-agent session-id) conj (strip-locals body))
     (dispatch-create-box-to-client me body)
     (exclusive-broadcast-create-box session-id me body))))

(defmulti update-graph
  (fn [message connection]
    (:type message)))

(defmethod update-graph "create" [message requester] (create-box message requester))
(defmethod update-graph :default [message requester] (unknown-api-call message requester))

(defn add-session-route [session-id]
  (.add server (str "/graph/" session-id)
        (proxy [WebSocketHandler] []
          (onOpen [c]      (register-connection session-id c))
          (onMessage [c m] (update-graph (assoc (parse-string m true) :session-id session-id) c))
          (onClose [c]     (unregister-connection session-id c)))))

(defn add-session! [session-id initial-state]
  (dosync
   (commute sessions assoc session-id {:connections (ref #{}) :graph (agent initial-state)}))
  (add-session-route session-id)
  (add-watch (graph-agent session-id) :datomic (persist-session session-id)))

(defn list-user-session-keys [connection]
  (.send connection (generate-string {:sessions (map :graphs/session-id (all-sessions))})))

(defn persist-empty-graph [session-id]
  (send (graph-agent session-id) identity))

(defn create-new-user-session [connection]
  (let [session-id (uuid)]
    (add-session! session-id [])
    (persist-empty-graph session-id)
    (.send connection (generate-string {:session-id session-id}))))

(defn unknown-session-call [connection message])

(defmulti dispatch-session-command
  (fn [_ message]
    (:type (parse-string message true))))

(defmethod dispatch-session-command "create" [connection message]
  (create-new-user-session connection))

(defmethod dispatch-session-command :default [connection message]
  (unknown-session-call connection message))

(.add server "/sessions"
      (proxy [WebSocketHandler] []
        (onOpen [c] (list-user-session-keys c))
        (onMessage [c m] (dispatch-session-command c m))
        (onClose [c])))

;;;;;;;;;;;;; Logging Hooks ;;;;;;;;;;;;;;;

(with-pre-hook! #'register-connection
  (fn [id connection]
    (println "-------------------------------------------------")
    (println "Registered connection on:" id)
    (println "\t" connection)
    (println "-------------------------------------------------")))

(with-pre-hook! #'unregister-connection
  (fn [id connection]
    (println "-------------------------------------------------")
    (println "Unregistered connection on:" id)
    (println "\t" connection)
    (println "-------------------------------------------------")))

(with-pre-hook! #'add-session!
  (fn [session-id _]
    (println "-------------------------------------------------")
    (println "Creating new graph session:" session-id)
    (println "-------------------------------------------------")))

(with-pre-hook! #'create-box
  (fn [{:keys [session-id] :as message} connection]
    (println "-------------------------------------------------")
    (println "Received creation event on:" session-id)
    (println "\t" message)
    (println "-------------------------------------------------")))

(with-pre-hook! #'unknown-api-call
  (fn [message connection]
    (println "-------------------------------------------------")
    (println "Received an unrecognized message:")
    (println "\t" message)
    (println "-------------------------------------------------")))

(with-pre-hook! #'list-user-session-keys
  (fn [connection]
    (println "-------------------------------------------------")
    (println "Listing session keys for:")
    (println "\t" connection)
    (println "-------------------------------------------------")))

(with-pre-hook! #'unknown-session-call
  (fn [connection message]
    (println "-------------------------------------------------")
    (println "Received an unrecognized session message:")
    (println "\t" message)
    (println "-------------------------------------------------")))

(with-pre-hook! #'persist-empty-graph
  (fn [session-id]
    (println "-------------------------------------------------")
    (println "Saving a new graph via Datomic for:")
    (println "\t" session-id)
    (println "-------------------------------------------------")))

(defn -main [& args]
  (generate-routes)
  (.start server))

