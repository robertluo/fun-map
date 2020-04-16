(ns robertluo.pull
  "Simple pull API inspired by datomic Pull API, but can be used on any ILookup
   instance" 
  (:require [robertluo.pull.impl :as impl]))

(def pull
  "Returns data specified by pattern inside data.

   data can be a ILookup instance (for example, a map, or a lookup of fun-map), or
   a sequential of ILookup.

   A pattern is a vector, contains keys or joins, with keys in patterns, pull
   acts just like clojure.core/select-keys:
     `(pull {:a 3 :b 5 :c 6} [:a :c]) => {:a 3 :c 6}`
   A join in the pattern is a map with its keys corresponding keys in data, while
   value is another pattern. So this nested pattern can travel nested data:

     ```
     (pull {:a 3 :b 5 :c [{:ca 4, :cb :foo} {:ca -1, :cb :bar}]} [:a {:c [:ca]}])
     => {:a 3 :c [{:ca 4} {:ca -1}]}
     ```

  A map (ILookup) has to specific a join to pull, otherwise its value will be returned
  as :robertluo.pull/join-required.
  "
  impl/pull)

(def private-attrs
  "If some private key-values in your data you do not want pull to return, for
   instance, password or other sensitive data, you can use this function to
   hide some attributes:
     `(private-attrs #{:hidden} {:a :hidden})`

  Uses clojure 1.10 's meta extending protocol, can not work on prior version of
  clojure"
  impl/private-attrs)
