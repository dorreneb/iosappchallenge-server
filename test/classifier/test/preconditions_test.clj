(ns classifier.test.preconditions-test
  (:require [midje.sweet :refer :all]
            [classifier.session :refer :all]
            [classifier.logging :refer :all]))

(def id #uuid "0468d64a-ad80-43e3-b24c-ae3253be196a")

(fact (graph-contains-id? (agent []) {:id id}) => false)
(fact (graph-contains-id? (agent [{:id id}]) id) => true)

(let [response (delete-box (agent []) {:id id} :me)]
  (fact (:success response) => false)
  (fact (:why response) => :bad-id))

(let [response (create-connection (agent [{:type "box" :id id}]) {:body {:to id}} :me)]
  (fact (:success response) => false)
  (fact (:why response) => :bad-src-id))

(let [response (create-connection (agent [{:type "box" :id id}]) {:from id} :me)]
  (fact (:success response) => false)
  (fact (:why response) => :bad-dst-id))

