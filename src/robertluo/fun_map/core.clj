(ns ^:no-doc robertluo.fun-map.core
  "implementation of fun-maps.

  a fun-map delegates its storage to underlying map m,
  m stores k,v pair with value wrapped inside a wrapper,
  when requested an entry from a fun-map, instead of
  returning map entry like an ordinary map, it returns
  a special version of entry, evaluate it's value by
  invoking the wrapper."
  (:import [clojure.lang
            IMapEntry
            IPersistentMap])
  (:require [robertluo.fun-map.util :as util]))

(defprotocol ValueWrapper
  (-unwrap [this m k]
    "unwrap the real value from a wrapper on the key of k"))

(defn value-wrapper?
  [o]
  (satisfies? ValueWrapper o))

(defn deep-unwrap [o m k]
  (if (value-wrapper? o)
    (recur (-unwrap o m k) m k)
    o))

(deftype WrappedEntry [m ^clojure.lang.MapEntry entry]
  clojure.lang.Seqable
  (seq [this]
    (seq [(.key this) (.val this)]))
  IMapEntry
  (key [_]
    (.key entry))
  (val [_]
    (deep-unwrap (.val entry) m (.key entry)))

  java.util.Map$Entry
  (getKey [this]
    (.key this))
  (getValue [this]
    (.val this)))

(def wrapped-entry
  "return a wrapped map entry"
  ->WrappedEntry)

(definterface IFunMap
  (rawSeq []))

(deftype DelegatedMap [^IPersistentMap m]
  IFunMap
  (rawSeq [_]
    (.seq m))
  java.io.Closeable
  (close [this]
    (when-let [close-fn (some-> (.meta this) ::close-fn)]
      (close-fn this)))
  clojure.lang.MapEquivalence
  clojure.lang.IHashEq
  (hasheq [_]
    (.hasheq ^clojure.lang.IHashEq m))
  (hashCode [_]
    (.hashCode m))
  (equals [this other]
    (clojure.lang.APersistentMap/mapEquals this other))
  clojure.lang.IObj
  (meta [_]
    (.meta ^clojure.lang.IObj m))
  (withMeta [_ mdata]
    (DelegatedMap. (with-meta m mdata)))
  clojure.lang.ILookup
  (valAt [this k]
    (some-> ^IMapEntry (.entryAt this k) (.val)))
  (valAt [this k not-found]
    (if (.containsKey this k)
      (.valAt this k)
      not-found))
  clojure.lang.IPersistentMap
  (count [_]
    (.count m))
  (empty [_]
    (DelegatedMap. (.empty m)))
  (cons [_ o]
    (DelegatedMap.
     (.cons m (if (instance? IFunMap o) (.rawSeq ^IFunMap o) o))))
  (equiv [this other]
    (.equals this other))
  (containsKey [_ k]
    (.containsKey m k))
  (entryAt [this k]
    (when (.containsKey m k)
      (wrapped-entry this (.entryAt m k))))
  (seq [this]
    (clojure.lang.IteratorSeq/create (.iterator this)))
  (iterator [this]
    (let [ite (.iterator m)]
      (reify java.util.Iterator
        (hasNext [_]
          (.hasNext ite))
        (next [_]
          (wrapped-entry this (.next ite))))))
  (assoc [_ k v]
    (DelegatedMap. (.assoc m k v)))
  (assocEx [_ k v]
    (DelegatedMap. (.assocEx m k v)))
  (without [_ k]
    (DelegatedMap. (.without m k)))
  java.util.Map
  (size [this]
    (.count this))
  (isEmpty [this]
    (zero? (.count this)))
  (containsValue [this v]
    (boolean (some #{v} (vals this))))
  (get [this k]
    (.valAt this k))
  (keySet [this]
    (set (keys this)))
  (values [this]
    (vals this))
  (entrySet [this]
    (set this))
  (put [_ _ _] (throw (UnsupportedOperationException.)))
  (remove [_ _] (throw (UnsupportedOperationException.)))
  (putAll [_ _] (throw (UnsupportedOperationException.)))
  (clear [_] (throw (UnsupportedOperationException.))))

(def delegate-map
  "Return a delegated map"
  ->DelegatedMap)

;;;;;;;;;;;; ValueWrappers

(extend-protocol ValueWrapper
  clojure.lang.IDeref
  (-unwrap [d _ _]
    (deref d)))

(deftype FunctionWrapper [f]
  ValueWrapper
  (-unwrap [_ m k]
    (f m k)))

(defn fun-wrapper
  "returns a new FunctionWrapper with single argument function f"
  [f]
  (->FunctionWrapper (fn [m _] (f m))))

;;;;;;;;;;; Wrapper of wrapper

(deftype CachedWrapper [wrapped a-val-pair focus-fn]
  ValueWrapper
  (-unwrap [_ m k]
    (let [[val focus-val] @a-val-pair
          new-focus-val (if focus-fn (focus-fn m) ::unrealized)]
      (if (or (= ::unrealized val) (not= new-focus-val focus-val))
        (first (swap! a-val-pair (fn [_] [(-unwrap wrapped m k) new-focus-val])))
        val))))

(defn cache-wrapper
  [wrapped focus]
  (CachedWrapper. wrapped (atom [::unrealized ::unrealized]) focus))

(deftype TracedWrapper [wrapped trace-fn]
  ValueWrapper
  (-unwrap [_ m k]
    (let [v (-unwrap wrapped m k)]
      (when-let [trace-fn (or trace-fn (some-> m meta ::trace))]
        (trace-fn k v))
      v)))

(defn trace-wrapper
  [wrapped trace]
  (TracedWrapper. wrapped trace))

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
            `(fun-wrapper ~f)
            (or (:wrappers fm) default-wrappers))))

(defmethod fw-impl :trace
  [{:keys [f options]}]
  `(trace-wrapper ~f ~(:trace options)))

(defmethod fw-impl :cache
  [{:keys [f options arg-map]}]
  (let [focus (when-let [focus (:focus options)]
                `(fn [~arg-map] ~focus))]
    `(cache-wrapper ~f ~focus)))

;;;;;;;;;;; Utilities

(deftype CloseableValue [value close-fn]
  clojure.lang.IDeref
  (deref [_]
    value)
  java.io.Closeable
  (close [_]
    (close-fn)))

(defn fun-map?
  [o]
  (instance? IFunMap o))

(defmethod print-method FunctionWrapper [^FunctionWrapper o ^java.io.Writer wtr]
  (.write wtr (str "<<" (.f o) ">>")))

(defmethod print-method CachedWrapper [^CachedWrapper o ^java.io.Writer wtr]
  (.write wtr
          (str "<<"
               (let [v (-> (.a_val_pair o) deref first)]
                 (if (= ::unrealized v) "unrealized" v))
               ">>")))

(defmethod print-method IFunMap [^IFunMap o ^java.io.Writer wtr]
  (let [raw-entries (.rawSeq o)]
    (print-method (into {} raw-entries) wtr)))

(prefer-method print-method IFunMap clojure.lang.IPersistentMap)
(prefer-method print-method IFunMap java.util.Map)

;;;;;;;;;;;; Spec your FunctionWrapper

(util/opt-require [clojure.spec.alpha :as s]
  (deftype SpecCheckingWrapper [wrapped spec]
    ValueWrapper
    (-unwrap [_ m k]
      (let [v (-unwrap wrapped m k)]
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
