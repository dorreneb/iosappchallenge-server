(ns classifier.persistence
  (:require [datomic.api :refer [q db] :as d]))

(def uri "datomic:free://localhost:4334/graphs-db")
(d/create-database uri)

(defonce connection (d/connect uri))
(def schema (read-string (slurp "database/schema.edn")))

@(d/transact connection schema)

(defn transaction-data [graph]
  [{:graphs/graph (pr-str @graph)
    :db/id #db/id [:db.part/user -1]}])

(defn save-data! [data]
  @(d/transact connection data))

