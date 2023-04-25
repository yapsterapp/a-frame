(ns a-frame.interceptor-chain.data
  (:require
   [a-frame.interceptor-chain.data.protocols :as p]
   [a-frame.interceptor-chain.data.data-path]
   [taoensso.timbre :refer [info warn]])
  (:import
   #?(:clj [clojure.lang IPersistentMap IPersistentVector])))

(declare resolve-data)

(extend-protocol p/IResolveData
  #?@(:clj
      [IPersistentMap
       (-resolve-data [spec interceptor-ctx]
                      ;; (warn "resolve-data MAP")
                      (into
                       {}
                       (for [[k v] spec]
                         (let [rv (resolve-data v interceptor-ctx)]
                           [k rv]))))])

  #?@(:cljs
      [cljs.core.PersistentHashMap
       (-resolve-data [spec interceptor-ctx]
                      (into
                       {}
                       (for [[k v] spec]
                         [k (resolve-data v interceptor-ctx)])))

       cljs.core.PersistentArrayMap
       (-resolve-data [spec interceptor-ctx]
                      (into
                       {}
                       (for [[k v] spec]
                         [k (resolve-data v interceptor-ctx)])))])

  #?(:clj IPersistentVector
     :cljs cljs.core.PersistentVector)
  (-resolve-data [spec interceptor-ctx]
    (mapv
     #(resolve-data % interceptor-ctx)
     spec))

  #?(:clj Object
     :cljs default)
  (-resolve-data [spec _interceptor-ctx]
    ;; (warn "resolve-data DEFAULT" spec)
    spec)

  nil
  (-resolve-data [_spec _interceptor-ctx]
    nil))


(defn resolve-data
  [spec interceptor-ctx]
  ;; (warn "resolve-data" spec)
  (p/-resolve-data spec interceptor-ctx))
