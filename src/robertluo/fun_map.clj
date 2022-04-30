(ns robertluo.fun-map
  "fun-map Api"
  (:require
   [robertluo.fun-map [core :as impl]
    [util :as util]
    [protocols :as proto]
    [wrapper :as wrapper]]
 [clojure.spec.alpha :as s]))

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

(defn lookup
  "Returns a semi Associative instance by mapping single arity function f as
  argument to return value."
  [f]
  (impl/lookup f))

;;;;;;;;;; fw macro implementation

(defn destruct-map
  "destruct arg-map of fw macro into different groups"
  [arg-map]
  (reduce
   (fn [rst [k v]]
     (cond
       (= k :keys)
       (update rst :naming into (map (fn [s] [(-> s name symbol) (keyword s)]) v))

       (symbol? k)
       (update rst :naming assoc k v)

       (#{:as :or} k)
       (update rst :normal assoc k v)

       (and (keyword? k) (= "keys" (name k)))
       (let [ns (namespace k)]
         (update rst :naming into (map (fn [s] [s (keyword ns (name s))]) v)))

       :else
       (update rst :fm assoc k v)))
   {:naming {} :normal {} :fm {}}
   arg-map))

(defn make-binding
  "prepare binding for let"
  [m-sym naming {:keys [or as]}]
  (cond-> (mapcat
           (fn [[sym k]]
             (let [g `(get ~m-sym ~k)
                   kf (if-let [v (get or sym)] `(or ~g ~v) g)]
               [sym kf]))
           naming)
    as (concat [as m-sym])
    true vec))

(defmulti fw-impl
  "returns a form for fw macro implementation"
  :impl)

(defn let-form
  "returns a pair first to let or equivalent, and the second to transformed bindings."
  [_ bindings]
  [`let bindings])

(def default-wrappers
  "Default wrappers for fw macro"
  [:trace :cache])

(defn make-fw-wrapper
  "construct fw"
  [arg-map body]
  (let [{:keys [naming normal fm]} (destruct-map arg-map)
        arg-map (merge naming normal)
        m-sym (gensym "fmk")
        [lets binding] (let-form fm (make-binding m-sym naming normal))
        f `(fn [~m-sym] (~lets ~binding ~@body))]
    (reduce (fn [rst wrapper]
              (fw-impl {:impl wrapper :arg-map arg-map :f rst :options fm}))
            `(wrapper/fun-wrapper ~f)
            (or (:wrappers fm) default-wrappers))))

(defmethod fw-impl :trace
  [{:keys [f options]}]
  `(wrapper/trace-wrapper ~f ~(:trace options)))

(defmethod fw-impl :cache
  [{:keys [f options arg-map]}]
  (let [focus (when-let [focus (:focus options)]
                `(fn [~arg-map] ~focus))]
    `(wrapper/cache-wrapper ~f ~focus)))

;;;;;;;;;;;; Spec your FunctionWrapper

(util/opt-require [clojure.spec.alpha :as s]
                  (deftype SpecCheckingWrapper [wrapped spec]
                    proto/ValueWrapper
                    (-wrapped? [_] true)
                    (-unwrap [_ m k]
                      (let [v (proto/-unwrap wrapped m k)]
                        (if (s/valid? spec v)
                          v
                          (throw (ex-info "Value unwrapped does not conform spec"
                                          {:key k :value v :explain (s/explain-data spec v)}))))))

                  (defn spec-wrapper
                    [wrapped spec]
                    (SpecCheckingWrapper. wrapped spec))

                  (def default-wrappers
                    "Redefine default wrappers to support spec"
                    [:spec :trace :cache])

                  (defmethod fw-impl :spec
                    [{:keys [f options]}]
                    (let [spec (:spec options)]
                      (if spec `(spec-wrapper ~f ~spec) f))))

;;;;;;;;;;;;;;;; Parallel support

(util/opt-require [manifold.deferred]
                  (defn let-form
                    "redefine let-form check if :par? is truthy, then use manifold's let-flow
     to replace let, and create future for value."
                    [fm bindings]
                    (if (:par? fm)
                      [`manifold.deferred/let-flow
                       (->> bindings
                            (partition 2)
                            (mapcat (fn [[k v]] [k `(manifold.deferred/future ~v)]))
                            vec)]
                      [`let bindings])))

(defmacro fw
  "Returns a FunctionWrapper of an anonymous function defined by body.

   Since a FunctionWrapper's function will be called with the map itself as the
   argument, this macro using a map `arg-map` as its argument. It follows the
   same syntax of clojure's associative destructure. You may use `:keys`, `:as`,
   `:or` inside.

   Special key `:wrappers` specify additional wrappers of function wrapper:

    - `[]` for naive one, no cache, no trace.
    - default to specable cached traceable implementation. which supports special keys:
      - `:spec` a spec that the value must conform.
      - `:focus` A form that will be called to check if the function itself need
        to be called. It must be pure functional and very effecient.
      - `:trace` A trace function, if the value updated, it will be called with key
        and the function's return value.

   Special option `:par? true` will make dependencies accessing parallel.

   Example:

    (fw {:keys  [a b]
         :as    m
         :trace (fn [k v] (println k v))
         :focus (select-keys m [:a :b])}
      (+ a b))"
  {:style/indent 1}
  [arg-map & body]
  (make-fw-wrapper arg-map body))

(comment
  (fw {:keys [a] :focus a} (inc a)))

(defmacro fnk
  "A shortcut for `fw` macro. Returns a simple FunctionWrapper which depends on
  `args` key of the fun-map, it will *focus* on the keys also."
  {:style/indent 1}
  [args & body]
  `(fw {:keys  ~args
        :focus ~args}
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

(defn fun-map?
  "If m is a fun-map"
  [m]
  (impl/fun-map? m))

(util/opt-require [clojure.spec.alpha :as s]
  (s/def ::trace-fn fn?)
  (s/fdef fun-map
    :args (s/cat :map map? :trace (s/keys* :opt-un [::trace-fn]))
    :ret fun-map?)

  (s/fdef fw
    :args (s/cat :arg-map map? :body (s/* any?)))

  (s/fdef fnk
    :args (s/cat :args vector? :body (s/* any?))))
