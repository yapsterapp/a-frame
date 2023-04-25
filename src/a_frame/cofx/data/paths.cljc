(ns a-frame.cofx.data.paths
  (:require
   [a-frame.schema :as af.schema]
   [a-frame.interceptor-chain.data.data-path :refer [data-path]]))

(defn ^:private coerce-vec
  [p]
  (if (sequential? p)
    (vec p)
    [p]))

(defn cofx-path
  ([p] (cofx-path p false))
  ([p maybe?]
   (data-path
    (into
     [af.schema/a-frame-coeffects]
     (coerce-vec p))
    maybe?)))

(defn event-path
  ([p] (event-path p false))
  ([p maybe?]
   (data-path
    (into
     [af.schema/a-frame-coeffects
      af.schema/a-frame-coeffect-event]
     (coerce-vec p))
    maybe?)))
