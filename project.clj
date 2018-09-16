(defproject robertluo/fun-map "0.2.2-SNAPSHOT"
  :description "a map implementation blurs line between identity, state and function"
  :min-lein-version "2.7.0"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0" :scope "provided"]]
  :profiles {:dev
             {:plugins [[jonase/eastwood "0.2.9"]]}}
  :eastwood
  {:exclude-linters [:unused-ret-vals]})
