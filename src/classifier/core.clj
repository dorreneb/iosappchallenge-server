(ns classifier.core
  (:require [cheshire.core :refer [generate-string parse-string]]
            [datomic.api :refer [q db] :as d]
            [dire.core :refer [with-pre-hook!]])
  (:import [org.webbitserver WebServer WebServers WebSocketHandler HttpHandler]
           [org.webbitserver.handler StaticFileHandler]))

(declare add-session-route)
(declare add-session!)

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

(defn graph-for-session-id [id]
  (q '[:find ?e :in $ ?id :where [?e :graphs/session-id ?id]] (db connection) id))

(defn persist-session [session-id]
  (fn [_ _ _ state]
    (println "Updating via Datomic.")
    (update-session session-id state)))

(defn generate-routes []
  (doseq [session (all-sessions)]
    (add-session-route (str (:graphs/session-id session)))
    (add-session! (str (:graphs/session-id session)) (read-string (:graphs/graph session)))))

;;;;;;;;;;;;;;; In Memory Sessions ;;;;;;;;;;;;;;;;;

(def sessions (ref {}))

(defonce server (WebServers/createWebServer 8080))

(defn register-connection [id connection]
  (dosync
   (commute (get-in @sessions [id :connections]) conj connection))
  (.send connection (generate-string {:type :init :body @(get-in @sessions [id :graph])})))

(defn unregister-connection [id connection]
  (dosync
   (commute (get-in @sessions [id :connections]) disj connection)))

(defn unknown-api-call [message])

(defn create-box [{:keys [session-id body] :as message}]
  (dosync
   (send (get-in @sessions [session-id :graph]) conj body)
   (doseq [c @(get-in @sessions [session-id :connections])]
     (.send c (generate-string (merge {:type :create}
                                      (select-keys message [:body])))))))

(defmulti update-graph :type)

(defmethod update-graph "create" [message] (create-box message))
(defmethod update-graph :default [message] (unknown-api-call message))

(defn add-session-route [session-id]
  (.add server (str "/graph/" session-id)
        (proxy [WebSocketHandler] []
          (onOpen [c]      (register-connection session-id c))
          (onMessage [c m] (update-graph (assoc (parse-string m true) :session-id session-id)))
          (onClose [c]     (unregister-connection session-id c)))))

(defn add-session! [session-id initial-state]
  (dosync
   (commute sessions assoc session-id {:connections (ref #{}) :graph (agent initial-state)}))
  (add-session-route session-id)
  (add-watch (get-in @sessions [session-id :graph]) :datomic (persist-session session-id)))

(defn create-new-user-session [connection]
  (let [session-id (java.util.UUID/randomUUID)]
    (add-session! session-id [])
    (.send connection (generate-string {:session-id session-id}))))

(.add server "/create-session"
      (proxy [WebSocketHandler] []
        (onOpen [c] (create-new-user-session c))
        (onMessage [c m])
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
  (fn [{:keys [session-id] :as message}]
    (println "-------------------------------------------------")
    (println "Received creation event on:" session-id)
    (println "\t" message)
    (println "-------------------------------------------------")))

(with-pre-hook! #'unknown-api-call
  (fn [message]
    (println "-------------------------------------------------")
    (println "Received an unrecognized message:")
    (println "\t" message)
    (println "-------------------------------------------------")))

(defn -main [& args]
  (generate-routes)
  (.start server))

