(ns ^:no-doc robertluo.pull.impl)

(defprotocol Pullable
  (-pull [this ptn]
    "return data structure pulling from this"))

(defn join?
  "predict if ptn is a join grouup"
  [ptn]
  (map? ptn))

(defn find-join
  [m to-find]
  (->> to-find
       (map
        (fn [[k p]]
          (when-let [v (get m k)]
            [k (-pull v p)])))
       (into {})))

(comment
  (find-join {:a {:b 5}} {:a [:b :c]})
  )

(extend-protocol Pullable

  clojure.lang.Sequential
  (-pull
    [this ptn]
    (map #(-pull % ptn) this))

  clojure.lang.ILookup
  (-pull
    [this ptn]
    (let [privates (some-> this meta :private set)]
      (->> ptn
           (map
            (fn [to-find]
              (if (join? to-find)
                (find-join this to-find)
                (when-not (and privates (privates to-find))
                  (when-let [v (.valAt this to-find)]
                    [to-find v])))))
           (into {})))))

(comment
  (-pull {:a 3 :b 5} [:a])
  (-pull [{:a 3 :b 5} {:a 5} {:b 6}] [:a])
  (-pull {:a {:b 5 :c 6} :d 5} [{:a [:b]} :d])
  )
