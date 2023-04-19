(ns prpr3.a-frame.interceptor-chain.data.data-path
  (:require
   #?(:clj [clojure.pprint :as pprint])
   [prpr3.a-frame.interceptor-chain.data.protocols :as p]
   [taoensso.timbre :refer [info warn]]))

;; this protocol is for the cljs IPrintWithWriter
;; method, because there is no .path getter
;; wiht a cljs deftype
(defprotocol IDataPath
  (-path [_]))

;; don't know why, but cljs compile doesn't agree
;; with deftype here - probably some badly
;; documented interaction with the tag-readers
(defrecord DataPath [path]
  p/IResolveData
  (-resolve-data [_spec interceptor-ctx]
    (let [data (get-in interceptor-ctx path)]
      ;; (warn "resolve DataPath" path)
      data))

  IDataPath
  (-path [_]
    path))

#?(:clj
   (defn print-data-path
     [dp ^java.io.Writer w]
     (.write w "#prpr3.ctx/path ")
     (print-method (-path dp) w)))

#?(:clj
   (defmethod print-method DataPath [this ^java.io.Writer w]
     (print-data-path this w)))

#?(:clj
   (defmethod print-dup DataPath [this ^java.io.Writer w]
     (print-data-path this w)))

#?(:clj
   (.addMethod pprint/simple-dispatch
               DataPath
               (fn [dp]
                 (print-data-path dp *out*))))

#?(:cljs
   (extend-protocol IPrintWithWriter
     DataPath
     (-pr-writer [dp writer _]
       (write-all writer "#prpr3.ctx/path " (-path dp) ""))))
