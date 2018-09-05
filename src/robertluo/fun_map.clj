(ns robertluo.fun-map
  "fun-map Api"
  (:require
   [robertluo.fun-map.core :as impl]))

(defn fun-map
  "Returns a new fun-map.

  when put a function as a value in a fun-map, it will be invoked
  with the map itself and returns the value as the value
  when referred by the key associated, and only
  be invoked once. "
  [m & {:keys [trace-fn]}]
  (with-meta
    (impl/delegate-map m)
    {::impl/trace trace-fn}))

(defn touch
  "forcefully evaluate all entries of a map"
  [m]
  (doseq [[_ _] m] nil)
  m)

(defn wrap-f
  "Wrap a function f and returns a wrapper to be used as a value
  in fun-map, f should accept fun-map as its single argument, its return
  value can be accessed by its key"
  ([f]
   (wrap-f f nil))
  ([f trace-fn]
   (impl/fn-wrapper f trace-fn)))

(defmacro fw
  "define an anonymous function in place and wrap it as a value"
  [args & body]
  `(wrap-f
    (fn [~args]
      ~@body)))

(defmacro fnk
  "a function with all its args taken from a map, args are the
  values of corresponding keys by args' name"
  {:style/indent 1}
  [args & body]
  `(wrap-f
    (fn [{:keys ~args}]
      ~@body)))

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
        halt-fn (fn [_]
                  (doseq [component (reverse @components)]
                    (halt! component)))]
    (vary-meta sys assoc ::impl/close-fn halt-fn)))

(defn closeable
  "returns a wrapped plain value, which implements IDref and Closeable,
   the close-fn is an effectual function with no argument.
   when used inside a life cycle map, its close-fn when get called when
   closing the map."
  [r close-fn]
  (impl/->CloseableValue r close-fn))
