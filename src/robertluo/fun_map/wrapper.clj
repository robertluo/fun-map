(ns robertluo.fun-map.wrapper
  (:require [robertluo.fun-map.protocols :as proto]))

(deftype FunctionWrapper [f]
  proto/ValueWrapper
  (-wrapped? [_] true)
  (-unwrap [_ m k]
    (f m k)))

(defn fun-wrapper
  "returns a new FunctionWrapper with single argument function f"
  [f]
  (->FunctionWrapper (fn [m _] (f m))))

;;;;;;;;;;; Wrapper of wrapper

(deftype CachedWrapper [wrapped a-val-pair focus-fn]
  proto/ValueWrapper
  (-wrapped? [_] true)
  (-unwrap [_ m k]
    (let [[val focus-val] @a-val-pair
          new-focus-val (if focus-fn (focus-fn m) ::unrealized)]
      (if (or (= ::unrealized val) (not= new-focus-val focus-val))
        (first (swap! a-val-pair (fn [_] [(proto/-unwrap wrapped m k) new-focus-val])))
        val))))

(defn cache-wrapper
  [wrapped focus]
  (CachedWrapper. wrapped (atom [::unrealized ::unrealized]) focus))

(deftype TracedWrapper [wrapped trace-fn]
  proto/ValueWrapper
  (-wrapped? [_] true)
  (-unwrap [_ m k]
    (let [v (proto/-unwrap wrapped m k)]
      (when-let [trace-fn (or trace-fn (some-> m meta ::trace))]
        (trace-fn k v))
      v)))

(defn trace-wrapper
  [wrapped trace]
  (TracedWrapper. wrapped trace))