(ns hooks.macros
  "hooks for macros. 
   https://github.com/clj-kondo/clj-kondo/blob/master/doc/hooks.md"
  (:require [clj-kondo.hooks-api :as api]))

(defn fw [{:keys [node]}]
  (let [[m & body] (-> node :children rest)]
    (when (not= (:tag m) :map)
     (throw (ex-info "fw need a map as its first argument" {})))
    {:node (api/list-node
            (list*
             (api/token-node 'clojure.core/fn)
             (api/vector-node [(api/token-node '_) m])
             body))}))

(comment
  (def node (api/parse-string "(fw {:keys [a b]} (+ a b))"))
  (str (:node (fw {:node node})))
  )

(defn fnk [{:keys [node]}]
  (let [[arg-vec & body] (-> node :children rest)
        new-node (api/list-node
                  (list*
                   (api/token-node 'fw)
                   (api/map-node [(api/keyword-node :keys) arg-vec])
                   body))]
    {:node new-node}))

(comment
  (api/map-node [(api/token-node 'a) (api/token-node 'b)])
  (def node (api/parse-string "(fnk [a b] (+ a b))")) 
  (:node (fnk {:node node}))
  )
