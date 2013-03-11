(ns classifier.persistence
  (:require [datomic.api :refer [q db] :as d]))

(def uri "datomic:free://localhost:4334/fake-db")
(d/create-database uri)

(defonce connection (d/connect uri))
(def schema (read-string (slurp "database/schema.edn")))

@(d/transact connection schema)


