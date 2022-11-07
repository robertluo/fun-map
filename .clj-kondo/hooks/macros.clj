(ns hooks.macros
  "hooks for macros. 
   https://github.com/clj-kondo/clj-kondo/blob/master/doc/hooks.md"
  (:require [clj-kondo.hooks-api :as api]))

(defn fw [{:keys [node]}]
  (let [[m & body] (-> node :children rest)
        new-node (api/list-node
                  (list*
                   (api/token-node 'fn)
                   (api/vector-node [(api/token-node '_) m])
                   body))]
    {:node new-node}))

(comment
  (def node (api/parse-string "(fw {:keys [a b]} (+ a b))"))
  (str (:node (fw {:node node})))
  )

(defn fnk [{:keys [node]}]
  (let [[args & body] (-> node :children :rest)
        new-node (api/list-node
                  (list*
                   (api/token-node 'fn)
                   args
                   body))]
    {:node new-node}))

(comment
  (def node (api/parse-string "(fnk [a b] (+ a b))"))
  (:node (fnk {:node node}))
  )
