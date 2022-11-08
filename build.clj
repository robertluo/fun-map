(ns build
  (:require [clojure.tools.build.api :as b]
            [org.corfield.build :as cb]))

(defn project
  "apply project default to `opts`"
  [opts]
  (let [defaults {:lib     'robertluo/fun-map
                  :version (format "0.5.%s" (b/git-count-revs nil))
                  :scm     {:url "https://github.com/robertluo/fun-map"}}]
    (merge defaults opts)))

(defn tests
  [opts]
  (-> opts
      (cb/run-task [:dev :test])
      (cb/run-task [:dev :cljs-test])))

(defn copy-clj-kondo-config 
  [{:keys [lib class-dir] :as opts :or {class-dir (cb/default-class-dir)}}]
  (let [target-dir (str class-dir "/clj-kondo.exports/" lib)]
    (b/copy-file {:src ".clj-kondo/config.edn" :target (str target-dir "/config.edn")})
    (b/copy-dir {:src-dirs [".clj-kondo/hooks"] 
                 :target-dir (str target-dir "/hooks")}))
  opts)

(defn ci
  [opts]
  (-> opts
      (project)
      (cb/clean)
      (tests)
      (copy-clj-kondo-config)
      (cb/jar)))

(defn deploy
  [opts]
  (-> opts
      (project)
      (cb/deploy)))
