(ns robertluo.pull
  (:require [robertluo.pull.impl :as impl]))

(defn pull
  [ptn m]
  (impl/-pull m ptn))
