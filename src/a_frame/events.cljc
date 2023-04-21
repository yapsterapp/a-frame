(ns a-frame.events
  (:require
   [malli.experimental :as mx]
   [promisespromises.error :as err]
   [a-frame.schema :as schema]
   [a-frame.registry :as registry]
   [a-frame.std-interceptors :as std-interceptors]
   [a-frame.interceptor-chain :as interceptor-chain]
   [a-frame.multimethods :as mm]
   [taoensso.timbre :refer [warn]]))


;; maps are a nicer representation of events, since
;; their fields have names, which makes paths easier to
;; read
(defmethod mm/event->id :map
  [{id schema/a-frame-id
    :as _ev}]
  id)

;; vectors are the default re-frame representation of events.
;; their fields only have index names, which makes paths
;; hard to read
(defmethod mm/event->id :vector
  [[id :as _ev]]
  id)

(defn flatten-and-remove-nils
  [_id interceptors]
  (->> interceptors
       flatten
       (remove nil?)))

(defn register
  "register an interceptor chain for an event - this should terminate
   in an interceptor which has a data-reference to the pure event-handler
   fn for the event"
  [id interceptors]
  (registry/register-handler
   schema/a-frame-kind-event
   id
   (flatten-and-remove-nils id interceptors)))

(defn register-pure
  "register a pure event-handler fn"
  [id pure-handler-fn]
  (registry/register-handler
   schema/a-frame-kind-event-pure
   id
   pure-handler-fn))

(defn reg-event-fx
  ([id handler]
   (reg-event-fx id nil handler))

  ([id
    interceptors
    handler]
   (register-pure id handler)
   (register
    id
    [::std-interceptors/unhandled-error-report
     :a-frame.fx/do-fx
     interceptors
     (std-interceptors/fx-handler->interceptor id)])))

(defn reg-event-ctx
  ([id handler]
   (reg-event-ctx id nil handler))

  ([id
    interceptors
    handler]
   (register-pure id handler)
   (register
    id
    [::std-interceptors/unhandled-error-report
     :a-frame.fx/do-fx
     interceptors
     (std-interceptors/ctx-handler->interceptor id)])))

(defn clear-event
  ([]
   (registry/unregister-handler
    schema/a-frame-kind-event-pure)
   (registry/unregister-handler
    schema/a-frame-kind-event))

  ([id]
   (registry/unregister-handler
    schema/a-frame-kind-event-pure
    id)
   (registry/unregister-handler
    schema/a-frame-kind-event
    id)))

(mx/defn coerce-event-options
  "Event|EventOptions -> EventOptions"
  [event-or-event-options :- schema/EventOrEventOptions]

  (cond

    (vector? event-or-event-options)
    {schema/a-frame-event event-or-event-options}

    (and (map? event-or-event-options)
         (contains? event-or-event-options schema/a-frame-id))
    {schema/a-frame-event event-or-event-options}

    (and (map? event-or-event-options)
         (contains? event-or-event-options schema/a-frame-event))
    event-or-event-options

    :else
    (throw (ex-info "malformed event-or-event-options"
                    {:event-or-event-options event-or-event-options}))))

(defn handle
  [{app-ctx schema/a-frame-app-ctx
    a-frame-router schema/a-frame-router
    global-interceptors schema/a-frame-router-global-interceptors}

   event-or-event-options]

  ;; (prn "HANDLE" event-or-event-options)

  (let [{event schema/a-frame-event
         init-coeffects schema/a-frame-init-coeffects
         modify-interceptor-chain schema/a-frame-event-modify-interceptor-chain
         :as _event-options} (coerce-event-options event-or-event-options)

        event-id (mm/event->id event)

        interceptors (registry/get-handler
                      schema/a-frame-kind-event
                      event-id)]

    (if (some? interceptors)
      (let [interceptors (into (vec global-interceptors) interceptors)

            interceptors (if (some? modify-interceptor-chain)
                           (modify-interceptor-chain interceptors)
                           interceptors)

            init-ctx (-> {schema/a-frame-coeffects init-coeffects
                          schema/a-frame-effects {}}

                         (assoc schema/a-frame-app-ctx app-ctx)

                         ;; add the event to any init-ctx coeffects ...
                         ;; so cofx handlers can access it
                         (assoc-in [schema/a-frame-coeffects
                                    schema/a-frame-coeffect-event]
                                   event))]

        (interceptor-chain/execute
         app-ctx
         a-frame-router
         interceptors
         init-ctx))

      (throw
       (err/ex-info
        (prn-str ::no-event-handler event)
        {:event event})))))
