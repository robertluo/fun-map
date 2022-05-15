(ns robertluo.fun-map.util 
  (:require [clojure.spec.alpha :as s]))

(defmacro opt-require
  "Optional requires rqr-clause and if it succeed do `then-body` or `else-body`"
  {:style/indent 2}
  [rqr-clause then-body else-body]
  (if
   (try
     (require rqr-clause)
     true
     (catch Exception _
       false))
    then-body
    else-body))

(defmacro when-require
  {:style/indent 2}
  [rqr-clause & body]
  `(opt-require ~rqr-clause (do ~@body) nil))

(comment
  (macroexpand '(when-require [clojure.spec.alpha] (do it) (do it 2)))
  )
