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
  attribute :foo/baz and method shout."
  {:style/indent [2 :form :form [1]]}
  [obj-name mix-ins & attributes]
  (let [f-trans    (fn [[aname avalue]]
                     [(if (symbol? aname) `'~aname aname) avalue])
        attributes (mapcat f-trans (partition 2 attributes))
        base       `(reduce #(%2 %) (fm/fun-map ~'this) ~mix-ins)]
    `(defn ~obj-name
       [~'this]
       (reduce #(%2 %)
               (merge (fm/fun-map (array-map ~@attributes)) ~'this)
               ~mix-ins))))

(defmacro .-
  "Calls a method of obj"
  [obj method & args]
  `((~(if (symbol? method) `'~method method) ~obj) ~@args))

;;Optional with spec support for working with clojure < 1.9
(fm/opt-require '[clojure.spec.alpha :as s]
  (s/def ::object-name (s/and symbol? #(nil? (namespace %))))
  (s/def ::attribute-name (s/or :keyword keyword? :symbol symbol?))
  (s/def ::attribute-value any?)
  (s/fdef defobject
    :args (s/cat :obj-name ::object-name
                 :mix-ins (s/coll-of symbol?)
                 :attributes (s/* (s/cat :attr-name ::attribute-name
                                         ::attribute-val ::attribute-value))))

  (s/fdef .-
    :args (s/cat :obj any?
                 :method ::attribute-name
                 :args (s/* any?))))

(comment
  (defobject Foo []
    :foo/inc
    (fm/fnk [number] (inc number))
    :greet
    (fm/fnk [:foo/inc]
            (fn [name]
              (str name inc)))
    shout
    (fm/fnk [:foo/inc]
            (fn [name] (str "Hello," inc " from " name))))
  (def a (Foo {:number 4}))
  (:number a)
  (.- (Foo {:number 4}) shout "world")
  (.- (Foo {:number 3}) :greet "world"))
