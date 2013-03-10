(defproject classifier "0.1.0-SNAPSHOT"
  :description "RIT iOS App challenge server"
  :url "https://github.com/dorreneb/iosappchallenge-server"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.0"]
                 [org.webbitserver/webbit "0.4.3"]
                 [cheshire "5.0.2"]
                 [dire "0.3.0"]]
  :main classifier.core)

