(ns build
  (:require [clojure.tools.build.api :as b]
            [org.corfield.build :as cb]))

(defn project
  "apply project default to `opts`"
  [opts]
  (let [defaults {:lib     'robertluo/fun-map
                  :version (format "0.5.%s" (b/git-count-revs nil))
                  :scm     {:url "https://github.com/robertluo/fun-map"}}]
    (merge defaults opts)))

(defn tests
  [opts]
  (-> opts
      (cb/run-task [:dev :test])
      (cb/run-task [:dev :cljs-test])))

(defn ci
  [opts]
  (-> opts
      (project)
      (cb/clean)
      (tests)
      (cb/jar)))

(defn deploy
  [opts]
  (-> opts
      (project)
      (cb/deploy)))
