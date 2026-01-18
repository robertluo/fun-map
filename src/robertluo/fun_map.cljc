(ns robertluo.fun-map
  "fun-map Api"
  (:require
   [robertluo.fun-map.core :as core]
   [robertluo.fun-map.wrapper :as wrapper]
   #?(:clj
      [robertluo.fun-map.helper :as helper])))

(defn fun-map
  "Returns a new fun-map.

  A fun-map is a special map which will automatically *unwrap* a value if it's a
  wrapper when accessed by key. A wrapper is anything which wrapped a ordinary value
  inside. Many clojure data structures are wrapper, such as atom, ref, future, delay,
  agent etc. In fact, anything implements clojure.lang.IDeref interface is a wrapper.

  FunctionWrapper is another wrapper can be used in a fun-map, which wraps a function,
  it will be called with the fun-map itself as the argument.

  Map m is the underlying storage of a fun-map, fun-map does not change its property
  except accessing values.

  Options:

   - :trace-fn   An effectful function for globally tracing FunctionWrapper calls.
                 Accepts key and value as arguments.
   - :keep-ref   When true, IDeref values (delay, future, atom, etc.) will NOT be
                 automatically dereferenced. Use this when you want to store refs
                 as actual values. Individual values can still use `fnk` or `fw`
                 to opt into lazy evaluation.

  Example:

    (fun-map {:a 35 :b (delay (+ 5 3))})  ; (:b m) => 8
    (fun-map {:a (atom 1)} :keep-ref true) ; (:a m) => #<Atom@... 1>"
  [m & {:keys [trace-fn keep-ref]}]
  (with-meta
    (core/delegate-map m wrapper/wrapper-entry)
    {::trace trace-fn ::wrapper/keep-ref keep-ref}))

(defn fun-map?
  "If m is a fun-map"
  [m]
  (core/fun-map? m))

(comment
  (fun-map {:a 1 :b 5 :c (wrapper/fun-wrapper (fn [m _] (let [a (get m :a) b (get m :b)] (+ a b))))})
  )

(defmacro fw
  "Returns a FunctionWrapper of an anonymous function defined by body.

   Since a FunctionWrapper's function will be called with the map itself as the
   argument, this macro uses a map `arg-map` as its argument. It follows the
   same syntax of Clojure's associative destructuring. You may use `:keys`, `:as`,
   `:or` inside.

   Options (special keys in arg-map):

   - `:wrappers` Controls caching and tracing behavior:
     - `[]`      No caching, no tracing. Function called on every access.
     - (default) `[:trace :cache]` - cached and traceable (see below).

   - `:focus`   A form evaluated to determine if cached value is stale.
                Must be pure and efficient. If the focus value changes,
                the function is re-evaluated. Without `:focus`, the function
                is called only once (memoized).

   - `:trace`   A function `(fn [k v] ...)` called when the wrapped function
                is actually invoked (not on cache hits).

   - `:par?`    When true, dependencies are accessed in parallel using
                manifold's `let-flow`. Requires manifold on classpath.

   Example:

    (fw {:keys  [a b]
         :as    m
         :trace (fn [k v] (println k v))
         :focus (select-keys m [:a :b])}
      (+ a b))

   Works in both Clojure and ClojureScript."
  {:style/indent 1}
  [arg-map & body]
  (helper/make-fw-wrapper `wrapper/fun-wrapper [:trace :cache] arg-map body))

#?(:clj
   (defmethod helper/fw-impl :trace
     [{:keys [f options]}]
     `(wrapper/trace-wrapper ~f ~(:trace options))))

#?(:clj
   (defmethod helper/fw-impl :cache
     [{:keys [f options arg-map]}]
     (let [focus (when-let [focus (:focus options)]
                   `(fn [~arg-map] ~focus))]
       `(wrapper/cache-wrapper ~f ~focus))))

(defmacro fnk
  "A shortcut for `fw` macro. Returns a cached FunctionWrapper that:
   1. Destructures the specified keys from the fun-map
   2. Automatically focuses on those keys (re-evaluates when they change)

   Equivalent to:
     (fnk [a b] body) => (fw {:keys [a b] :focus [a b]} body)

   Note: Namespace qualifiers on keys are used for destructuring but stripped
   for focus comparison. E.g., `(fnk [:ns/a] ...)` destructures `:ns/a` but
   focuses on the local binding `a`.

   Works in both Clojure and ClojureScript."
  {:style/indent 1}
  [args & body]
  (let [focus (mapv (comp symbol name) args)]
    `(fw {:keys  ~args
          :focus ~focus}
       ~@body)))

(comment
  (macroexpand-1 '(fnk [a :ns/b] (+ a b)))
  )

;;;;;; life cycle map

(defn touch
  "Forcefully evaluate all entries of a map and returns itself."
  [m]
  (doseq [[_ _] m] nil)
  m)

(defprotocol Haltable
  "Life cycle protocol, signature just like java.io.Closeable,
  being a protocol gives user ability to extend"
  (halt! [this]))

#?(:clj
   (extend-protocol Haltable
     java.io.Closeable
     (halt! [this]
       (.close this)))
   :cljs
   (extend-protocol Haltable
     core/DelegatedMap
     (halt! [this]
       (when-let [close-fn (some-> this meta ::core/close-fn)]
         (close-fn this)))))

(defn life-cycle-map
  "Returns a fun-map that can be shutdown orderly.

   Any value satisfying the `Haltable` protocol in this map will be considered
   a component. Its `halt!` method will be called in reverse order of creation
   when the map itself is halted via `(halt! the-map)`.

   Note: Only accessed components will be shutdown."
  [m]
  (let [components (atom [])
        trace-fn (fn [_ v]
                   (when (satisfies? Haltable v)
                     (swap! components conj v)))
        sys        (fun-map m :trace-fn trace-fn)
        halt-fn (fn [_]
                  (doseq [component (reverse @components)]
                    (halt! component)))]
    (vary-meta sys assoc ::core/close-fn halt-fn)))

;;;;;;;;;;; Utilities

(deftype CloseableValue [value close-fn]
  #?(:clj clojure.lang.IDeref :cljs IDeref)
  #?(:clj (deref [_] value)
     :cljs (-deref [_] value))
  Haltable
  (halt! [_]
    (close-fn))
  #?@(:clj
      [java.io.Closeable
       (close [this]
         (halt! this))]))

(defn closeable
  "Returns a wrapped plain value which implements IDeref, Haltable, and (in CLJ)
   java.io.Closeable. The close-fn is an effectful function with no arguments.

   When used inside a life-cycle-map, close-fn will be called when
   halting the map via `(halt! the-map)`.

   In Clojure, the returned value works with `with-open`:

     (with-open [conn (closeable (create-conn) #(close-conn conn))]
       (use-conn @conn))"
  [r close-fn]
  (->CloseableValue r close-fn))

#?(:clj
   (defn lookup
     "Returns a ILookup object for calling f on k"
     [f]
     (reify clojure.lang.Associative
       (entryAt [this k]
         (clojure.lang.MapEntry. k (.valAt this k)))
       (valAt [_ k]
         (f k))
       (valAt [this k not-found]
         (or (.valAt this k) not-found)))))
