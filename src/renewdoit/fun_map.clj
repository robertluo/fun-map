(ns renewdoit.fun-map
  (:require
   [renewdoit.fun-map.core :as impl]))

(defn fun-map
  "Returns a new fun-map.

  when put a function as a value in a fun-map, it will be invoked
  with the map itself and returns the value as the value
  when referred by the key associated, and only
  be invoked once."
  [m & {:keys [trace-fn]}]
  (impl/delegate-map* (partial impl/function-wrapper trace-fn) m))

(defn touch
  "forcefully evaluate all entries of a map"
  [m]
  (select-keys m (keys m)))

(defmacro fnk
  "a function with all its args take"
  [args & body]
  `(fn [{:keys ~args}]
     ~@body))
