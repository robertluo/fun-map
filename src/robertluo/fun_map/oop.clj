(ns robertluo.fun-map.oop
  "Object Oriented Functional pattern experiment."
  (:require
   [robertluo.fun-map :as fm]))

(defmacro defobject
  "Returns a constructor function takes a map as the argument, return the object.
  Following OO's tradition, suggest camel cased obj-name. mix-ins is a vector
  of other object which will *merge* into this object.

  Example:
    (defobject Foo [Bar]
      :foo/baz
      (fnk [number] (inc number))
      'shout
      (fnk [:foo/baz]
        (fn [prompt]
          (str \"Hello,\" baz))))

  defines an object constructor Foo which mixins constructor Bar with
  attribute :foo/baz and method 'shout."
  {:style/indent [2 :form :form [1]]}
  [obj-name mix-ins & attributes]
  (let [base `(reduce #(%2 %) (fm/fun-map ~'this) ~mix-ins)]
    `(defn ~obj-name
       [~'this]
       ~(if (seq attributes)
          `(-> ~base (assoc ~@attributes))
          base))))

(try
  (require '[clojure.spec.alpha :as s])
  (s/def ::object-name (s/and symbol? #(nil? (namespace %))))
  (s/def ::attribute-name (s/or :keyword keyword? :symbol symbol?))
  (s/def ::attribute-value any?)
  (s/fdef defobject
          :args (s/cat :obj-name ::object-name
                       :mix-ins (s/coll-of symbol?)
                       :attributes (s/* (s/cat :attr-name ::attribute-name
                                               ::attribute-val ::attribute-value))))
  (catch java.io.FileNotFoundException _))

(defmacro .-
  "Calls a method of obj"
  [obj method & args]
  `((~method ~obj) ~@args))

(comment
  (.- d :greet "1" 3))
