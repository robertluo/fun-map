(ns robertluo.fun-map.wrapper-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [robertluo.fun-map.wrapper :as sut]))

(deftest wrapper-entry
  (testing "a wrapped entry will apply its value to the map"
    (is (= [:b 1] (sut/wrapper-entry {:a 1} [:b (sut/fun-wrapper (fn [m _] (get m :a)))]))))
  (testing "wrapped entry will unwrap deeply until geeting non-wrapped value"
    (is (= [:b 6] (sut/wrapper-entry {:a 3} [:b (atom (atom 6))])))))
