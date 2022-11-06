(ns robertluo.fun-map.helper
  "Helpers for writing wrappers"
  (:require
   #?(:clj [robertluo.fun-map.util :as util])))

#?(:clj
   (defn let-form
     "returns a pair first to let or equivalent, and the second to transformed bindings."
     [fm bindings]
     #_{:clj-kondo/ignore [:unresolved-symbol]}
     (util/opt-require
      '[manifold.deferred]
      (if (:par? fm)
        [`manifold.deferred/let-flow
         (->> bindings
              (partition 2)
              (mapcat (fn [[k v]] [k `(manifold.deferred/future ~v)]))
              vec)]
        [`let bindings])
      [`let bindings]))
   :cljs
   (defn let-form
     [_ bindings]
     [`let bindings]))

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

(comment
  (destruct-map '{:keys [a b]})
  )

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

(defmethod fw-impl :default
  [{f :f}] f)

;; Global options
(defn make-fw-wrapper
  "construct fw"
  [fun-wrapper default-wrappers arg-map body]
  (let [{:keys [naming normal fm]} (destruct-map arg-map)
        arg-map (merge naming normal)
        m-sym (gensym "fmk")
        [lets binding] (let-form fm (make-binding m-sym naming normal))
        f `(fn [~m-sym ~'_] (~lets ~binding ~@body))]
    (reduce (fn [rst wrapper]
              (fw-impl {:impl wrapper :arg-map arg-map :f rst :options fm}))
            `(~fun-wrapper ~f)
            (or (:wrappers fm) default-wrappers))))

(comment
  (make-fw-wrapper (fn [_]) [] {:keys ['a]} '[(* 2 a)])
  )
