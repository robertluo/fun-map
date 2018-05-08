(ns robertluo.fun-map
  "fun-map Api"
  (:require
   [robertluo.fun-map.core :as impl]))

(defn fun-map
  "Returns a new fun-map.

  when put a function as a value in a fun-map, it will be invoked
  with the map itself and returns the value as the value
  when referred by the key associated, and only
  be invoked once.

  :trace-fn is a side effect function accept key, value as the
   underlying function is really invoked"
  [m & {:keys [trace-fn]}]
  (impl/delegate-map* (partial impl/function-wrapper trace-fn) m))

(defn touch
  "forcefully evaluate all entries of a map"
  [m]
  (select-keys m (keys m)))

(defmacro fnk
  "a function with all its args take"
  {:style/indent [:defn]}
  [args & body]
  `(with-meta
     (fn [{:keys ~args}]
       ~@body)
     {:wrap true}))

(defn system-map
  "returns a fun-map can be shutdown orderly.
  
   any value adapts Closable in this map will be considered as a 
   component, it will be closed in reversed order of its invoking.
   Notice only accessed components will be shutdown."
  [m]
  (let [components (atom [])
        sys (fun-map m
               :trace-fn (fn [_ v] 
                           (when (satisfies? impl/Closeable v)
                             (swap! components conj v))))
        halt-fn (fn [] (doseq [comp (reverse @components)]
                         (impl/close comp)))]
      (vary-meta sys assoc ::impl/close-fn halt-fn)))

