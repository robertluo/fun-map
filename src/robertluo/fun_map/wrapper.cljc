(ns robertluo.fun-map.wrapper
  "Protocols that sharing with other namespaces"
  (:require [clojure.string :as str]))

(defprotocol ValueWrapper
  "A wrapper for a value."
  (-wrapped? [this m]
    "is this a wrapper?")
  (-unwrap [this m k]
    "unwrap the real value from a wrapper on the key of k"))

;; Track keys currently being unwrapped to detect circular dependencies
(def ^:dynamic *unwrap-stack*
  "Stack of keys currently being unwrapped. Used for cycle detection."
  [])

;; Make sure common value is not wrapped
#?(:clj
   (extend-protocol ValueWrapper
     Object
     (-wrapped? [_ _] false)
     (-unwrap [this _ k]
       ;; This should never be called since -wrapped? returns false.
       ;; If it is called, it indicates a bug in the unwrapping logic.
       (throw (ex-info "Bug: attempted to unwrap a non-wrapper value"
                       {:key k :value this :type (type this)})))
     nil
     (-wrapped? [_ _] false)
     (-unwrap [_ _ k]
       (throw (ex-info "Bug: attempted to unwrap nil" {:key k})))
     clojure.lang.IDeref
     (-wrapped? [_ m]
       (not (some-> m meta ::keep-ref)))
     (-unwrap [d _ _]
       (deref d))))

(defn- raw-keys
  "Get raw keys from a map without triggering unwrapping.
   In CLJ, uses rawSeq to avoid triggering unwrapping.
   In CLJS, falls back to regular keys (only used in error messages)."
  [m]
  #?(:clj
     (if (instance? robertluo.fun_map.core.IFunMap m)
       (map first (.rawSeq ^robertluo.fun_map.core.IFunMap m))
       (keys m))
     :cljs
     ;; In CLJS we can't easily reference core without circular dep,
     ;; and this is only used for error context, so just use keys
     (keys m)))

(defn- wrap-fn-error
  "Wraps exceptions from function wrapper with context about key and access path."
  [f m k]
  (try
    (f m k)
    (catch #?(:clj NullPointerException :cljs js/Error) e
      (throw (ex-info (str "Error computing key " (pr-str k)
                           (when (seq *unwrap-stack*)
                             (str " (dependency chain: "
                                  (str/join " -> " (map pr-str *unwrap-stack*))
                                  ")"))
                           ": " #?(:clj (.getMessage e) :cljs (.-message e))
                           ". Possible cause: a dependency key is missing from the map.")
                      {:type :function-wrapper-error
                       :key k
                       :access-path *unwrap-stack*
                       :available-keys (set (raw-keys m))}
                      e)))
    (catch #?(:clj Exception :cljs :default) e
      (throw (ex-info (str "Error computing key " (pr-str k)
                           (when (seq *unwrap-stack*)
                             (str " (dependency chain: "
                                  (str/join " -> " (map pr-str *unwrap-stack*))
                                  ")"))
                           ": " #?(:clj (.getMessage e) :cljs (.-message e)))
                      {:type :function-wrapper-error
                       :key k
                       :access-path *unwrap-stack*}
                      e)))))

(deftype FunctionWrapper [f]
  ValueWrapper
  (-wrapped? [_ _] true)
  (-unwrap [_ m k]
    (wrap-fn-error f m k))
  #?@(:cljs
      [IPrintWithWriter
       (-pr-writer 
        [_ wtr _]
        (-write wtr (str "<<" f ">>")))])
  )

(def fun-wrapper
  "Constructs a new FunctionWrapper."
  ->FunctionWrapper)

(defn- check-cycle!
  "Throws if k is already in the unwrap stack (circular dependency)."
  [k]
  (when (some #{k} *unwrap-stack*)
    (let [cycle-path (conj *unwrap-stack* k)]
      (throw (ex-info (str "Circular dependency detected: "
                           (str/join " -> " (map pr-str cycle-path)))
                      {:type :circular-dependency
                       :key k
                       :cycle cycle-path})))))

(defn- unwrap-with-tracking
  "Unwrap a value with cycle tracking. Returns the unwrapped value."
  [v m k]
  (check-cycle! k)
  (binding [*unwrap-stack* (conj *unwrap-stack* k)]
    (-unwrap v m k)))

(defn wrapper-entry
  "returns a k,v pair from map `m` and input k-v pair.
   If `v` is a wrapped, then recursive unwrap it.
   Detects circular dependencies and provides helpful error messages."
  [m [k v]]
  (loop [v v]
    #?(:clj
       (if (-wrapped? v m)
         (recur (unwrap-with-tracking v m k))
         [k v])
       :cljs
       (cond
         (satisfies? ValueWrapper v)
         (recur (unwrap-with-tracking v m k))
         (satisfies? IDeref v) (recur (deref v))
         :else [k v]))))

;;;;;;;;;;; High order wrappers

(deftype CachedWrapper [wrapped a-val-pair focus-fn]
  ValueWrapper
  (-wrapped? [_ _] true)
  (-unwrap [_ m k]
    ;; Use swap! properly to avoid race condition where multiple threads
    ;; could redundantly recompute the value.
    ;; Note: focus-fn must be pure since it may be called multiple times
    ;; during contention.
    (first
      (swap! a-val-pair
        (fn [[val focus-val]]
          (let [new-focus-val (if focus-fn (focus-fn m) ::unrealized)]
            (if (or (= ::unrealized val) (not= new-focus-val focus-val))
              [(-unwrap wrapped m k) new-focus-val]
              [val focus-val]))))))
  #?@(:cljs
      [IPrintWithWriter
       (-pr-writer
        [this wtr _]
        (-write wtr
                (str "<<"
                     (let [v (-> (.-a_val_pair this) deref first)]
                       (if (= ::unrealized v) "unrealized" v))
                     ">>")))]))

(defn cache-wrapper
  "Constructs a CachedWrapper."
  [wrapped focus]
  (CachedWrapper. wrapped (atom [::unrealized ::unrealized]) focus))

(deftype TracedWrapper [wrapped trace-fn]
  ValueWrapper
  (-wrapped? [_ _] true)
  (-unwrap [_ m k]
    (let [v (-unwrap wrapped m k)]
      (when-let [trace-fn (or trace-fn (some-> m meta :robertluo.fun-map/trace))]
        (trace-fn k v))
      v)))

(def trace-wrapper
  "Constructs a TracedWrapper."
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
                    ">>")))

     (defmethod print-method TracedWrapper [^TracedWrapper o ^java.io.Writer wtr]
       (.write wtr (str "<<traced:" (.wrapped o) ">>")))))
