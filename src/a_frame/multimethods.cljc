(ns a-frame.multimethods)

(defmulti event->id
  "get the id from an event - to support
   both map and vector events"
  (fn [ev]
    (cond

      (map? ev) :map
      (vector? ev) :vector)))

(defmethod event->id :default
  [ev]
  (throw
   (ex-info "unrecognised event type" {:event ev})))

(defmulti validate
  (fn [schema _value] schema))

(defmethod validate :default
  [schema value]
  (throw
   (ex-info
    "unknown validate schema"
    {:schema schema
     :value value})))
