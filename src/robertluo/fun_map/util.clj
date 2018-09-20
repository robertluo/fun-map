(ns robertluo.fun-map.util)

(defmacro opt-require
  "Optional requires rqr-clause and if it succeed do the body"
  {:style/indent 1}
  [rqr-clause & body]
  (when
      (try
        (require rqr-clause)
        true
        (catch Exception _
          false))
    `(do ~@body)))
