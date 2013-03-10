(ns classifier.core
  (:require [cheshire.core :refer [generate-string parse-string]])
  (:import [org.webbitserver WebServer WebServers WebSocketHandler HttpHandler]
           [org.webbitserver.handler StaticFileHandler]))

(def connections (ref #{}))
(def graph (agent []))

(defonce server (WebServers/createWebServer 8080))

(defn register-connection [connection]
  (dosync
   (commute connections conj connection))
  (.send connection (generate-string {:type :init :body @graph})))

(defn unregister-connection [connection]
  (dosync
   (commute connections disj connection)))

(defmulti update-graph :type)

(defmethod update-graph "create" [message]
  (println "Received creation event: " message)
  (dosync
   (send graph conj (:body message))
   (doseq [c @connections]
     (.send c (generate-string (merge {:type :create}
                                      (select-keys message [:body])))))))

(defmethod update-graph :default [a]
  (println "Received an unrecognized message: " a))

(.add server "/create-session"
      (proxy [WebSocketHandler] []
        (onOpen [c] (let [session-id (java.util.UUID/randomUUID)]
                      (add-session! session-id)
                      (.send c (generate-string {:session-id session-id}))))))

(defn add-session! [session-id]
  (.add server (str "/graph/" session-id)
        (proxy [WebSocketHandler] []
          (onOpen [c] (do (println "Connected: " c)
                          (register-connection c)))
          (onMessage [c m] (update-graph (:type (parse-string m true))))
          (onClose [c] (do (println "Disconnected: " c)
                           (unregister-connection c))))))

(defn -main [& args]
  (.start server))

