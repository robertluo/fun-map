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
            IPersistentMap
            ITransientMap])
  (:require [robertluo.fun-map.util :as util]
            [robertluo.fun-map.protocols :as proto]))

(extend-type Object
  proto/ValueWrapper
  (-wrapped? [_] false))

(extend-type nil
  proto/ValueWrapper
  (-wrapped? [_] false))

(defn value-wrapper?
  [o]
  (proto/-wrapped? o))

(defn deep-unwrap [o m k]
  (if (value-wrapper? o)
    (recur (proto/-unwrap o m k) m k)
    o))

(deftype WrappedEntry [m ^clojure.lang.IMapEntry entry]
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

(declare ->DelegatedMap)

;;Support transient
(deftype TransientDelegatedMap [^ITransientMap tm]
  ITransientMap
  (conj [_ v] (TransientDelegatedMap. (.conj tm v)))
  (persistent [_] (->DelegatedMap (persistent! tm)))
  ;;ITransientAssociative
  (assoc [_ k v] (TransientDelegatedMap. (.assoc tm k v)))
  ;;ILookup
  (valAt [this k] (.valAt this k nil))
  (valAt [this k not-found]
    (if-let [^clojure.lang.IMapEntry entry (.entryAt this k)]
      (.val entry)
      not-found))

  (without [_ k] (TransientDelegatedMap. (.without tm k)))
  (count [_] (.count tm))

  clojure.lang.ITransientAssociative2
  (containsKey [_ k]
    (.containsKey tm k))
  (entryAt [_ k]
    (wrapped-entry tm (.entryAt tm k))))

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
  (clear [_] (throw (UnsupportedOperationException.)))

  clojure.lang.IEditableCollection
  (asTransient [_]
    (TransientDelegatedMap. (transient m))))

(def delegate-map
  "Return a delegated map"
  ->DelegatedMap)

;;;;;;;;;;;; ValueWrappers

(extend-protocol proto/ValueWrapper
  clojure.lang.IDeref
  (-wrapped? [_] true)
  (-unwrap [d _ _]
    (deref d)))

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


(defmethod print-method IFunMap [^IFunMap o ^java.io.Writer wtr]
  (let [raw-entries (.rawSeq o)]
    (print-method (into {} raw-entries) wtr)))

(prefer-method print-method IFunMap clojure.lang.IPersistentMap)
(prefer-method print-method IFunMap java.util.Map)

(defn lookup
  "Returns a ILookup object for calling f on k"
  [f]
  (reify clojure.lang.Associative
    (entryAt [this k]
      (clojure.lang.MapEntry. k (.valAt this k)))
    (valAt [_ k]
      (f k))
    (valAt [this k not-found]
      (or (.valAt this k) not-found))))
