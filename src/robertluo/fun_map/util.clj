(ns robertluo.fun-map.util)

(defmacro opt-require
  "Optional requires rqr-clause and if it succeed do the `if-body` or `else-body`"
  {:style/indent 1}
  [rqr-clause if-body else-body]
  `(try
     (require ~rqr-clause)
     ~if-body
     (catch Exception _#
       ~else-body)))
