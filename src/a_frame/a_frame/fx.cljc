(ns prpr3.a-frame.fx
  (:require
   [malli.experimental :as mx]
   [promesa.core :as pr]
   [prpr3.promise :as prpr]
   [prpr3.error :as err]
   [prpr3.a-frame.schema :as schema]
   [prpr3.a-frame.registry :as registry]
   [prpr3.a-frame.events :as events]
   [prpr3.a-frame.router :as router]
   [prpr3.a-frame.interceptor-chain :as interceptor-chain]
   [taoensso.timbre :refer [warn]]))

(defn reg-fx
  "register an fx which will be called with:
   (<fx-handler> app fx-data)"
  [id handler]
  (let [ctx-handler (fn [{app schema/a-frame-app-ctx
                         :as _context}
                        fx-data]
                      (handler app fx-data))]
    (registry/register-handler schema/a-frame-kind-fx id ctx-handler)))

(defn reg-fx-ctx
  "register an fx which will be called with:
  (<fx-handler> ctx fx-data)

  note that the fx-handler will receive the interceptor ctx,
  so can access values from the ctx (e.g. to dispatch to the event-stream),
  but it cannot modify the ctx"
  [id handler]
  (registry/register-handler schema/a-frame-kind-fx id handler))

(defn do-single-effect
  "return a promise of the result of the effect"
  [context fx-id fx-data]
  (let [handler (registry/get-handler schema/a-frame-kind-fx fx-id)]
    (if (some? handler)
      (pr/let [r (handler context fx-data)]
        {fx-id r})
      (throw
       (err/ex-info
        ::no-fx-handler
        {:id fx-id
         :data fx-data})))))

(defn do-map-of-effects
  [context effects]

  ;; do individual effects from the map concurrently
  (pr/let [result-ps (for [[id data] effects]
                       (do-single-effect context id data))

           all-results (pr/all result-ps)]

    (apply merge all-results)))

(defn do-seq-of-effects*
  [context effects]

  ;; do the seq-of-maps-of-effects in strict sequential order
  ;;
  #_{:clj-kondo/ignore [:loop-without-recur]}
  (pr/loop [results-effects [[] effects]]
    (let [[results [first-map-fx & rest-map-fx]] results-effects]
      (prpr/handle-always
       (do-map-of-effects context first-map-fx)
       (fn [r err]
         (cond
           (some? err)
           (err/wrap-uncaught err)

           (empty? rest-map-fx)
           [(conj results r) []]

           :else
           (pr/recur [(conj results r) rest-map-fx])))))))

(defn do-seq-of-effects
  [context effects]
  (pr/let [r (do-seq-of-effects* context effects)]

    ;; unpick just the results (or throw an error)
    (-> r
        (err/unwrap)
        first)))

(def do-fx-interceptor
  "an interceptor which will execute all effects from the
  :a-frame/effects key of the interceptor context "
  {::interceptor-chain/name ::do-fx

   ::interceptor-chain/leave
   (fn do-fx-leave
     [{_app schema/a-frame-app-ctx
       effects schema/a-frame-effects
       :as context}]

     (pr/let [_ (if (map? effects)
                  (do-map-of-effects context effects)
                  (do-seq-of-effects context effects))]
       context))})

(interceptor-chain/register-interceptor
 ::do-fx
 do-fx-interceptor)

(defn apply-transitive-coeffects?
  [default-transitive-coeffects?
   {transitive-coeffects? schema/a-frame-event-transitive-coeffects?
    :as _extended-event}]
  (if (some? transitive-coeffects?)
    transitive-coeffects?
    default-transitive-coeffects?))

(defn xev-with-all-coeffects
  "take coeffects from the context and merge
   with any specified on the event to give the
   final coeffects "
  [{ctx-coeffects schema/a-frame-coeffects
    :as _context}
   default-transitive-coeffects?
   event-or-extended-event]
  (let [{_ev schema/a-frame-event
         ev-coeffects schema/a-frame-coeffects
         :as extended-event} (events/coerce-extended-event
                              event-or-extended-event)
        transitive-coeffects? (apply-transitive-coeffects?
                               default-transitive-coeffects?
                               extended-event)]
    (if transitive-coeffects?
      (assoc
       extended-event
       schema/a-frame-coeffects
       (merge ctx-coeffects
              ev-coeffects))

      extended-event)))

;; standard fx

;; dispatch an event - coeffects are *not* transitive
;; by default

(mx/defn dispatch
   [{router schema/a-frame-router
          :as context}
         event :- schema/EventOrExtendedEvent]
   (router/dispatch
    router
    (xev-with-all-coeffects context false event)))

(reg-fx-ctx
 :a-frame/dispatch
 dispatch)

;; dispatch a vector of events - coeffects are *not*
;; transitive by default

(mx/defn dispatch-n
   [{router schema/a-frame-router
          :as context}
         events :- schema/EventsOrExtendedEvents]
   (router/dispatch-n
    router
    (map (partial xev-with-all-coeffects context false) events)))

(reg-fx-ctx
 :a-frame/dispatch-n
 dispatch-n)

;; dispatch an event and wait for it to be fully processed
;; before proceeding (pausing fx processing for the current
;; event, and any further processing on the main event stream)
;;
;; coeffects *are* transitive by default

(mx/defn dispatch-sync
  [{router schema/a-frame-router
    :as context}
   event :- schema/EventOrExtendedEvent]
  (router/dispatch-sync
   router
   (xev-with-all-coeffects context true event)))

(reg-fx-ctx
 :a-frame/dispatch-sync
 dispatch-sync)

;; dispatch n events, and wait for them all to be fully processed
;; before proceeding (pausing fx processing for the current
;; event, and any further processing on the main event stream)
;;
;; coeffects *are* transitive by default

(mx/defn dispatch-n-sync
  [{router schema/a-frame-router
    :as context}
   events :- schema/EventsOrExtendedEvents]
  (router/dispatch-n-sync
   router
   (map (partial xev-with-all-coeffects context true) events)))

(reg-fx-ctx
 :a-frame/dispatch-n-sync
 dispatch-n-sync)
