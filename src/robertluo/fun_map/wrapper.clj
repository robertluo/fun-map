(ns robertluo.fun-map.wrapper
  "Protocols that sharing with other namespaces")

(defprotocol ValueWrapper
  "A wrapper for a value."
  (-wrapped? [this]
    "is this a wrapper?")
  (-unwrap [this m k]
    "unwrap the real value from a wrapper on the key of k"))

(extend-protocol ValueWrapper
  Object
  (-wrapped? [_] false)
  (-unwrap [this _ k]
    (ex-info "Unwrap a common value" {:key k :value this}))
  nil
  (-wrapped? [_] false)
  (-unwrap [_ _ k]
    (ex-info "Unwrap a nil" {:key k}))
  clojure.lang.IDeref
  (-wrapped? [_] true)
  (-unwrap [d _ _]
    (deref d)))

(deftype FunctionWrapper [f]
  ValueWrapper
  (-wrapped? [_] true)
  (-unwrap [_ m k]
    (f m k)))

(def fun-wrapper ->FunctionWrapper)

(defn wrapper-entry
  [m [k v]]
  (if (-wrapped? v)
    (recur m [k (-unwrap v m k)])
    [k v]))

;;;;;;;;;;; High order wrappers

(deftype CachedWrapper [wrapped a-val-pair focus-fn]
  ValueWrapper
  (-wrapped? [_] true)
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
  (-wrapped? [_] true)
  (-unwrap [_ m k]
    (let [v (-unwrap wrapped m k)]
      (when-let [trace-fn (or trace-fn (some-> m meta ::trace))]
        (trace-fn k v))
      v)))

(defn trace-wrapper
  [wrapped trace]
  (TracedWrapper. wrapped trace))
