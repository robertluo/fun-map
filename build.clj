(ns build
  (:require [clojure.tools.build.api :as b]
            [org.corfield.build :as cb]))

(def lib 'robertluo/fun-map)
(def version (format "0.5.%s" (b/git-count-revs nil)))

(defn ci
  [opts]
  (-> opts
      (assoc :lib lib :version version)
      (cb/clean)
      (cb/run-tests)
      (cb/jar)))

(defn deploy
  [opts]
  (-> opts
      (assoc :lib lib :version version)
      (cb/deploy)))
