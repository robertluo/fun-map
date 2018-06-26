# fun-map, one map for all code in map

[![Build Status](https://travis-ci.org/robertluo/fun-map.svg?branch=master)](https://travis-ci.org/robertluo/fun-map)
[![Clojars Project](https://img.shields.io/clojars/v/robertluo/fun-map.svg)](https://clojars.org/robertluo/fun-map)

In clojure, code is data, the fun-map turns value fetching function call into map value accessing.

For example, when we store a delay as a value inside a map, we may want to retrieve the value wrapped inside, not the delayed object itself, i.e. a `deref` automatically be called when accessed by map's key. This way, we can treat it as if it is a plain map, without the time difference of when you store and when you retrieve. There are libraries exist for this purpose commonly known as *lazy map*.

Likewise, we may want to store future object to a map, then when accessing it, it's value retrieving will happen in another thread, which is useful when you want parallels realizing your values. Maybe we will call this *future map*?

How about combine them together? Maybe a *delayed future map*?

Also there is widely used library as [prismatic graph](https://github.com/plumatic/plumbing), store functions as map values, by specify which keys they depend, this map can be compiled to traverse the map.

One common thing in above scenarios is that if we store something in a map as a value, it is intuitive that we care just its underlying real value, no matter when it can be accessed, or what its execution order. As long as it won't change its value once referred, it will be treated as a plain value.

## Usage

### Simplest scenarios

Any value implements `clojure.lang.IDeref` interface in a fun-map will automatically `deref` when accessed by its key. In fact, it will be a *deep deref*, keeps `deref` until it reaches a none ref value.

```clojure
(require '[robertluo.fun-map :refer [fun-map fnk touch]])

(def m (fun-map {:a 4 :b (delay (println "accessing :b") 10)}))

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

`fnk` macro will be handy in many cases, it destructs args from the map, and set the `:wrap` meta.

```clojure
(def m (fun-map {:xs (range 10)
                 :count-keys ^:wrap (fn [m] (count (keys m)))
                 :sum (fnk [xs] (apply + xs))
                 :cnt (fnk [xs] (count xs))
                 :avg (fnk [sum cnt] (/ sum cnt))}))
(:avg m)
;;=> 9/2

(:count-keys m)
;;=> 5
```

Notice the above example looks like a prismatic graph, with the difference that a fun-map remains a map, so it can be composed like a map, like `merge` with other map, `assoc` plain value, etc.

Though you should watch out that fun-map does not compute dependencies of keys and the function in a value will just be invoked once, re-assoc a value will not cause its dependents re-invoke.

Fun-map also can be nested, so you could `get-in` or `update-in`.

### Trace the function calls

Accessing values of fun-map can be traced, which is very useful for logging, debugging and make it an extremely lightweight (< 100 LOC now) life cycle system.

```clojure
(def invocations (atom []))

(def m (fun-map {:a 3 :b (fnk [a] (inc a)) :c (fnk [b] (inc b))}
                 :trace-fn (fn [k v] (swap! invocations conj [k v]))))

(:c m) ;;accessing :c will in turn accessing :b
@invocations
;;=> [[:b 4] [:c 5]]
```

### Life cycle map for orderred shutdown

Using above trace feature, it is very easy to support a common scenario of [components](http://thinkrelevance.com/blog/2013/06/04/clojure-workflow-reloaded). The starting of components is simply accessing them by name, `life-cycle-map` will make it haltable (implemented `java.io.Closeable` also) by reverse its starting order.

`closeable` function is very convenient this purpose, by wrapping any value and a close function (which takes no argument), the value will appear to be map entry's value, while the close function will get called when shutting down the life cycle map.

```clojure
(def system
  (life-cycle-map
    {:component/a
     (fnk []
       (closeable 100 #(println "halt :a")))
     :component/b
     (fnk [:component/a]
       (closeable (inc a) #(println "halt :b")))}))

 (touch system) ;;start the entire system, you may also just start part of system, and the system is {:component/a 100 :component/b 101}
 (halt! system)
 ;;halt :b
 ;;halt :a
```

`Haltable` protocol can be extended to your type of component, or you can implement `java.io.Closeable` interface to indicate it is a life cycle component.

## License

Copyright © 2018 Robertluo

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
