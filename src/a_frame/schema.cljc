(ns a-frame.schema
  (:require
   [malli.util :as mu]
   [promisespromises.stream :as stream]))

(def a-frame-state :a-frame/state)
(def a-frame-state-handlers-a :a-frame.state/handlers-a)
(def a-frame-router :a-frame/router)
(def a-frame-router-event-stream :a-frame.router/event-stream)
(def a-frame-router-executor :a-frame.router/executor)
(def a-frame-router-buffer-size :a-frame.router/buffer-size)
(def a-frame-router-global-interceptors :a-frame.router/global-interceptors)
(def a-frame-router-global-interceptors-a :a-frame.router/global-interceptors-a)

;; a system-map
(def a-frame-app-ctx :a-frame/app-ctx)

;; a timbre with-context map for logging
(def a-frame-log-ctx :a-frame/log-ctx)

(def a-frame-event :a-frame/event) ;; an event
(def a-frame-id :a-frame/id) ;; an event id
;;
(def a-frame-events :a-frame/events)
(def a-frame-event-transitive-coeffects? :a-frame.event/transitive-coeffects?)
(def a-frame-init-coeffects :a-frame.event/init-coeffects)

(def a-frame-event-modify-interceptor-chain
  :a-frame.event/modify-interceptor-chain)

(def a-frame-interceptor-init-ctx :a-frame.events.interceptor/init-ctx)

(def a-frame-effects :a-frame/effects)
(def a-frame-coeffects :a-frame/coeffects)
(def a-frame-coeffect-event :a-frame.coeffect/event)

(def a-frame-kind-fx :a-frame.kind/fx)
(def a-frame-kind-cofx :a-frame.kind/cofx)

;; the event-handler interceptor chain
(def a-frame-kind-event :a-frame.kind/event)

;; the pure event handler
(def a-frame-kind-event-pure :a-frame.kind/event-pure)

(def InitialCoeffects
  "these are initial coeffects which can be given to a dispatch
   they don't yet have the Event associated with them"
  [:map-of :keyword :any])

(def MapEvent
  "an event as dispatched by applications"
  [:map
   ;; mandatory id field
   [a-frame-id :keyword]

   ;; open to any other keys as required by an event handler
   ])

(def VectorEvent
  [:cat :keyword [:* :any]])

(def Event
  [:or MapEvent VectorEvent])

(def EventOptions
  "an event along with some options affecting event processing"
  [:map
   {:closed true}

   ;; the event
   [a-frame-event Event]

   ;; options to direct event processing
   [a-frame-init-coeffects {:optional true} InitialCoeffects]
   [a-frame-event-transitive-coeffects? {:optional true} :boolean]
   [a-frame-event-modify-interceptor-chain {:optional true} fn?]])

(def EventOrEventOptions
  [:or Event EventOptions])

(def Events
  "a list of events"
  [:+ Event])

(def EventOrEventOptionsList
  [:+ EventOrEventOptions])

(def Coeffects
  "the Coeffects that a handler will see"
  [:map
   [a-frame-coeffect-event Event]
   ;; open to other keys
   ])

(defn derive-coeffects-schema
  "given some expected coeffects, derive
   a Coeffects schema from the base schema
   (always including the event, with a lax schema if no
    stricter schema is given)

   - closed? : true to prevent additional coeffect keys. false by default
   - event-schema : a more restrictive schema for the event
   - expected-coeffects-schema : a map schema for exepcted coeffects"
  ([closed? event-schema expected-coeffects-schema]

   (cond->

       (mu/merge
        [:map
         [a-frame-coeffect-event
          (if (some? event-schema)
            event-schema
            Event)]]
        expected-coeffects-schema)

     closed? (mu/closed-schema)))

  ([expected-coeffects-schema]
   (derive-coeffects-schema false nil expected-coeffects-schema))
  ([event-schema expected-coeffects-schema]
   (derive-coeffects-schema false event-schema expected-coeffects-schema)))

(def EffectsMap
  [:map-of :keyword :any])

(def EffectsVector
  [:* EffectsMap])

(def Effects
  [:or EffectsVector EffectsMap :nil])

(def AppCtx
  [:map-of :keyword :any])

(def Interceptor
  [:map
   [:id :keyword]
   [:enter {:optional true} fn?]
   [:leave {:optional true} fn?]])

(def Router
  [:map
   [a-frame-app-ctx AppCtx]
   [a-frame-router-event-stream [:fn stream/stream?]]
   [a-frame-router-global-interceptors-a :any]
   [a-frame-router-executor {:optional true} :any]
   [a-frame-router-buffer-size {:optional true} :int]])

(def HandleEventInterceptorCtx
  [:map
   [a-frame-router Router]
   [a-frame-app-ctx AppCtx]
   [a-frame-effects Effects]
   [a-frame-coeffects Coeffects]])
