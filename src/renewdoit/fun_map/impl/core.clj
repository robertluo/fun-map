(ns renewdoit.fun-map.impl.core
  (:import [clojure.lang
            IPersistentMap
            Associative
            IMapEntry
            Counted
            IPersistentCollection
            Seqable
            ILookup]))

(deftype FunMapEntry
  [val-fn ^IMapEntry entry]
  IMapEntry
  (key [this]
    (.key entry))
  (val [this]
    (val-fn (.val entry)))

  java.util.Map$Entry
  (getKey [this]
    (.key this))
  (getValue [this]
    (.val this)))

(deftype FunMap [val-fn ^IPersistentMap m]
  IPersistentMap
  (assoc [this k v]
    (FunMap. val-fn (.assoc m k v)))
  (assocEx [this k v]
    (FunMap. val-fn (.assocEx m k v)))
  (without [this k]
    (FunMap. val-fn (.without m k)))

  Associative
  (entryAt [this k]
    (FunMapEntry. (val-fn this) (.entryAt m k)))

  Counted
  (count [this]
    (.count m))

  Iterable
  (iterator [this]
    (.iterator m))

  IPersistentCollection
  (cons [_ o]
    (FunMap. val-fn (.cons m o)))
  (empty [_]
    (FunMap. val-fn (.empty m)))
  (equiv [this o]
    false)

  Seqable
  (seq [this]
    (map #(.entryAt this %) (keys m)))

  ILookup
  (valAt [this k]
    (when-let [^IMapEntry entry (.entryAt this k)]
      (.val entry)))
  (valAt [this k not-found]
    (or (.valAt this k) not-found))
  )

(defn function-val-fn
  [fm]
  (fn [val]
    (if (fn? val) (val fm) val)))

(def fun-map
  (partial ->FunMap function-val-fn))

(defmacro fnk
  [args & body]
  `(fn [{:keys ~args}]
     ~@body))

(comment
  (def m (fun-map {:a 4
                   :b "ok"
                   :c (constantly 10)
                   :d (fnk [c] (prn :d) (* c c))}))
  (:d m)
  (def m (assoc m :e (fnk [d] (str d))))
  (def m (assoc m :f/a -35 :f/b (fnk [:f/a] (* 2 a))))
)
