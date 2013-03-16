(ns classifier.core
  (:require [cheshire.core :refer [generate-string parse-string]]
            [datomic.api :refer [q db] :as d]
            [dire.core :refer [with-pre-hook!]])
  (:import [org.webbitserver WebServer WebServers WebSocketHandler HttpHandler]
           [org.webbitserver.handler StaticFileHandler]))

(declare add-session-route)
(declare add-session!)

(defn uuid
  ([] (java.util.UUID/randomUUID))
  ([x] (if (string? x)
         (java.util.UUID/fromString x)
         x)))

;;;;;;;;;;;; Datomic persistence ;;;;;;;;;;;;;;;;;

(def uri "datomic:free://localhost:4334/graphs")
(d/create-database uri)

(defonce connection (d/connect uri))
(def schema (read-string (slurp "database/schema.edn")))

@(d/transact connection schema)

(defn update-session [session-id graph-name data]
  (d/transact
   connection
   [{:graphs/session-id session-id
     :graphs/name graph-name
     :db/id #db/id [:db.part/user]
     :graphs/graph (pr-str data)}]))

(defn all-sessions []
  (map
   (fn [[id]]
     (-> connection db (d/entity id)))
   (q '[:find ?e :where [?e :graphs/session-id]] (db connection))))

(defn session [session-id]
  (-> connection db
      (d/entity
       (ffirst (q '[:find ?e :in $ ?id :where [?e :graphs/session-id ?id]]
                  (db connection) session-id)))))

(defn revisions [id]
  (reverse (sort-by :transaction-id
                    (map (fn [[a b]]
                           {:transaction-id a :timestamp b})       
                         (q '[:find ?tx ?tx-time
                              :in $ ?e ?a
                              :where [?e ?a ?v ?tx _]
                              [?tx :db/txInstant ?tx-time]]
                            (d/history (db connection))
                            (:db/id id)
                            :graphs/graph)))))

(defn revision [tx-id]
  (read-string
   (ffirst
    (q '[:find ?v
         :in $ ?a ?tx-id 
         :where
         [?e ?a ?v ?tx _]
         [(= ?tx-id ?tx)]]
       (d/history (db connection))
       :graphs/graph
       tx-id))))

(defn persist-session [session-id graph-name]
  (fn [_ _ _ state]
    (println "Updating via Datomic.")
    (if (string? session-id)
      (update-session (java.util.UUID/fromString session-id) graph-name state)
      (update-session session-id graph-name state))))

(defn generate-routes []
  (doseq [session (all-sessions)]
    (add-session-route (str (:graphs/session-id session)))
    (add-session! (str (:graphs/session-id session))
                  (:graphs/name session)
                  (read-string (:graphs/graph session)))))

;;;;;;;;;;;;;;; In Memory Sessions ;;;;;;;;;;;;;;;;;

(def sessions (ref {}))

(defn graph-agent [session-id]
  (get-in @sessions [session-id :graph]))

(defn connections-ref [session-id]
  (get-in @sessions [session-id :connections]))

(defonce server (WebServers/createWebServer 8080))

(defn logical-load-ordering [coll]
  (sort-by #(keyword (:type %)) coll))

(defn register-connection [session-id connection]
  (dosync
   (commute (connections-ref session-id) conj connection))
  (.send connection (generate-string {:type :init :body (logical-load-ordering @(graph-agent session-id))})))

(defn unregister-connection [session-id connection]
  (dosync
   (commute (connections-ref session-id) disj connection)))

(defn unknown-api-call [message requester])

(defn broadcast [session-id message]
  (doseq [c @(connections-ref session-id)]
    (.send c message)))

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

(defn find-element [session-id element-id]
  (second
   (first (filter
           (fn [[element k]]
             (= element-id (str (:id element))))
           (map list @(graph-agent session-id) (range))))))

(defn dispatch-move-box-to-client [who body]
  (.send who (generate-string (merge {:type :move-box} {:body body}))))

(defn exclusive-broadcast-move-box [session-id excluder body]
  (doseq [c (exclude-client session-id excluder)]
    (dispatch-move-box-to-client c (strip-locals body))))

(defn move-box [{:keys [session-id body] :as message} me]
  (dosync
   (let [n (find-element session-id (:id body))]
     (send (graph-agent session-id)
           (fn [state]
             (update-in (vec state) [n :location]
                        (constantly {:x (:x (:location body)) :y (:y (:location body))}))))
     (dispatch-move-box-to-client me body)
     (exclusive-broadcast-move-box session-id me body))))

(defn rename-box [{:keys [session-id body] :as message} me]
  (dosync
   (let [n (find-element session-id (:id body))]
     (send (graph-agent session-id)
           (fn [state] (update-in (vec state) [n :name] (constantly (:name body)))))
     (broadcast session-id (generate-string {:type :rename-box
                                             :body {:id (:id body) :name (:name body)}})))))

(defn element-by-uuid [session-id element-id]
  (first (filter #(= (uuid element-id) (:id %)) @(graph-agent session-id))))

(defn connectors-only [coll]
  (filter (fn [entity] (= (keyword (:type entity)) :connection)) coll))

(defn connectors-for-box [session-id box-id]
  (filter
   (fn [entity]
     (or (= (:from entity) box-id)
         (= (:to entity) box-id)))
   (connectors-only @(graph-agent session-id))))

(defn delete-connection [{:keys [session-id id] :as message}]
  (dosync
   (let [element (element-by-uuid session-id id)]
     (send (graph-agent session-id) (fn [state] (filter (partial not= element) state)))
     (broadcast session-id (generate-string {:type :delete-connection :id (:id element)})))))

(defn delete-connectors-for-box [session-id id]
  (doseq [connector-id (map :id (connectors-for-box session-id id))]
    (delete-connection {:session-id session-id :id connector-id})))

(defn delete-box [{:keys [session-id id] :as message}]
  (dosync
   (let [element (element-by-uuid session-id id)]
     (delete-connectors-for-box session-id id)
     (send (graph-agent session-id) (fn [state] (filter (partial not= element) state)))
     (broadcast session-id (generate-string {:type :delete-box :id (:id element)})))))

(defn dispatch-create-connection-to-client [who body]
  (.send who (generate-string {:type :create-connection :body body})))

(defn exclusive-broadcast-create-connection [session-id excluder body]
  (doseq [c (exclude-client session-id excluder)]
    (dispatch-create-connection-to-client c (strip-locals body))))

(defn create-connection [{:keys [session-id body] :as message} me]
  (let [body (assoc body :id (uuid) :type :connection)]
    (dosync
     (send (graph-agent session-id) conj (strip-locals body))
     (dispatch-create-connection-to-client me body)
     (exclusive-broadcast-create-connection session-id me body))))

(defn number-of-connections [{:keys [session-id body] :as message} me]
  (.send me (generate-string {:n-connections (count @(connections-ref session-id))})))

(defn graph-revisions [{:keys [session-id body] :as message} me]
  (.send me (generate-string {:type :revisions :revisions (revisions (session (uuid session-id)))})))

(defn spec-revision [{:keys [transaction-id] :as message} me]
  (.send me (generate-string {:type :revision :revision (revision transaction-id)})))

(defn revert [{:keys [session-id transaction-id]}]
  (dosync
   (let [spec (revision transaction-id)]
     (send (graph-agent session-id) (constantly spec))))
  (prn "AND")
  (prn "The state is: " @(graph-agent session-id))
  (prn "AND")
  (prn "Coming back: " (logical-load-ordering @(graph-agent session-id)))
  (broadcast session-id (generate-string {:type :revert :revert (logical-load-ordering @(graph-agent session-id))})))

(defmulti update-graph
  (fn [message connection]
    (:type message)))

(defmethod update-graph "create" [message requester]
  (create-box message requester))

(defmethod update-graph "rename-box" [message requester]
  (rename-box message requester))

(defmethod update-graph "move-box" [message requester]
  (move-box message requester))

(defmethod update-graph "delete-box" [message _]
  (delete-box message))

(defmethod update-graph "create-connection" [message requester]
  (create-connection message requester))

(defmethod update-graph "delete-connection" [message _]
  (delete-connection message))

(defmethod update-graph "n-connections" [message requester]
  (number-of-connections message requester))

(defmethod update-graph "revisions" [message requester]
  (graph-revisions message requester))

(defmethod update-graph "spec-revision" [message requester]
  (spec-revision message requester))

(defmethod update-graph "revert" [message _]
  (revert message))

(defmethod update-graph :default [message requester]
  (unknown-api-call message requester))

(defn add-session-route [session-id]
  (.add server (str "/graph/" session-id)
        (proxy [WebSocketHandler] []
          (onOpen [c]      (register-connection session-id c))
          (onMessage [c m] (update-graph (assoc (parse-string m true) :session-id session-id) c))
          (onClose [c]     (unregister-connection session-id c)))))

(defn add-session! [session-id graph-name initial-state]
  (dosync
   (commute sessions assoc session-id {:connections (ref #{}) :graph (agent initial-state)}))
  (add-session-route session-id)
  (add-watch (graph-agent session-id) :datomic (persist-session session-id graph-name)))

(defn describe-open-session [session]
  {:session-id (:graphs/session-id session)
   :session-name (:graphs/name session)})

(defn list-user-session-keys [connection]
  (.send connection
         (generate-string {:type :session-listing
                           :sessions (map describe-open-session (all-sessions))})))

(defn persist-empty-graph [session-id graph-name]
  (send (graph-agent session-id) identity))

(defn create-new-user-session [connection graph-name]
  (let [session-id (uuid)]
    (add-session! session-id graph-name [])
    (persist-empty-graph session-id graph-name)
    (.send connection (generate-string {:type :new-spec :session-id session-id :graph-name graph-name}))))

(defn unknown-session-call [connection message])

(defmulti dispatch-session-command
  (fn [_ message]
    (:type (parse-string message true))))

(defmethod dispatch-session-command "create" [connection message]
  (create-new-user-session connection (:spec-name (parse-string message true))))

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
  (fn [{:keys [session-id] :as message}]
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

(defn -main [& args]
  (generate-routes)
  (.start server))

