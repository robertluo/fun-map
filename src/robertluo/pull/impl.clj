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
    [k (.valAt this k)]))

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

(defn all-findable?
  [x]
  (every? #(satisfies? Findable %) x))

(defn pull*
  [data ptn]
  (reduce
   (fn [acc k]
     (if (join? k)
       (let [[local-k sub-ptn] (first k)
             [k sub-data] (-find data local-k)]
         (when k
           (conj acc [k (apply-seq all-findable? #(pull* % sub-ptn) sub-data)])))
       (conj acc (find data k))))
   {}
   ptn))

(defn pull
  [data ptn]
  (apply-seq all-findable? #(pull* % ptn) data))

(comment
  (pull {:a [{:aa 3} {:aa 5} {:ab 6}]
         :b {:bb :foo}
         :c 5}
        [{:a [:aa]} {:b [:bb]} :c])
  (pull [{:a 3} {:a 4} {:b 5}] [:a])
  )
