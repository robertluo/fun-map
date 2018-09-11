(ns robertluo.fun-map.oop
  (:require
   [robertluo.fun-map :as fm]))

(defmacro defobject
  [obj-name mix-ins & methods]
  (let [base `(reduce #(%2 %) (fm/fun-map ~'this) ~mix-ins)]
    `(defn ~obj-name
       [~'this]
       ~(if (seq methods)
          `(-> ~base (assoc ~@methods))
          base))))

(comment
  (defobject Foo [Bar]
    :baz
    (fnk [a] a))
  (defobject Foo [Bar]))

(defmacro .-
  [obj method & args]
  `((~method ~obj) ~@args))

(comment
  (.- d :greet "1" 3))
