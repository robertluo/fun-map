(ns robertluo.fun-map.core-test
  (:require
   [robertluo.fun-map.core :as sut]
   [clojure.test :refer :all]))

(deftest destruct-map-test
  (is (= '{:naming {a :a b :b c :c e :d/e}
           :normal {:as this :or {a 3}}
           :fm     {:focus [a b]}}
         (sut/destruct-map '{:keys  [a b] c :c :d/keys [e] :as this
                             :or {a 3}
                             :focus [a b]}))))
