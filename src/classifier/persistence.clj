(ns classifier.persistence
  (:require [datomic.api :refer [q db] :as d]))

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

