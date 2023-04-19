(ns prpr3.a-frame.schema
  (:require
   [malli.util :as mu]
   [prpr3.stream :as stream]))

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

(def a-frame-event :a-frame/event)
(def a-frame-events :a-frame/events)
(def a-frame-event-transitive-coeffects? :a-frame.event/transitive-coeffects?)

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

;; (s/defschema Event
;;   [(s/one s/Keyword :event-kw)
;;    s/Any])

(def Event
  "an event as dispatched by applications"
  [:cat :keyword [:* :any]])

;; (s/defschema Events
;;   [Event])

(def Events
 [:+ Event])

;; (s/defschema InitialCoeffects
;;   {s/Keyword s/Any})

(def InitialCoeffects
  "these are initial coeffects which can be given to a dispatch
   they don't yet have the Event associated with them"
  [:map-of :keyword :any])

;; (s/defschema ExtendedEvent
;;   {a-frame-event Event
;;    (s/optional-key a-frame-coeffects) InitialCoeffects
;;    (s/optional-key a-frame-event-transitive-coeffects?) s/Bool
;;    (s/optional-key a-frame-event-modify-interceptor-chain) (s/pred fn?)})

(def ExtendedEvent
  "an event along with any initial coeffects"
  [:map
   [a-frame-event Event]
   [a-frame-coeffects {:optional true} InitialCoeffects]
   [a-frame-event-transitive-coeffects? {:optional true} :boolean]
   [a-frame-event-modify-interceptor-chain {:optional true} fn?]])

;; (s/defschema EventOrExtendedEvent
;;   (s/conditional
;;    vector? Event
;;    map? ExtendedEvent))

(def EventOrExtendedEvent
  [:or Event ExtendedEvent])

;; (s/defschema EventsOrExtendedEvents
;;   [EventOrExtendedEvent])

(def EventsOrExtendedEvents
  [:+ EventOrExtendedEvent])

;; (s/defschema Coeffects
;;   {a-frame-coeffect-event Event
;;    s/Keyword s/Any})

(def Coeffects
  "these are the Coeffects that a handler will see"
  [:map
   [a-frame-coeffect-event Event]])

;; (defn derive-coeffects-schema
;;   "given some expected coeffects, derive
;;    a Coeffects schema from the base schema
;;    (always including the event, with a lax schema if no
;;     stricter schema is given)

;;    - strict? : true to prevent additional coeffect keys. false by default
;;    - event-schema : a more restrictive schema for the event
;;    - expected-coeffects-schema : a map schema for exepcted coeffects"
;;   ([strict? event-schema expected-coeffects-schema]
;;    (prpr3.s/merge-map-schemas
;;     (if (some? event-schema)
;;      {a-frame-coeffect-event event-schema}
;;      {a-frame-coeffect-event Event})
;;     expected-coeffects-schema
;;     (when-not strict?
;;       {s/Keyword s/Any})))
;;   ([expected-coeffects-schema]
;;    (derive-coeffects-schema false nil expected-coeffects-schema))
;;   ([event-schema expected-coeffects-schema]
;;    (derive-coeffects-schema false event-schema expected-coeffects-schema)))

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


;; (s/defschema EffectsMap
;;   {s/Keyword s/Any})

(def EffectsMap
  [:map-of :keyword :any])

(declare Effects)

;; (s/defschema EffectsVector
;;   [Effects])

(def EffectsVector
  [:sequential Effects])

;; (s/defschema Effects
;;   (s/conditional
;;    vector?
;;    EffectsVector

;;    map?
;;    EffectsMap

;;    nil?
;;    (s/eq nil)))

(def Effects
  [:or EffectsVector EffectsMap :nil])

;; (s/defschema AppCtx
;;   {s/Keyword s/Any})

(def AppCtx
  [:map-of :keyword :any])

;; (s/defschema Interceptor
;;   {:id s/Keyword
;;    (s/optional-key :enter) (s/pred fn?)
;;    (s/optional-key :leave) (s/pred fn?)})

(def Interceptor
  [:map
   [:id :keyword]
   [:enter {:optional true} :fn?]
   [:leave {:optional true} :fn?]])

;; (s/defschema Router
;;   {a-frame-app-ctx AppCtx
;;    a-frame-router-event-stream #?(:clj (s/pred stream/stream?)
;;                                   :cljs s/Any)
;;    a-frame-router-global-interceptors-a s/Any
;;    (s/optional-key a-frame-router-executor) s/Any
;;    (s/optional-key a-frame-router-buffer-size) s/Int})

(def Router
  [:map
   [a-frame-app-ctx AppCtx]
   [a-frame-router-event-stream [:fn stream/stream?]]
   [a-frame-router-global-interceptors-a :any]
   [a-frame-router-executor {:optional true} :any]
   [a-frame-router-buffer-size {:optional true} :int]])

;; (s/defschema HandleEventInterceptorCtx
;;   {a-frame-router Router
;;    a-frame-app-ctx AppCtx
;;    a-frame-effects Effects
;;    a-frame-coeffects Coeffects
;;    s/Keyword s/Any})

(def HandleEventInterceptorCtx
  [:map
   [a-frame-router Router]
   [a-frame-app-ctx AppCtx]
   [a-frame-effects Effects]
   [a-frame-coeffects Coeffects]])
