(ns robertluo.fun-map.oop
  "Object Oriented Functional pattern experiment."
  (:require
   [robertluo.fun-map :as fm]
   [clojure.spec.alpha :as s]))

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

(defmacro +>
  "Calls a method of obj"
  [obj method & args]
  (let [method (if (symbol? method) `'~method method)]
    `(if-let [~'mth (~obj ~method)]
       (~'mth ~@args)
       (throw (IllegalArgumentException. (str ~method " not exist"))))))

;;Optional with spec support for working with clojure < 1.9
(fm/opt-require [clojure.spec.alpha :as s]

  (defmacro object-spec
    "Define spec for object"
    {:style/indent 2}
    [obj-spec mixins prop-specs]
    (let [prop-forms (for [[prop spec] prop-specs]
                       `(s/def ~prop ~spec))]
      `(do
         ~@prop-forms
         (s/def ~obj-spec (s/merge ~@mixins (s/keys :req ~(vec (keys prop-specs))))))))

  (s/def ::object-name (s/and symbol? #(nil? (namespace %))))
  (s/def ::attribute-name (s/or :keyword keyword? :symbol symbol?))
  (s/def ::attribute-value any?)

  (s/fdef defobject
    :args (s/cat :obj-name ::object-name
                 :mix-ins (s/coll-of symbol?)
                 :attributes (s/* (s/cat :attr-name ::attribute-name
                                         ::attribute-val ::attribute-value))))

  (s/fdef +>
    :args (s/cat :obj any?
                 :method ::attribute-name
                 :args (s/* any?)))
  (s/fdef object-spec
    :args (s/cat :obj-spec qualified-keyword?
                 :mix-ins  (s/coll-of qualified-keyword?)
                 :props (s/map-of qualified-keyword? any?))))

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
  (+> (Foo {:number 4}) shout "world")
  (object-spec ::Foo
               {:foo/inc number?}))
