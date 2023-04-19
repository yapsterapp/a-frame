(ns prpr3.a-frame.registry)

;; registry is for things defined at compile-time and
;; named globally
;; - handler functions
;; - interceptor chains
(def registry (atom {}))

(defn register-handler
  [kind id handler]
  (swap! registry assoc-in [kind id] handler)
  true)

(defn get-handler
  [kind id]
  (get-in @registry [kind id]))

(defn unregister-handler
  ([kind]
   (swap! registry assoc kind {}))
  ([kind id]
   (swap! registry update kind dissoc id)))
