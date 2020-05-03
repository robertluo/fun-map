(ns robertluo.pull.impl-test
  (:require
   [clojure.test :refer [deftest is are]]
   [robertluo.pull.impl :as sut]))

(deftest construct
  (is (= {:a {:b "foo" :c "bar"} :d 5}
       (sut/construct (map identity) [[[:a :b] "foo"] [[:a :c] "bar"] [[:d] 5]]))))

(deftest pattern->paths
  (are [pattern exp] (= exp (sut/pattern->paths [] pattern))
    [:a :b] [[:a] [:b]]
    [{:a [:b :c]}] [[:a :b] [:a :c]]
    [:a {:b [:c {:d [:e :f]}]} :g] [[:a] [:b :c] [:b :d :e] [:b :d :f] [:g]]))

(deftest pull
  (let [data {:a {:a1 {:a11 "foo"
                       :a12 :bar}
                  :a2 {:a21 [3 5]}}
              :b 8}]
    (are [pattern exp] (= exp (sut/pull2 data pattern))
      [{:a [{:a1 [:a12]}]} :b] {:a {:a1 {:a12 :bar}} :b 8})))