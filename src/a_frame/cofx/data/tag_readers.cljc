(ns a-frame.cofx.data.tag-readers
  #?(:cljs (:require-macros [a-frame.cofx.data.tag-readers]))
  (:require
   #?(:clj [promisespromises.util.macro :refer [if-cljs]])
   [a-frame.cofx.data.paths :refer [cofx-path event-path]]
   [a-frame.interceptor-chain.data.tag-readers]))

;; see https://github.com/clojure/clojurescript-site/issues/371
;; 3! different versions of the tag-readers are required for:
;; 1. clj compiling cljs
;; 2. clj
;; 3. cljs self-hosted or runtime (in .cljs file)

#?(:clj
   (defn read-cofx-path
     [path]
     (if-cljs
         `(cofx-path ~path)

       ;; if we eval the path then we can use var symbols
       ;; in the path. this will only work on clj
       (cofx-path (eval path)))))

#?(:clj
   (defn read-event-path
     "the event is always in the cofx at a known key"
     [path]
     (if-cljs
         `(event-path ~path)

       (event-path (eval path)))))

#?(:cljs
   (defn ^:export read-cofx-path
     [path]
     `(cofx-path ~path)))

#?(:cljs
   (defn ^:export read-event-path
     "the event is always in the cofx at a known key"
     [path]
     `(event-path ~path)))
