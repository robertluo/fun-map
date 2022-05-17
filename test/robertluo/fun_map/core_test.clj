(ns robertluo.fun-map.core-test
  (:require
   [robertluo.fun-map.core :as sut]
   [clojure.test :refer [deftest is testing]]))

(deftest delegate-map
  (testing "delegate-map with entry simple pass is a normal map"
    (let [m (sut/delegate-map {:a 1 :b 2} (fn [_ [k v]] [k (* 2 v)]))]
      (is (= {:a 2 :b 4} m)))))

(deftest transient-test
  (letfn [(wrap-m [m f] (-> m (sut/delegate-map (fn [_ [k v]] [k (* 2 v)])) transient f persistent!))]
    (is (= {} (wrap-m {} identity)))
    (is (= {:a 2} (wrap-m {} #(assoc! % :a 1))))
    (is (= {:a 2} (wrap-m {} #(conj! % [:a 1]))))
    (is (= {:b 4} (wrap-m {} #(-> % (conj! [:a 1]) (conj! [:b 2]) (dissoc! :a)))))))
