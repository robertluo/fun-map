(ns ^:no-doc robertluo.pull.impl
  "Implementation of pull")

(defprotocol Findable
  :extend-via-metadata true
  (-find [this k]
    "returns a vector of k, v.
     Introduce a new protocol rather than using existing interface
     ILookup in order to let us to extend this in the future"))

(extend-protocol Findable
  clojure.lang.ILookup
  (-find [this k]
    (when-let [v (.valAt this k)]
      [k v])))

(comment
 (-find {:a 1} :a))

(defn join?
  "predict if ptn is a join group"
  [ptn]
  (and (map? ptn) (= 1 (count ptn))))

(defn apply-seq
  "apply f to x when x is sequential and (pred x) is true, or simply
   apply f to x"
  [pred f x]
  (if (and (sequential? x) pred)
    (mapv f x)
    (f x)))

(def findable?
  (partial satisfies? Findable))

(def find-apply
  (partial apply-seq (partial every? findable?)))

(defn findable-seq?
  [v]
  (and (sequential? v) (every? findable? v)))

(defn pull*
  [data ptn]
  (reduce
   (fn [acc k]
     (if (join? k)
       (let [[local-k sub-ptn] (first k)
             [k sub-data] (-find data local-k)]
         (when k
           (conj acc [k (find-apply #(pull* % sub-ptn) sub-data)])))
       (if-let [[k v] (-find data k)]
         ;;for pullable sequence or value, a join is required
         (conj acc [k (if (or (findable? v) (findable-seq? v))
                        :robertluo.pull/join-required
                        v)])
         acc)))
   {}
   ptn))

(defn pull
  [data ptn]
  (find-apply #(pull* % ptn) data))

(defn private-attrs
  [pred m]
  (with-meta m
    {`-find
     (fn [this k]
       (when-let [v (get this k)]
         (when (not (pred k))
           [k v])))}))

(comment
  (pull {:a [{:aa 3} {:aa 5} {:ab 6}]
         :b {:bb :foo}
         :c 5}
        [{:a [:aa]} {:b [:bb]} :c])
  (pull [{:a 3} {:a 4} {:b 5}] [:a])
  )
