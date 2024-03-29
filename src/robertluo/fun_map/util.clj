(ns robertluo.fun-map.util)

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
