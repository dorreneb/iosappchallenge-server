(ns classifier.core
  (:require [cheshire.core :refer [generate-string parse-string]]
            [dire.core :refer [with-pre-hook!]]
            [classifier.persistence :refer [persist-session]])
  (:import [org.webbitserver WebServer WebServers WebSocketHandler HttpHandler]
           [org.webbitserver.handler StaticFileHandler]))

(def sessions (ref {}))

(defonce server (WebServers/createWebServer 8080))

(defn register-connection [id connection]
  (dosync
   (commute (get-in @sessions [id :connections]) conj connection))
  (.send connection (generate-string {:type :init :body @(get-in @sessions [id :graph])})))

(defn unregister-connection [id connection]
  (dosync
   (commute (get-in @sessions [id :connections]) disj connection)))

(defmulti update-graph :type)

(defmethod update-graph "create" [{:keys [session-id body] :as message}]
  (println "Received creation event on" session-id ":" message)
  (dosync
   (send (get-in @sessions [session-id :graph]) conj body)
   (doseq [c @(get-in @sessions [session-id :connections])]
     (.send c (generate-string (merge {:type :create}
                                      (select-keys message [:body])))))))

(defmethod update-graph :default [message]
  (println "Received an unrecognized message: " message))

(defn add-session-route [session-id]
  (.add server (str "/graph/" session-id)
        (proxy [WebSocketHandler] []
          (onOpen [c]      (register-connection session-id c))
          (onMessage [c m] (update-graph (assoc (:type (parse-string m true)) :session-id session-id)))
          (onClose [c]     (unregister-connection session-id c)))))

(defn add-session! [session-id]
  (dosync
   (commute sessions assoc session-id {:connections (ref #{}) :graph (agent [])}))
  (add-session-route session-id)
  (add-watch (get-in @sessions [session-id :graph]) :datomic (persist-session session-id)))

(defn create-new-user-session [connection]
  (let [session-id (java.util.UUID/randomUUID)]
    (add-session! session-id)
    (.send connection (generate-string {:session-id session-id}))))

(.add server "/create-session"
      (proxy [WebSocketHandler] []
        (onOpen [c] (create-new-user-session c))
        (onMessage [c m])
        (onClose [c])))

(with-pre-hook! #'register-connection
  (fn [id connection] (println "Registered connection on " id ":" connection)))

(with-pre-hook! #'unregister-connection
  (fn [id connection] (println "Unregistered connection on " id ": " connection)))

(with-pre-hook! #'add-session!
  (fn [session-id] (println "Creating new session " session-id)))

(defn -main [& args]
  (.start server))

