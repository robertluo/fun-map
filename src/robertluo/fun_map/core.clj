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
  (unwrap [this m]
    "unwrap the real value from a wrapper"))

(defn wrapped-entry [m ^IMapEntry entry]
  (proxy [clojure.lang.MapEntry] [(.key entry) (.val entry)]
    (val []
      (unwrap (proxy-super val) m))))

;; a marker interface for fun-map
(definterface FunMap)

(defn delegate-map [wrap-fn ^APersistentMap m]
  (proxy [APersistentMap clojure.lang.IObj java.io.Closeable FunMap] []
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
       (or (.valAt this k) not-found)))

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
  Object
  (unwrap [o _]
    o)
  nil
  (unwrap [_ _]
    nil)
  clojure.lang.IDeref
  (unwrap [d _]
    (loop [r d]
      (let [v (deref r)]
        (if (instance? clojure.lang.IDeref v)
          (recur v)
          v)))))

(deftype FunctionWrapper [v prom trace-fn]
  ValueWrapper
  (unwrap [_ m]
    (when-not (realized? prom)
      (let [rv (v m)]
        (deliver prom rv)
        (when trace-fn (trace-fn rv))))
    (deref prom)))

(defn function-wrapper
  "returns a FunctionWrapper wraps value v"
  [trace-fn k v]
  (if (and (fn? v) (some-> v meta :wrap))
    (FunctionWrapper. v (promise) (when trace-fn (partial trace-fn k)))
    v))
