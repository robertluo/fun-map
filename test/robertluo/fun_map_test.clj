(ns robertluo.fun-map-test
  (:require [clojure.test :refer :all]
            [robertluo.fun-map :refer :all]))

(deftest fun-map-test
  (testing "computed attribute of other attributes"
    (is (= 10 (:c (fun-map {:a 3 :b 7 :c (fnk [a b] (+ a b))}))))
    (is (= 1000 (:c (fun-map {:a 10 :b (fnk [a] (* a a)) :c (fnk [b] (* 10 b))})))))

  (testing "equiv test. ! Maybe very expensive"
    (is (= {:a 3 :b 4} (fun-map {:a 3 :b (fnk [a] (inc a))})))
    (is (= (fun-map {:a 3 :b (fnk [a] (inc a))}) {:a 3 :b 4})))

  (testing "function will be invoked just once"
    (let [f (let [z (atom 0)]
              (fnk [a] (+ (swap! z inc) a)))
          m (fun-map {:a 3 :b f})]
      (is (= {:a 3 :b 4} m))
      (is (= {:a 3 :b 4} m))))

  (testing "merge fun-map with another map"
    (is (= {:a 3 :b 4} (merge (fun-map {:a 3}) {:b (fnk [a] (inc a))}))))

  (testing "meta data support"
    (is (= {:msg "ok"} (meta (with-meta (fun-map {:a 3}) {:msg "ok"}))))))

(deftest reassoc-test
  (testing "when reassoc value to fun-map, its function can be re-invoked"
    (is (= 11
           (-> (fun-map {:a 2 :b (fnk [a] (inc a))}) (assoc :a 10) :b)))))

(deftest dref-test
  (testing "delay, future, delayed future value will be deref when accessed"
    (is (= {:a 3 :b 4 :c 5}
           (fun-map {:a (delay 3) :b (future 4) :c (delay (future 5))})))))

(deftest trace-map-test
  (testing "invocation record"
    (let [traced (atom [])
          m (fun-map {:a 5 :b (fnk [a] (inc a)) :c (fnk [b] (inc b))}
                     :trace-fn #(swap! traced conj [%1 %2]))
          _ (:c m)]
      (is (= [[:b 6] [:c 7]]
             @traced)))))

(deftest normal-function-value-test
  (testing "when a function hasn't :wrap meta, it will be stored as is"
    (is (= "ok"
           ((-> (fun-map {:a (fn [] "ok")}) :a))))))

(deftest life-cycle-map-test
  (testing "a life cycle map will halt! its components in order"
    (let [close-order (atom [])
          component (fn [k]
                      (reify Haltable
                        (halt! [_] (swap! close-order conj k))))
          sys (life-cycle-map
               {:a (fnk [] (component :a)) :b (fnk [a] (component :b))})]
      (:b sys)
      (.close sys)
      (is (= [:b :a] @close-order)))))

(deftest touch-test
  (testing "touching a fun-map will call all functions inside"
    (let [far (atom 0)
          inc-fn (fnk [] (swap! far inc))
          m (-> (array-map :a inc-fn :b inc-fn)
                fun-map
                touch)]
      (is (= 2 @far))
      (is (= {:a 1 :b 2} m)))))

(deftest merge-trace-test
  (testing "trace-fn should ok for merging"
    (let [marker (atom {})
          trace-f #(swap! marker assoc % %2)
          a (fun-map {:a (fnk [] 0)} :trace-fn trace-f)
          b (fun-map {:b (fnk [a] (inc a))})
          a (touch (merge a b))]
      (is (= {:a 0 :b 1} a))
      (is (= {:a 0 :b 1} @marker)))))

(deftest closeable-test
  (testing "put a closeable value into life cycle map will get closed"
    (let [marker (atom 0)
          m (touch (life-cycle-map
                    {:a (fnk [] (closeable 3 (fn [_] (swap! marker inc))))}))]
      (is (= {:a 3} m))
      (halt! m)
      (is (= 1 @marker)))))
