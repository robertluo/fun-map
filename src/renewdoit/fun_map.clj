(ns renewdoit.fun-map
  (:require
   [renewdoit.fun-map.impl.core :as impl]))

(defn fun-map
  [m]
  (impl/->FunMap impl/function-val-fn m))

(defmacro fnk
  [args & body]
  `(fn [{:keys ~args}]
     ~@body))
