# fun-map, blurs the line between identity, state and function

[![CI](https://github.com/robertluo/fun-map/actions/workflows/main.yml/badge.svg)](https://github.com/robertluo/fun-map/actions/workflows/main.yml)
[![Clojars Project](https://img.shields.io/clojars/v/robertluo/fun-map.svg)](https://clojars.org/robertluo/fun-map)
[![cljdoc badge](https://cljdoc.xyz/badge/robertluo/fun-map)](https://cljdoc.xyz/d/robertluo/fun-map/CURRENT)

## TL;DR show me the code!

```clojure
(require '[robertluo.fun-map :refer :all])

;; Showcase for wrapping functions in a map, accessing using key

(def m
  "a map contains routines to calculate on :numbers"
  (fun-map {:cnt     (fnk [numbers] (count numbers))
            :sum     (fnk [numbers] (reduce + 0 numbers))
            :average (fnk [cnt sum] (float (/ sum cnt)))}))

(:average (assoc m :numbers [3 9 8 10 20])) ;=> 10.0

;; Showcase for lazily update pattern

(def numbers (atom [0]))
(def m (assoc m :numbers numbers))
(:average m) ;=> 0.0
(reset! numbers (range 101))
(:average m) ;=> 50.0

;; Showcase for composing steps later

(def m (merge m {:num (range 101) :numbers (fnk [num] (filter #(< % 50) num))}))
(:average m) ;=> 24.5

;; Showcase for parallel computing value
;; With manifold in your dependencies

(def m
  (fun-map
   {:a (delay (Thread/sleep 3000) 30)
    :b (delay (Thread/sleep 3000) 20)
    :c (fw {:keys [a b] :par? true} (* a b))}))

(time (:c m)) ;=> 600 in approx. 3000msec
```
## Changes

### 0.5
 - Change version naming convention from `major.minor.patch` to `major.minor.commit-count`.
 
### 0.4

 - Introduce `robertluo.pull/pull` function to pull information.

### 0.3

 - `fw` macro now is more flexible, you can use `:wrappers` to specify underlying wrapper implementations.
 - optional spec checker with `:spec` specify the return value's spec. Will check if it conform the spec.
 - optional parallel execution with `:par? true` specified in `fw` and with [manifold](https://github.com/ztellman/manifold) in your projects dependencies.
 - `print-method` for better developing experience. When you print the content of a fun map, the wrapped function will not be invoked. You can use `(into {} m)` to print everything inside it though.
 - introduce `lookup` function allowing transform any function as a part of map.

> Breaking change since 0.1.x: normal function inside fun-map with `:wrap true` is not supported now, use `fw` macro instead.

> Breaking change since 0.2.x: function wrapper does not extending `IFn` anymore since it brings too much implementation burden for nested wrappers.

## Rationale

In clojure, code is data, the fun-map turns value fetching function call into map value accessing.

For example, when we store a delay as a value inside a map, we may want to retrieve the value wrapped inside, not the delayed object itself, i.e. a `deref` automatically be called when accessed by map's key. This way, we can treat it as if it is a plain map, without the time difference of when you store and when you retrieve. There are libraries exist for this purpose commonly known as *lazy map*.

Likewise, we may want to store future object to a map, then when accessing it, it's value retrieving will happen in another thread, which is useful when you want parallels realizing your values. Maybe we will call this *future map*?

How about combine them together? Maybe a *delayed future map*?

Also there is widely used library as [prismatic graph](https://github.com/plumatic/plumbing), store functions as map values, by specify which keys they depend, this map can be compiled to traverse the map.

One common thing in above scenarios is that if we store something in a map as a value, it is intuitive that we care just its underlying real value, no matter when it can be accessed, or what its execution order. As long as it won't change its value once referred, it will be treated as a plain value.

## Concepts

### Value Wrapper

A value wrapper is anything wrapped a value inside. The consumer of a wrapper just interests in the value, it is the provider who concerns about how it wraps. In a fun-map, when accessed by key, the consumer just get the wrapped value, ignoring the difference of the wrapper itself. This frees up for consumer to change code if the wrapper itself changes. Practically, the consumer can just assume it is a plain value, fun-map will unwrap it.

Simple value wrappers are `clojure.lang.IDeref` instances, like `delay`, `future`, `promise` which can not change its wrapped value once realized; `atom`, `ref`, `agent` are also wrappers, but their wrapped value can change. Fun-map blurs the line between all these wrappers. For example:

```clojure
(def m (fun-map {:numbers (delay [3 4])}))

(defn f [{:keys [numbers]}]
  (apply * numbers))
```

The author of f can just use plain value map `{:a [3 4]}` to test, but use `m` as the argument later.

### Function Wrapper

What takes fun-map even further is that a function takes a map as its argument can be treated as value wrapper. The wrapped value will be returned when call it. `fw` macro will define such wrapper inline:

```clojure
(def m (fun-map {:numbers (fw {} [3 4])}))
```

And author of `f` can also treat it as a normal map!

### Chained Function Wrapper

The function wrappers' map argument is the fun-map contained it, so by putting different function wrappers inside a fun-map, meaning it provides a new way to construct a function invoking path.

```clojure
(def m (fun-map {:numbers [3 4]
                 :cnt (fw {:keys [numbers]} (count numbers))
                 :average (fw {:keys [numbers cnt]}
                            (/ (reduce + 0 numbers) cnt))}))
```

Accessing `:average` value of `m` will make fun-map call it and in turns accessing its `:numbers` and `:cnt` values, and the later is another function wrapper, which make the `:average` function indirectly calling `:cnt` function inside.

### `fw` macro

`fw` macro can be used to create an anonymous function with its wrapper like in the above examples.

```clojure
(fw {:keys [:a/a :b] :or {a 10 b 20}}
  (* a b))
```

You may notice that `fw` does not take a vector as its argument, but a map. That's because a function wrapped in can only take a map as its single argument, so the macro saved you a pair of square bracket. It follows same syntax as [standard associative destructrue](https://clojure.org/guides/destructuring#_associative_destructuring).

### Wrapper decorators

In addition to simple function wrapper, we can use wrapper decorators to provide more, you can use `:wrappers` key in `fw`'s argument map:

```clojure
(fw {:keys [a] :wrappers []}
  (inc a))
```

This will create a non-decorated plain function wrapper.

```clojure
(fw {:keys [a]}
  (inc a))
```

Without specify `:wrappers`, the created wrapper will have default decorators `[:spec :trace :cache]`.

#### `:spec` decorator (only available with `clojure.spec.alpha` in your class path, a.k.a clojure >= 1.9)

You can specify the spec of the returned value, and the decorator will check if the actual result conform it.

```clojure
(def m (fun-map {:a 12 :b (fw {:keys [a] :spec string?} (inc a))}))

(:b m) ;=> will throw an exception
```

#### `:cache` decorator

A function wrapper can have a focus function to define whether it should be re-unwrapped, if the return value of the function keeps same, it will just return a cached return value. So the focus function need to be very efficient (at least much faster than the wrapped function itself) and pure functional. If no focus function provided, the function wrapper will just be invoked once.

```clojure
(def m (fun-map {:numbers (atom [5 3])
                 :other-content (range 1000)
                 :cnt (fw {:keys [numbers] :focus numbers}
                        (count numbers))}))
```

The function inside `:cnt` will only be invoked if `:numbers` changes. `fnk` macro can be used instead of `fw` for keys destructuring and focus on these keys:

```clojure
(fnk [numbers] (count numbers))
```

#### `:trace` decorator

Sometimes you want to know when your function wrapper really called wrapped function, you could attach this trace functions to it by `:trace` option:

```clojure
(def m (fun-map {:numbers (atom [5 3])
                 :cnt (fw {:keys [numbers]
                           :trace (fn [k v] (println "key is:" k "value is:" v))}
                        (count numbers))}))
```

##### Map shared trace function

The fun-map function itself has a `:trace-fn` function can apply to all function wrappers inside.

### Parallel accessing dependencies

With manifold's excellent `let-flow` macro and its `future` function, if you have it in your class path, specify `:par? true` in `fw` macro will make a function wrapper accessing its dependencies in managed threads.

```clojure
(def m (fun-map {:a (delay (Thread/sleep 3000) 20)
                 :b (delay (Thread/sleep 3000) 30)
                 :c (fw {:keys [a b] :par? true} (* a b))}))

(time (:c m)) ;=> 600 in approx. 3000msec instead of 6000
```

## API doc

Check the [cljdoc](https://cljdoc.xyz) link on the top of the page.

A briefing:

 - `fun-map` itself, returns a fun-map of course.
 - `fw` macro for create function wrappers.
 - `fnk` macro is a shortcut for common scenario of `fw`, with just keys can be specified, and focus on these keys.
 - `touch` function to force evaluating a fun-map.
 - `life-cycle-map` a simple life cycle management fun-map implementation.
 - `closeable` to create a value wrapper for components support `close` concept.
 - `lookup` returns a ILookup instance from any single arity function
 - `robertluo.pull/pull` returns a data structure from a ILookup (map is also supported)

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
;;=> 10 ;Delay will be evaluated just once

```

### Where fun begins

`fnk` macro will be handy in many cases, it destructs args from the map, and set focus on these keys:

```clojure
(def m (fun-map {:xs (range 10)
                 :count-keys (fw {:as m} (count (keys m)))
                 :sum (fnk [xs] (apply + xs))
                 :cnt (fnk [xs] (count xs))
                 :avg (fnk [sum cnt] (/ sum cnt))}))
(:avg m)
;;=> 9/2

(:count-keys m)
;;=> 5
```

Notice the above example looks like a prismatic graph, with the difference that a fun-map remains a map, so it can be composed like a map, like `merge` with other map, `assoc` plain value, etc.

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

### Use components from other library

It is very easy to turn a [component](https://github.com/stuartsierra/component) to `closeable` and use a life-cycle-map to integrate a system:

```clojure
(defn component->closeable
  [component]
  (closeable
    (lifecycle/start component)
    #(lifecycle/stop component)))
```
## Development

Fun-map using [kaocha](https://github.com/lambdaisland/kaocha) as test runner.

 - use `lein kaocha --watch` to automatically run tests whenever code changes.

## License

Copyright © 2018, 2019 Robertluo

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
