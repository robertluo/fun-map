(ns renewdoit.fun-map-test
  (:require [clojure.test :refer :all]
            [renewdoit.fun-map :refer :all]))

(deftest fun-map-test
  (testing "computed attribute of other attributes"
    (is (= 10 (:c (fun-map {:a 3 :b 7 :c (fnk [a b] (+ a b))}))))
    (is (= 1000 (:c (fun-map {:a 10 :b (fnk [a] (* a a)) :c (fnk [b] (* 10 b))})))))
  (testing "equiv test. ! Maybe very expensive"
    (is (= {:a 3 :b 4} (fun-map {:a 3 :b (fnk [a] (inc a))})))))
