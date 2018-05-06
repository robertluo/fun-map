# fun-map, one map for all code in map

[![Build Status](https://travis-ci.org/robertluo/fun-map.svg?branch=master)](https://travis-ci.org/robertluo/fun-map)

In clojure, code is data, the fun-map turns value fetching function call into map value accessing.

For example, when we store a delay as a value inside a map, we may want to retrieve the value wrapped inside, not the delayed object itself, i.e. a `deref` automatically be called when accessed by map's key. This way, we can treat it as if it is a plain map, without the time difference of when you store and when you retrieve. There are libraries exist for this purpose commonly known as *lazy map*.

Likewise, we may want to store future object to a map, then when accessing it, it's value retrieving will happen in another thread, which is useful when you want parallels realizing your values. Maybe we will call this *future map*?

How about combine them together? Maybe a *delayed future map*?

Also there is widely used library as [prismatic graph](https://github.com/plumatic/plumbing), store functions as map values, by specify which keys they depend, this map can be compiled to traverse the map.

One common thing in above scenarios is that if we store something in a map as a value, it is intuitive that we care just its underlying real value, no matter when it can be accessed, or what its execution order. As long as it won't change its value once referred, it will be treated as a plain value.

## Usage

### Simplest scenarios

As a lazy map without `delay`, because a function is only be called when accessed.

```clojure
(require '[robertluo.fun-map :refer [fun-map fnk touch]])

(def m (fun-map {:a 4 :b (fn [_] (println "accessing :b") 10)}))

(:b m)

;;"accessing :b"
;;=> 10

(:b m)
;;=> 10 ;Any function as a value will be just be invoked once

```

Or future objects as values that will be accessed at same time.

```clojure
(def m (fun-map {:a (future (do (Thread/sleep 1000) 10))
                 :b (future (do (Thread/sleep 1000) 20))}))

(touch m)

;;=> {:a 10, b 20} ;futures in :a, :b are evaluated parallelly
```

### Where fun begins

A function in fun-map and has `:wrap` meta as `true` takes the map itself as the argument, return value will be *unwrapped* when accessed by key.

```clojure
(def m (fun-map {:xs (range 10)
                 :count-keys ^:wrap (fn [m] (count (keys m)))
                 :sum (fnk [xs] (apply + xs))
                 :cnt (fnk [xs] (count xs)
                 :avg (fnk [sum cnt] (/ sum cnt)))}))
(:avg m)
;;=> 9/2

(:count-keys m)
;;=> 5
```

### Trace the function calls

Instead of statically compiled, invocation of fun-map's function can be traced.

```clojure
(def invocations (atom []))

(def m (fun-map {:a 3 :b (fnk [a] (inc a)) :c (fnk [b] (inc b))}
                 :trace-fn (fn [k v] (swap! invocations conj [k v]))))

(:c m) ;;accessing :c will in turn accessing :b
@invocations
;;=> [[:b 4] [:c 5]]
```

In above example, the invocations is ordered, so it can be used in a scenario like a dependency graph.

## License

Copyright © 2018 Robertluo

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
