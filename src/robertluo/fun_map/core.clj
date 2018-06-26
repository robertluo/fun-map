(ns robertluo.fun-map.core
  "implementation of fun-maps.

  a fun-map delegates its storage to underlying map m,
  m stores k,v pair with value wrapped inside a wrapper,
  when requested an entry from a fun-map, instead of
  returning map entry like an ordinary map, it returns
  a special version of entry, evaluate it's value by
  invoking the wrapper."
  (:import [clojure.lang
            IMapEntry
            APersistentMap]))

(defprotocol ValueWrapper
  (raw [this]
    "returns the raw value of the wrapper")
  (unwrap [this m]
    "unwrap the real value from a wrapper"))

(defn deep-unwrap [o m]
  (if (satisfies? ValueWrapper o)
    (recur (unwrap o m) m)
    o))

(defn wrapped-entry [m ^IMapEntry entry]
  (proxy [clojure.lang.MapEntry] [(.key entry) (.val entry)]
    (val []
      (deep-unwrap (proxy-super val) m))))

(definterface IFunMap
  (rawSeq []))

(defn delegate-map [wrap-fn ^APersistentMap m]
  (proxy [APersistentMap clojure.lang.IObj java.io.Closeable IFunMap] []
    (rawSeq []
      (map (fn [[k v]] [k (raw v)]) m))

    (close []
      (when-let [close-fn (some-> (.meta this) ::close-fn)]
        (close-fn this)))

    (meta []
      (.meta m))

    (withMeta [mdata]
      (delegate-map wrap-fn (.withMeta m mdata)))

    (containsKey [k]
      (.containsKey m k))

    (valAt
      ([k]
       (some-> (.entryAt this k) (.val)))
      ([k not-found]
       (if (.containsKey this k)
         (.valAt this k)
         not-found)))

    (entryAt [k]
      (when (.containsKey m k)
        (wrapped-entry this (.entryAt m k))))

    (assoc [k v]
      (delegate-map wrap-fn (.assoc m k (wrap-fn k v))))

    (assocEx [k v]
      (delegate-map wrap-fn (.assocEx m k (wrap-fn k v))))

    (empty []
      (delegate-map wrap-fn (.empty m)))

    (without [k]
      (delegate-map wrap-fn (.dissoc m k)))

    (count []
      (.count m))

    (iterator []
      (let [ite (.iterator m)]
        (reify java.util.Iterator
          (hasNext [_]
            (.hasNext ite))
          (next [_]
            (wrapped-entry this (.next ite))))))

    (cons [o]
      (if (instance? IFunMap o)
        (reduce (fn [acc [k v]] (assoc acc k (wrap-fn k v))) this (.rawSeq o))
        (proxy-super cons o)))

    (seq []
      (clojure.lang.IteratorSeq/create (.iterator this)))))

(defn delegate-map*
  "create a fun-map with wrapper-fn to wrap values of underlying m"
  [wrapper-fn m]
  (reduce-kv
   (fn [acc k v] (assoc acc k v))
   (delegate-map wrapper-fn (.empty m))
   m))

;;;;;;;;;;;; Function wrapper

(extend-protocol ValueWrapper
  clojure.lang.IDeref
  (raw [d]
    d)
  (unwrap [d _]
    (deref d)))

(deftype FunctionWrapper [f prom trace-fn]
  ValueWrapper
  (raw [_]
    f)
  (unwrap [_ m]
    (when-not (realized? prom)
      (let [rv (f m)]
        (deliver prom rv)
        (when trace-fn (trace-fn rv))))
    prom))

(deftype CloseableValue [value close-fn]
  clojure.lang.IDeref
  (deref [_]
    value)
  java.io.Closeable
  (close [_]
    (close-fn value)))

(defn function-wrapper
  "returns a FunctionWrapper wraps value v"
  [trace-fn k v]
  (if (and (fn? v) (some-> v meta :wrap))
    (FunctionWrapper. v (promise) (when trace-fn (partial trace-fn k)))
    v))
