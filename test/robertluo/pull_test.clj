(ns robertluo.pull-test
  (:require [robertluo.pull :as sut]
            [robertluo.fun-map :as fm]
            [clojure.test :refer [deftest is testing are]]
            [clojure.spec.test.alpha :as stest]))

(deftest pull-simple-tests
  (testing "regular pull pattern"
    (are [ptn expected]
         (= expected
            (sut/pull {:a 3 :b {:c "foo" :d :now}} ptn))
      [:a] {:a 3}
      [{:b [:c :d]}] {:b {:c "foo" :d :now}}
      [:a {:b [:e]}] {:a 3 :b {}}))
  (testing "pulling a sequence will return pulling result of its element"
    (is (= {:a [{:b 3} {:b 5}]}
           (sut/pull {:a [{:b 3} {:b 5}]} [{:a [:b]}]))))
  (testing "Can pull on a sequence"
    (is (= [{:a 5 :c 3} {:a 8}]
           (sut/pull [{:a 5 :b 6 :c 3} {:a 8}] [:a :c]))))
  (testing "when pull on a non-existent value, it will not included in result"
    (is (= {:b 5} (sut/pull {:b 5} [:a :b]))))
  (testing "pulling a map without specifying join will return a specific keyword
            indicating join is required"
    (is (= {:a ::sut/join-required} (sut/pull {:a {:b "foo"}} [:a])))
    (is (= {:a ::sut/join-required} (sut/pull {:a '({:b "foo"} {:b "bar"})} [:a])))))

(deftest fun-map-test
  (testing "fun-map and lookup can be pulled"
    (let [data (fm/fun-map
                {:numbers (range 10)
                 :sum     (fm/fnk [numbers] (apply + numbers))
                 :avg     (fm/fnk [numbers sum] (/ sum (count numbers)))
                 :dummy   (fm/lookup identity)})]
      (is (= {:avg 9/2 :dummy {3 3}} (sut/pull data [:avg {:dummy [3]}]))))))

(deftest pull-customize-find-test
  (let [v (sut/private-attrs #{:hidden} {:a 5 :hidden 6})]
    (is (= {:a 5} (sut/pull v [:a :hidden])))))

(deftest spec
  (testing "the spec of pull is correct."
    (stest/instrument)
    (is (= {:foo {5 6}} (sut/pull (fm/fun-map {:a 3 :foo (fm/lookup inc)}) [{:foo [5]}])))
    (is (thrown? clojure.lang.ExceptionInfo (sut/pull {:a 3} 2))) ;;basic pattern error
    (stest/unstrument)))
