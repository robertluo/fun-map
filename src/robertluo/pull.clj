(ns robertluo.pull
  "Simple pull API inspired by datomic Pull API, but can be used on any ILookup
   instance" 
  (:require [robertluo.pull.impl :as impl]))

(defn pull
  "Returns data specified by pattern inside data.
   m can be a ILookup instance (for example, a map, or a lookup of fun-map), or 
   a sequential of ILookup.
   A pattern is a vector, contains keys or joins, with keys in patterns, pull
   acts just like clojure.core/select-keys:
     `(pull [:a :c] {:a 3 :b 5 :c 6}) => {:a 3 :c 6}`
   A join in the pattern is a map with its keys corresponding keys in data, while
   value is another pattern. So this nested pattern can travel nested data:
     ```
     (pull [:a {:c [:ca]}] {:a 3 :b 5 :c [{:ca 4, :cb :foo} {:ca -1, :cb :bar}])
     => {:a 3 :c [{:ca 4} {:ca -1}]}
     ```
    If some private key-values in your data you do not want pull to return, for
    instance, password or other sensitive data, you can specify a `:private` meta
    to the data, like:
     `(pull [:a :b] ^:private #{:a} {:a :secret, b: 5}) => {:b 5}`
  "
  [pattern data]
  (impl/-pull data pattern))
