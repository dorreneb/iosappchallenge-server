(defproject classifier "0.1.0-SNAPSHOT"
  :description "RIT iOS App challenge server"
  :url "https://github.com/dorreneb/iosappchallenge-server"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repositories {"stuart" "http://stuartsierra.com/maven2"}  
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.webbitserver/webbit "0.4.3"]
                 [org.clojure/tools.logging "0.2.6"]
                 [com.stuartsierra/lazytest "1.2.3"]
                 [com.datomic/datomic-free "0.8.3664"]
                 [log4j/log4j "1.2.17"]
                 [midje "1.4.0"]
                 [cheshire "5.0.2"]
                 [dire "0.4.0"]]
  :plugins [[lein-midje "2.0.3"]]
  :main classifier.core)

