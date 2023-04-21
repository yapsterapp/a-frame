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
