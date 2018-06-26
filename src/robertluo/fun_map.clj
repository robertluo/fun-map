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
  (doseq [[_ _] m] nil)
  m)

(defmacro fnk
  "a function with all its args taken from a map, args are the
  values of corresponding keys by args' name"
  {:style/indent 1}
  [args & body]
  `(with-meta
     (fn [{:keys ~args}]
       ~@body)
     {:wrap true}))

;;;;;; life cycle map

(defprotocol Haltable
  "Life cycle protocol, signature just like java.io.Closeable,
  being a protocol gives user ability to extend"
  (halt! [this]))

;; make it compatible with java.io.Closeable
(extend-protocol Haltable
  java.io.Closeable
  (halt! [this]
    (.close this)))

(defn life-cycle-map
  "returns a fun-map can be shutdown orderly.

   any value supports `Closeable` in this map will be considered as a
   component, it will be closed in reversed order of its invoking.
   Notice only accessed components will be shutdown."
  [m]
  (let [components (atom [])
        sys        (fun-map m
                            :trace-fn (fn [_ v]
                                        (when (satisfies? Haltable v)
                                          (swap! components conj v))))
        halt-fn    (fn [_] (doseq [component (reverse @components)]
                             (halt! component)))]
      (vary-meta sys assoc ::impl/close-fn halt-fn)))

(defn closeable [value close-fn]
  (impl/->CloseableValue value close-fn))
