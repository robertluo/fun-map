(defproject robertluo/fun-map "0.4.0-SNAPSHOT"
  :description "a map implementation blurs line between identity, state and function"
  :min-lein-version "2.7.0"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0" :scope "provided"]]
  :profiles {:dev
             {:dependencies [[manifold "0.1.8"]]
              :plugins      [[jonase/eastwood "0.3.4"]]}}
  :eastwood
  {:exclude-linters [:unused-ret-vals :redefd-vars]})
