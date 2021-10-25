(defproject robertluo/fun-map "0.4.5"
  :description "a map implementation blurs line between identity, state and function"
  :min-lein-version "2.7.0"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.3" :scope "provided"]]
  :profiles {:dev    {:dependencies [[manifold "0.1.9"]]
                      :plugins      [[jonase/eastwood "0.9.9"]]}
             :kaocha [:dev {:dependencies [[lambdaisland/kaocha "1.60.945"]]}]}
  :aliases {"kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]}
  :eastwood
  {:exclude-linters [:unused-ret-vals :redefd-vars]})
