(ns fun-map.core-test
  (:require [clojure.test :refer :all]
            [fun-map.core :refer :all]))

(deftest fun-map-test
  (let [m (fun-map {:a 3
                    :b (fnk [a] (* a a))})]
    (testing "a function in the values will be called when accessed"
      (= 9 (:b m)))))
