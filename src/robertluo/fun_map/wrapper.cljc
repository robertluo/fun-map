(ns robertluo.fun-map.wrapper
  "Protocols that sharing with other namespaces")

(defprotocol ValueWrapper
  "A wrapper for a value."
  (-wrapped? [this]
    "is this a wrapper?")
  (-unwrap [this m k]
    "unwrap the real value from a wrapper on the key of k"))

;; Make sure common value is not wrapped
#?(:clj
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
       (deref d))))

(deftype FunctionWrapper [f]
  ValueWrapper
  (-wrapped? [_] true)
  (-unwrap [_ m k]
    (f m k)))

(def fun-wrapper
  "construct a new FunctionWrapper"
  ->FunctionWrapper)

(defn wrapper-entry
  "returns a k,v pair from map `m` and input k-v pair.
   If `v` is a wrapped, then recursive unwrap it."
  [m [k v]]
  #?(:clj
     (if (-wrapped? v)
       (recur m [k (-unwrap v m k)])
       [k v])
     :cljs
     (cond
       (satisfies? ValueWrapper v) (recur m [k (-unwrap v m k)])
       (satisfies? IDeref v) (recur m [k (deref v)])
       :else [k v])))

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
  "construct a CachedWrapper"
  [wrapped focus]
  (CachedWrapper. wrapped (atom [::unrealized ::unrealized]) focus))

(deftype TracedWrapper [wrapped trace-fn]
  ValueWrapper
  (-wrapped? [_] true)
  (-unwrap [_ m k]
    (let [v (-unwrap wrapped m k)]
      (when-let [trace-fn (or trace-fn (some-> m meta :robertluo.fun-map/trace))]
        (trace-fn k v))
      v)))

(def trace-wrapper
  "constructs a TraceWrapper"
  ->TracedWrapper)

;; Fine print the wrappers
#?(:clj
   (do
     (defmethod print-method FunctionWrapper [^FunctionWrapper o ^java.io.Writer wtr]
       (.write wtr (str "<<" (.f o) ">>")))

     (defmethod print-method CachedWrapper [^CachedWrapper o ^java.io.Writer wtr]
       (.write wtr
               (str "<<"
                    (let [v (-> (.a_val_pair o) deref first)]
                      (if (= ::unrealized v) "unrealized" v))
                    ">>")))))
