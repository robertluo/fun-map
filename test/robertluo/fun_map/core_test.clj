(ns robertluo.fun-map.core-test
  (:require
   [robertluo.fun-map.core :as sut]
   [clojure.test :refer [deftest is]]))

(deftest destruct-map-test
  (is (= '{:naming {a :a b :b c :c e :d/e f :d/f}
           :normal {:as this :or {a 3}}
           :fm     {:focus [a b]}}
         (sut/destruct-map '{:keys  [a b :d/f] c :c :d/keys [e] :as this
                             :or {a 3}
                             :focus [a b]}))))

(deftest transient-test
  (letfn [(wrap-m [m f] (-> m sut/delegate-map transient f persistent!))]
    (is (= {} (wrap-m {} identity)))
    (is (= {:a 1} (wrap-m {} #(assoc! % :a 1))))
    (is (= {:a 1} (wrap-m {} #(conj! % [:a 1]))))
    (is (= 2 (-> (sut/delegate-map {:a (delay 2)}) transient (.valAt :a))))
    (is (= 2 (-> (sut/delegate-map {:a 1 :b 2}) count)))
    (is (= {:a 1} (wrap-m {:a 1 :b 2} #(dissoc! % :b))))))
