(ns robertluo.fun-map-test
  (:require [clojure.test :refer :all]
            [robertluo.fun-map :refer :all]
            [manifold.deferred :as d]))

(deftest fun-map-test
  (testing "computed attribute of other attributes"
    (is (= 10 (:c (fun-map {:a/a 3 :b 7 :c (fnk [:a/a b] (+ a b))}))))
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
    (is (= {:a 3 :b 4} (merge (fun-map {:a (fnk [] 3)})
                              (fun-map {:b (fnk [a] (inc a))})))))

  (testing "merge fun-map with plain map"
    (is (= {:a 5 :b 6}
           (merge (fun-map {:b (fnk [a] (inc a))})
                  {:a 5}))))

  (testing "dissoc"
    (is (= {:a 3} (dissoc (fun-map {:a 3 :b 4}) :b))))

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
          m      (fun-map {:a 5
                           :b (fnk [a] (inc a))
                           :c (fnk [b] (inc b))}
                          :trace-fn (fn [k v] (swap! traced conj [k v])))]
      (is (= {:a 5 :b 6 :c 7} m))
      (is (= [[:b 6] [:c 7]]
             @traced)))))

(deftest normal-function-value-test
  (testing "when a function hasn't :wrap meta, it will be stored as is"
    (is (= "ok"
           ((-> (fun-map {:a (fn [] "ok")}) :a))))))

(deftest life-cycle-map-test
  (testing "a life cycle map will halt! its components in order"
    (let [close-order (atom [])
          component   (fn [k]
                        (closeable nil
                          (fn [] (swap! close-order conj k))))
          sys         (life-cycle-map
                       {:a (fnk [] (component :a)) :b (fnk [a] (component :b))})]
      (:b sys)
      (.close sys)
      (is (= [:b :a] @close-order)))))

(deftest touch-test
  (testing "touching a fun-map will call all functions inside"
    (let [far (atom 0)
          m   (-> (array-map :a (fnk [] (swap! far inc)) :b (fnk [] (swap! far inc)))
                  fun-map
                  touch)]
      (is (= 2 @far))
      (is (= {:a 1 :b 2} m)))))

(deftest merge-trace-test
  (testing "trace-fn should ok for merging"
    (let [marker  (atom {})
          trace-f #(swap! marker assoc % %2)
          a       (fun-map {:a (fnk [] 0)} :trace-fn (fn [k v] (swap! marker conj [k v])))
          b       (fun-map {:b (fnk [a] (inc a))})
          a       (merge a b)]
      (is (= {:a 0 :b 1} a))
      (is (= {:a 0 :b 1} @marker)))))

(deftest closeable-test
  (testing "put a closeable value into life cycle map will get closed"
    (let [marker (atom 0)
          m      (touch (life-cycle-map
                         {:a (fnk [] (closeable 3 #(swap! marker inc)))}))]
      (is (= {:a 3} m))
      (halt! m)
      (is (= 1 @marker)))))

(deftest fw-test
  (testing "fw macro using normal destructure syntax to define wrapper"
    (is (= {:a 3 :b 5}
           (fun-map {:a (fw {} 3) :b (fw {:keys [a]} (+ a 2))})))))

(deftest fnk-focus-test
  (testing "fnk automatically focus on its dependencies, re-run when dependencies change"
    (let [input (atom 5)
          a (fun-map {:a input :b (fnk [a] (* a 2))})]
      (touch a)
      (reset! input 7)
      (is (= 14 (:b a)))))
  (testing "if no focus function define, the function wrapper will just invoke once"
    (let [input (atom [3 4])
          a (fun-map {:a input :cnt (fw {:keys [a]} (count a))})]
      (is (= 2 (:cnt a)))
      (reset! input (range 10))
      (is (= 2 (:cnt a))))))

(deftest naive-fw-test
  (testing "choose naive function wrapper, no value will be cached"
    (let [a (atom 0)
          m (fun-map {:a (fw {:impl :naive} (swap! a inc))})]
      (is (= 1 (:a m)))
      (is (= 2 (:a m))))))

(deftest spec-wrapper-test
  (let [m (fun-map {:a/a 5
                    :b (fw {:a/keys [a] :spec number?} (inc a))
                    :c (fw {:keys [b] :spec number?} (str b))})]
    (is (= 6 (:b m)))
    (is (thrown? clojure.lang.ExceptionInfo (:c m)))))

(deftest parallel-execution-test
  (let [a (atom 5)
        m (fun-map {:a (delay (Thread/sleep 200) @a)
                    :b (delay (Thread/sleep 200) 20)
                    :z (delay (Thread/sleep 350) (reset! a 10))
                    :c (fw {:keys [z a b] :par? true} (* a b))})]
    (is (= 100 (:c m)))))
