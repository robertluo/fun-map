(ns renewdoit.fun-map.impl.core
  "implementation of fun-maps.

  a fun-map delegates its storage to underlying map m,
  m stores k,v pair with value wrapped inside a wrapper,
  when requested an entry from a fun-map, instead of
  returning map entry like an ordinary map, it returns
  a special version of entry, evaluate it's value by
  invoking the wrapper."
  (:import [clojure.lang
            IPersistentMap
            Associative
            IMapEntry
            Counted
            IPersistentCollection
            APersistentMap
            ArraySeq
            MapEquivalence
            Seqable
            ILookup]))

(defprotocol ValueWrapper
  (unwrap [this m]
    "unwrap the real value from a wrapper"))

(defn wrapped-entry [m ^IMapEntry entry]
  (proxy [clojure.lang.MapEntry] [(.key entry) (.val entry)]
    (val []
      (unwrap (proxy-super val) m))))

(deftype FunMap [wrap-fn m]
  MapEquivalence
  java.util.Map
  (containsKey [this k]
    (.containsKey m k))
  (get [this k]
    (.valAt this k))
  (size [this]
    (.count m))

  IPersistentMap
  (assoc [this k v]
    (FunMap. wrap-fn (.assoc m k (wrap-fn v))))
  (assocEx [this k v]
    (FunMap. wrap-fn (.assocEx m k (wrap-fn v))))
  (without [this k]
    (FunMap. wrap-fn (.without m k)))

  Associative
  (entryAt [this k]
    (wrapped-entry this (.entryAt m k)))

  Counted
  (count [this]
    (.count m))

  Iterable
  (iterator [this]
    (.iterator m))

  IPersistentCollection
  (cons [this o]
    (cond
      (instance? java.util.Map$Entry o)
      (.assoc this (.getKey o) (wrap-fn (.getValue o)))
      (instance? clojure.lang.IPersistentVector o)
      (.assoc this (.nth o 0) (wrap-fn (.nth o 1)))))
  (empty [_]
    (FunMap. wrap-fn (.empty m)))
  (equiv [this o]
    (APersistentMap/mapEquals this o))

  Seqable
  (seq [this]
    (map #(.entryAt this %) (keys m)))

  ILookup
  (valAt [this k]
    (when-let [^IMapEntry entry (.entryAt this k)]
      (.val entry)))
  (valAt [this k not-found]
    (or (.valAt this k) not-found)))

(comment
  (merge (->FunMap function-wrapper {}) {:a 1}))

(defn fun-map*
  "create a fun-map with wrapper-fn to wrap values of underlying m"
  [wrapper-fn m]
  (reduce-kv
   (fn [acc k v] (assoc acc k v))
   (FunMap. wrapper-fn (.empty m))
   m))

;;;;;;;;;;;; Function wrapper

(deftype FunctionWrapper [v prom]
  ValueWrapper
  (unwrap [_ m]
    (if (nil? prom)
      v
      (do
        (when-not (realized? prom)
          (deliver prom (v m)))
        (deref prom)))))

(defn function-wrapper
  "returns a FunctionWrapper wraps value v"
  [v]
  (if (fn? v)
    (FunctionWrapper. v (promise))
    (FunctionWrapper. v nil)))

(comment
  (def fm (fun-map* function-wrapper {:a 3 :b (fn [{:keys [a]}] (+ a 5))})))
