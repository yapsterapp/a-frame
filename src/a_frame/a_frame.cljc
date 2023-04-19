(ns prpr3.a-frame
  (:require
   [prpr3.a-frame.schema :as schema]
   [prpr3.a-frame.events :as events]
   [prpr3.a-frame.fx :as fx]
   [prpr3.a-frame.cofx :as cofx]
   [prpr3.a-frame.std-interceptors
    :refer [modify-interceptors-for-coeffects]]
   [prpr3.a-frame.registry :as registry]
   [prpr3.a-frame.router :as router]))

;; like re-frame, but for backend stuff
;; provide an app-context map to most of the core fns, from which an
;; a-frame instance (with event-stream, handler registrations &c)
;; can be extracted at :prpr3.a-frame/app
;; app-context will also be provided to event/fx/cofx handlers
;; so they can do complex stuff

(defn create-router
  "create an a-frame router"
  ([app] (create-router app {}))
  ([app opts] (router/create-router app opts)))

(defn create-router-dispose-fn
  "factory function for an a-frame router and
   a fn to dispose the router

  returns [router dispose-fn]"
  ([app] (create-router-dispose-fn app {}))
  ([app opts]
   (let [router (router/create-router app opts)]

     (router/run-a-frame-router router)

     [router #(router/stop-a-frame-router router)])))

(defn add-init-coeffects
  "add some initial coeffects to an event"
  [event init-coeffects]
  (assoc
     (events/coerce-extended-event event)
     schema/a-frame-coeffects init-coeffects))

(defn dispatch
  "dispatch a single event"
  ([router event]
   (router/dispatch router event))
  ([router init-coeffects event]
   (dispatch
    router
    (add-init-coeffects event init-coeffects))))

(defn dispatch-n
  "dispatch a vector of events"
  ([router events]
   (router/dispatch-n router events))

  ([router init-coeffects events]
   (dispatch-n
    router
    (map #(add-init-coeffects % init-coeffects)
         events))))

(defn dispatch-sync
  "dispatch a single event and await the completion of its
   processing (all cofx, event and fx will complete before
   the result promise returns)"
  ([router event]
   (router/dispatch-sync router event))

  ([router init-coeffects event]
   (dispatch-sync
    router
    (add-init-coeffects event init-coeffects))))

(defn dispatch-n-sync
  "dispatch-sync a vector of events"
  ([router events]
   (router/dispatch-n-sync router events))

  ([router init-coeffects events]
   (dispatch-n-sync
    router
    (map #(add-init-coeffects % init-coeffects)
         events))))

(defn repl-dispatch-sync->coeffects
  "return the coeffects for an event

   processes all the :enter interceptor fns of the event's chain,
   but skips the event-handler and any :leave or :error fns

   n - the number of handlers to drop from he end of
       the interceptor chain. default 1"
  ([n router event]
   (router/dispatch-sync
    router
    (assoc
     (events/coerce-extended-event event)

     schema/a-frame-event-modify-interceptor-chain
     (partial modify-interceptors-for-coeffects n))))
  ([router event]
   (repl-dispatch-sync->coeffects 1 router event)))

(defn reg-global-interceptor
  "register a global interceptor for the router instance - all events
   dispatched to this router will have global interceptors prepended to
   the interceptor chain declared with the handler"
  ([router interceptor]
   (router/reg-global-interceptor router interceptor)))

(defn clear-global-interceptors
  "clear all or one global interceptors"
  ([router]
   (router/clear-global-interceptors router))
  ([router id]
   (router/clear-global-interceptors router id)))

(defn reg-event-fx
  "register an event-handler expected to return a (promise of a) seq of fx

   (<handler> cofx event) -> Promise<[{<fx-id> <fx-args>}]>

   the seq of fx will be processed sequentially - maps with multiple fx
   entries may be processed concurrently

   note that processing of the partition's queue will be suspended until the
   handler returns"
  ([id handler]
   (reg-event-fx id nil handler))
  ([id
    interceptors
    handler]
   (events/reg-event-fx id interceptors handler)))

(defn reg-event-ctx
  "register an event-handler expected to return a (promise of an) updated
   event-context

   (<handler> context) -> Promise<context>

   fx from the returned context will be processed as described in reg-event-fx"
  ([id handler]
   (reg-event-ctx id nil handler))
  ([id
    interceptors
    handler]
   (events/reg-event-ctx id interceptors handler)))

(defn clear-event
  ([]
   (events/clear-event))
  ([id]
   (events/clear-event id)))

(defn reg-fx
  "register an fx handler
   (<handler> app arg) -> Promise<*>"
  [id handler]
  (fx/reg-fx id handler))

(defn clear-fx
  ([]
   (registry/unregister-handler schema/a-frame-kind-fx))
  ([id]
   (registry/unregister-handler schema/a-frame-kind-fx id)))

(defn reg-cofx
  "register a cofx handler
   (<handler> app) -> Promise<*>
   (<handler> app arg) -> Promise<*>"
  [id handler]
  (cofx/reg-cofx id handler))

(defn inject-cofx
  ([id]
   (cofx/inject-cofx id))
  ([id value]
   (cofx/inject-cofx id value)))

(defn clear-cofx
  ([]
   (registry/unregister-handler schema/a-frame-kind-cofx))
  ([id]
   (registry/unregister-handler schema/a-frame-kind-cofx id)))

(defn ->interceptor
  "TODO impl"
  [{_id :id
    _before :before
    _after :after}])

(defn get-coeffect
  "TODO impl"
  ([_context])
  ([_context _key])
  ([_context _key _not-found]))

(defn assoc-coeffect
  "TODO impl"
  [_context _key _value])

(defn get-effect
  "TODO impl"
  ([_context])
  ([_context _key])
  ([_context _key _not-found]))

(defn assoc-effect
  "TODO impl"
  [_context _key _value])

(defn enqueue
  "TODO impl"
  [_context _interceptors])
