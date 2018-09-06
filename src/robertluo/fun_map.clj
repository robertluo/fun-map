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
   (wrap-f f trace-fn nil))
  ([f trace-fn focus-fn]
   (impl/fn-wrapper f trace-fn focus-fn)))

(defmacro fw
  "Define an anonymous function in place to be used in fun-map.
  When accessed by key, it will be called with the map itself. So the arg-map
  is a map following clojure's destructure syntax with two additional optional
  keys:

   - :focus A form that will be called to check if the function itself need
     to be called. It must be pure functional and very quick.
   - :trace A trace function, if the value updated, it will be called with key
     and the vale.

  Example:
    (fw {:keys [a b] :as m
         :trace (fn [k v] (println k v))
         :focus (select-keys m [:a :b])}
      (+ a b))"
  {:style/indent 1}
  [arg-map & body]
  (let [{:keys [trace focus]} arg-map
        arg-map (dissoc arg-map :trace :focus)]
    `(wrap-f
      (fn [~arg-map]
        ~@body)
      ~(when trace trace)
      ~(when focus `(fn [~arg-map] ~focus)))))

(defmacro fnk
  "Define an anonymous function intended to use in fun-map.
  Its arguments will be take from the corresponding keys of the map, and
  the value will be changed when its dependencies change."
  {:style/indent 1}
  [args & body]
  `(fw {:keys  ~args
        :as    m#
        :focus (select-keys m# ~(mapv keyword args))}
     ~@body))

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
