(ns a-frame.interceptor-chain.data.tag-readers
  #?(:cljs (:require-macros [a-frame.interceptor-chain.data.tag-readers]))
  (:require
   #?(:clj [promisespromises.util.macro :refer [if-cljs]])
   [a-frame.interceptor-chain.data.data-path
    :refer [data-path]]))

;; see https://github.com/clojure/clojurescript-site/issues/371
;; 3! different versions of the tag-readers are required for:
;; 1. clj compiling cljs
;; 2. clj
;; 3. cljs self-hosted or runtime

#?(:clj
   (defn read-ctx-path
     [path]
     (if-cljs
         `(data-path ~path)

       ;; if we eval the path then we can use var symbols
       ;; in the path. this will only work on clj
       (data-path (eval path)))))

#?(:cljs
   (defn ^:export read-ctx-path
     [path]
     `(data-path ~path)))
