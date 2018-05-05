(ns renewdoit.fun-map-test
  (:require [clojure.test :refer :all]
            [renewdoit.fun-map :refer :all]))

(deftest fun-map-test
  (testing "computed attribute of other attributes"
    (is (= 10 (:c (fun-map {:a 3 :b 7 :c (fnk [a b] (+ a b))}))))
    (is (= 1000 (:c (fun-map {:a 10 :b (fnk [a] (* a a)) :c (fnk [b] (* 10 b))})))))

  (testing "equiv test. ! Maybe very expensive"
    (is (= {:a 3 :b 4} (fun-map {:a 3 :b (fnk [a] (inc a))})))
    (is (= (fun-map {:a 3 :b (fnk [a] (inc a))}) {:a 3 :b 4})))

  (testing "function will be invoked just once"
    (let [f (let [z (atom 0)]
              (fnk [a] (+ (swap! z inc) a)))
          m (fun-map {:a 3 :b f})]
      (is (= {:a 3 :b 4} m))
      (is (= {:a 3 :b 4} m))))

  #_(testing "merge fun-map with another map"
      (is (= {:a 3 :b 4} (merge (fun-map {:a 3}) {:b (fnk [a] (inc a))})))))
