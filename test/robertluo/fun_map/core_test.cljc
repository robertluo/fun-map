(ns robertluo.fun-map.core-test
  (:require
   [robertluo.fun-map.core :as sut]
   [clojure.test :refer [deftest is testing]]))

(deftest delegate-map
  (testing "methods implementation"
    (is (= (sut/delegate-map {} second) {})))
  (testing "delegate-map with entry simple pass is a normal map"
    (let [m (sut/delegate-map {:a 1 :b 2} (fn [_ [k v]] [k (* 2 v)]))]
      (is (= 4 (get m :b)))
      (is (= 2 (count m)))
      (is (= {:a 2 :b 4} m))
      (is (= m {:a 2 :b 4}))
      (is (= 2 (m :a)))
      (is (= ::not-found (m :c ::not-found))))))

(deftest nil-and-false-value-test
  (testing "nil values are returned correctly with not-found"
    (let [m (sut/delegate-map {:a nil :b 1} (fn [_ [k v]] [k v]))]
      (is (nil? (get m :a)))
      (is (nil? (get m :a :not-found))) ; should return nil, not :not-found
      (is (= :not-found (get m :missing :not-found)))))
  (testing "false values are returned correctly with not-found"
    (let [m (sut/delegate-map {:a false :b true} (fn [_ [k v]] [k v]))]
      (is (false? (get m :a)))
      (is (false? (get m :a :not-found))) ; should return false, not :not-found
      (is (= :not-found (get m :missing :not-found))))))

(deftest transient-test
  (letfn [(wrap-m [m f] (-> m (sut/delegate-map (fn [_ [k v]] [k (* 2 v)])) transient f persistent!))]
    (is (= {} (wrap-m {} identity)))
    (is (= {:a 2} (wrap-m {} #(assoc! % :a 1))))
    (is (= {:a 2} (wrap-m {} #(conj! % [:a 1]))))
    (is (= {:b 4} (wrap-m {} #(-> % (conj! [:a 1]) (conj! [:b 2]) (dissoc! :a)))))))

#?(:cljs
   (deftest map-compatibility
     (testing "If it compatible to map's expected behavior"
       (let [m (sut/delegate-map {:a 1 :b 2 :c 3} (fn [_ [k v]] [k (* 2 v)]))]
         (is (= [:a :b :c] (keys m)))
         (is (= [2 4 6] (vals m)))
         (is (= [[:a 2] [:b 4] [:c 6]] (.entries m)))))))
