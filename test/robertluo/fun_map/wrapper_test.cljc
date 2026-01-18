(ns robertluo.fun-map.wrapper-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [robertluo.fun-map :refer [fun-map fnk]]
   [robertluo.fun-map.wrapper :as sut]))

(deftest wrapper-entry
  (testing "a wrapped entry will apply its value to the map"
    (is (= [:b 1] (sut/wrapper-entry {:a 1} [:b (sut/fun-wrapper (fn [m _] (get m :a)))]))))
  (testing "wrapped entry will unwrap deeply until geeting non-wrapped value"
    (is (= [:b 6] (sut/wrapper-entry {:a 3} [:b (atom (atom 6))])))))

(deftest circular-dependency-detection
  (testing "self-referencing key throws with cycle info"
    (let [m (fun-map {:a (fnk [a] a)})]
      (is (thrown-with-msg? #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo)
                            #"Circular dependency detected: :a -> :a"
                            (:a m)))))
  (testing "two-key cycle throws with cycle path"
    (let [m (fun-map {:a (fnk [b] b) :b (fnk [a] a)})]
      (is (thrown-with-msg? #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo)
                            #"Circular dependency detected: :a -> :b -> :a"
                            (:a m)))))
  (testing "three-key cycle throws with full path"
    (let [m (fun-map {:a (fnk [b] b) :b (fnk [c] c) :c (fnk [a] a)})]
      (is (thrown-with-msg? #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo)
                            #"Circular dependency detected: :a -> :b -> :c -> :a"
                            (:a m)))))
  (testing "ex-data contains cycle information"
    (let [m (fun-map {:a (fnk [b] b) :b (fnk [a] a)})
          ex (try (:a m) nil (catch #?(:clj Exception :cljs :default) e e))]
      (is (= :circular-dependency (:type (ex-data ex))))
      (is (= :a (:key (ex-data ex))))
      (is (= [:a :b :a] (:cycle (ex-data ex)))))))

;; Note: These tests are CLJ-only because in CLJS, (inc nil) returns NaN
;; instead of throwing an exception due to JavaScript's type coercion.
#?(:clj
   (deftest error-context-on-failure
     (testing "NPE in fnk body provides context about key"
       (let [m (fun-map {:a (fnk [missing] (inc missing))})
             ex (try (:a m) nil (catch Exception e e))]
         (is (= :function-wrapper-error (:type (ex-data ex))))
         (is (= :a (:key (ex-data ex))))
         (is (= #{:a} (:available-keys (ex-data ex))))))
     (testing "error message mentions the key being computed"
       (let [m (fun-map {:result (fnk [x] (inc x))})]
         (is (thrown-with-msg? clojure.lang.ExceptionInfo
                               #"Error computing key :result"
                               (:result m)))))))
