(ns renewdoit.fun-map
  (:require
   [renewdoit.fun-map.impl.core :as impl]))

(defn fun-map
  "Returns a new f-map"
  [m]
  (-> (impl/->FunMap impl/function-val-fn {})
      (into m)))

(defmacro fnk
  [args & body]
  `(fn [{:keys ~args}]
     ~@body))
