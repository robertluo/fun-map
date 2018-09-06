(ns robertluo.fun-map
  "fun-map Api"
  (:require
   [robertluo.fun-map.core :as impl]))

(defn fun-map
  "Returns a new fun-map.

  A fun-map is a special map which will automatically *unwrap* a value if it's a 
  wrapper when accessed by key. A wrapper is anything which wrapped a ordinary value
  inside. Many clojure data structures are wrapper, such as atom, ref, future, delay,
  agent etc. In fact, anything implements clojure.lang.IDRef interface is a wrapper.
  
  FuntionWrapper is another wrapper can be used in a fun-map, which wraps a function,
  it will be called with the fun-map itself as the argument.
  
  Map m is the underlying storage of a fun-map, fun-map does not change its property
  except accessing values.
  
  Options:
  
   - :trace-fn An Effectful function for globally FunctionWrapper calling trace which
     accept key and value as its argument.
  
  Example:
  
    (fun-map {:a 35 :b (delay (println \"hello from b!\"))}"
  [m & {:keys [trace-fn]}]
  (with-meta
    (impl/delegate-map m)
    {::impl/trace trace-fn}))

(defn touch
  "Forcefully evaluate all entries of a map and returns itself."
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
  "Returns a FunctionWrapper of an anonymous function defined by body.

   Since a FunctionWrapper's function will be called with the map itself as the
   argument, this macro using a map `arg-map` as its argument. It follows the 
   same syntax of clojure's associative destructure. You may use `:keys`, `:as`,
   `:or` inside.
  
   Two special keys available though:

    - :focus A form that will be called to check if the function itself need
      to be called. It must be pure functional and very effecient.
    - :trace A trace function, if the value updated, it will be called with key
      and the function's return value.

   Example:
  
    (fw {:keys  [a b]
         :as    m
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
  "A shortcut for `fw` macro. Returns a simple FunctionWrapper which depends on
  `args` key of the fun-map, it will *focus* on the keys also."
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

   Any FunctionWrapper supports `Closeable` in this map will be considered
   as a component, its `close` method will be called in reversed order of its
   creation when the map itself closing.
  
   Notice only accessed components will be shutdown."
  [m]
  (let [components (atom [])
        trace-fn (fn [_ v]
                   (when (satisfies? Haltable v)
                     (swap! components conj v)))
        sys        (fun-map m :trace-fn trace-fn)
        halt-fn (fn [_]
                  (doseq [component (reverse @components)]
                    (halt! component)))]
    (vary-meta sys assoc ::impl/close-fn halt-fn)))

(defn closeable
  "Returns a wrapped plain value, which implements IDref and Closeable,
   the close-fn is an effectual function with no argument.
  
   When used inside a life cycle map, its close-fn when get called when
   closing the map."
  [r close-fn]
  (impl/->CloseableValue r close-fn))
