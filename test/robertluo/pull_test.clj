(ns robertluo.pull-test
  (:require [robertluo.pull :as sut]
            [robertluo.fun-map :as fm]
            [clojure.test :refer [deftest is testing are]]))

(deftest pull-simple-tests
  (testing "regular pull pattern"
    (are [ptn expected]
        (= expected
           (sut/pull {:a 3 :b {:c "foo" :d :now}} ptn))
        [:a] {:a 3}
        [{:b [:c :d]}] {:b {:c "foo" :d :now}}
        [:a {:b [:e]}] {:a 3 :b {}}))
  (testing "when pull on a non-existent value, it will not included in result"
    (is (= {:b 5} (sut/pull {:b 5} [:a :b]))))
  (testing "pulling a map without specifying join will return an empty map"
    (is (= {:a {}} (sut/pull {:a {:b "foo"}} [:a])))))

(deftest fun-map-test
  (testing "fun-map and lookup can be pulled"
    (let [data (fm/fun-map
                {:numbers (range 10)
                 :sum     (fm/fnk [numbers] (apply + numbers))
                 :avg     (fm/fnk [numbers sum] (/ sum (count numbers)))
                 :dummy   (fm/lookup identity)})]
      (is (= {:avg 9/2 :dummy {3 3}} (sut/pull data [:avg {:dummy [3]}]))))))
