(ns robertluo.pull-test
  (:require [robertluo.pull :as sut]
            [robertluo.fun-map :as fm]
            [clojure.test :refer [deftest is testing are]]))

(deftest pull-simple-tests
  (are [ptn expected]
      (= expected
         (sut/pull ptn {:a 3 :b {:c "foo" :d :now}}))
    [:a] {:a 3}
    [{:b [:c :d]}] {:b {:c "foo" :d :now}}
    [:d] {} ;;non-existent value should not return
    [:a {:b [:e]}] {:a 3 :b {}}
    ))

(deftest private-value-tests
  (testing "value has meta :private should not be pulled"
    (is (= {} (sut/pull [:a] ^{:private #{:a}} {:a "ok"})))))

(deftest fun-map-test
  (testing "fun-map and lookup can be pulled"
    (let [data (fm/fun-map
                {:numbers (range 10)
                 :sum     (fm/fnk [numbers] (apply + numbers))
                 :avg     (fm/fnk [numbers sum] (/ sum (count numbers)))
                 :dummy   (fm/lookup identity)})]
      (is (= {:avg 9/2 :dummy {3 3}} (sut/pull [:avg {:dummy [3]}] data))))))
