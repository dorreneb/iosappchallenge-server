(ns classifier.core
  (:require [classifier.session :refer [init-server]]
            [classifier.logging :as logger]))

(defn -main [& args]
  (apply init-server args))

