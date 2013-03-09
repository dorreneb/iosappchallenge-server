(ns classifier.core
  (:require [cheshire.core :refer [generate-string parse-string]])
  (:import [org.webbitserver WebServer WebServers WebSocketHandler HttpHandler]
           [org.webbitserver.handler StaticFileHandler]))

(def connections (ref #{}))
(def graph (agent []))

(defonce server (WebServers/createWebServer 8080))

(defn register-connection [connection]
  (dosync
   (commute connections disj connection))
  (.send connection (generate-string {:type :init :message @graph})))

(defn unregister-connection [connection]
  (dosync
   (commute connections conj connection)))

(defn update-graph [connection message]
  (let [json (parse-string message)]
    (dosync
     (send graph conj message)
     (doseq [c @connections]
       (.send c (generate-string {:type :update :message message}))))))

(.add server "/graph"
      (proxy [WebSocketHandler] []
        (onOpen [c] (do (println "Connected: " c)
                        (register-connection c)))
        (onMessage [c m] (do (println c ":" m)
                             (update-graph c m)))
        (onClose [c] (do (println "Disconnected: " c)
                         (unregister-connection c)))))

(defn -main [& args]
  (.start server))

