(ns prpr3.a-frame.std-interceptors
  (:require
   [#?(:clj clj-uuid :cljs cljs-uuid-utils.core) :as uuid]
   [#?(:clj clojure.pprint :cljs cljs.pprint) :as pprint]
   [prpr3.a-frame.schema :as schema]
   [prpr3.a-frame.registry :as registry]
   [prpr3.a-frame.interceptor-chain :as interceptor-chain]
   [prpr3.a-frame.log :as af.log]
   [taoensso.timbre :refer [info warn error]]))

(defn fx-handler->interceptor
  [pure-handler-key]
  {::interceptor-chain/key ::fx-event-handler

   ::interceptor-chain/data
   {::interceptor-chain/enter-data pure-handler-key}})

(def fx-event-handler-interceptor
  {::interceptor-chain/name ::fx-event-handler
   ::interceptor-chain/enter
   (fn fx-handler-fn
     [context
      pure-handler-key]
     (let [handler-fn (registry/get-handler
                       schema/a-frame-kind-event-pure
                       pure-handler-key)

           {{event schema/a-frame-coeffect-event
             :as coeffects} schema/a-frame-coeffects} context

           effects (af.log/with-a-frame-log-context
                     context
                     (handler-fn coeffects event))]

       (assoc context schema/a-frame-effects effects)))})

(interceptor-chain/register-interceptor
 ::fx-event-handler
 fx-event-handler-interceptor)

(defn ctx-handler->interceptor
  [pure-handler-key]
  {::interceptor-chain/key ::ctx-event-handler

   ::interceptor-chain/data
   {::interceptor-chain/enter-data pure-handler-key}})

(def ctx-event-handler-interceptor
  {::interceptor-chain/name ::ctx-event-handler

   ::interceptor-chain/enter
   (fn ctx-handler-fn
     [context
      pure-handler-key]
     (let [handler-fn (registry/get-handler
                       schema/a-frame-kind-event-pure
                       pure-handler-key)]

       (handler-fn context)))})

(interceptor-chain/register-interceptor
 ::ctx-event-handler
 ctx-event-handler-interceptor)

(def extract-coeffects-interceptor
  "an interceptor to extract coeffects. must be at the head of the
   chain, because this breaks the interceptor-context"
  {::interceptor-chain/key ::extract-coeffects

   ::interceptor-chain/leave
   (fn [ctx]
     (info "extract-coeffects-interceptor")
     (get ctx schema/a-frame-coeffects))})

(interceptor-chain/register-interceptor
 ::extract-coeffects
 extract-coeffects-interceptor)

(defn interceptor->remove-leave-error
  [interceptor-spec]
  {::interceptor-chain/key ::remove-leave-error-proxy
   ::interceptor-chain/data {::interceptor-chain/enter-data interceptor-spec}})

(def remove-leave-error-proxy-interceptor
  "a proxy interceptor which removes ::leave and ::error
   handlers from the proxied interceptor

   to do this the proxy interceptor defines only an
   ::enter fn and accepts an InterceptorSpec
   for the proxied interceptor as enter-data"
  {::interceptor-chain/name ::remove-leave-error-proxy

   ::interceptor-chain/enter
   (fn [context
       interceptor-spec]

     (interceptor-chain/maybe-execute-interceptor-fn
      ::interceptor-chain/enter
      interceptor-spec
      context
      nil))})

(interceptor-chain/register-interceptor
 ::remove-leave-error-proxy
 remove-leave-error-proxy-interceptor)

(defn modify-interceptors-for-coeffects
  "an interceptor modifier which
    - removes n (default 1) interceptors completely from the and of the chain
    - removes all :leave and :error fns from the remaining interceptors
    - inserts a new interceptor at the beginning of the chain which
        extracts coeffects from the context"
  [n interceptors]
  (info "modify-interceptors-for-coeffects - dropping:" n)
  (let [interceptors (vec interceptors)
        cnt (count interceptors)
        interceptors (subvec interceptors 0 (max (- cnt n) 0))
        interceptors (mapv interceptor->remove-leave-error interceptors)]

    (into
     [::extract-coeffects]
     interceptors)))

(defn error-context-report
  [err]
  (let [{{err-ctx-queue ::interceptor-chain/queue
          err-ctx-stack ::interceptor-chain/stack
          err-ctx-history ::interceptor-chain/history
          :as err-ctx} ::interceptor-chain/context
         fn-key ::interceptor-chain/interceptor-fn-key
         :as _exd} (ex-data err)]

    (str "a-frame unhandled error:\n\n"

         "the failure happened in the <fn-key> function of either the head "
         "(first element) of the queue, OR the top (first element) of the stack"

         "\n\nfn-key: " (pr-str fn-key)
         "\n\nqueue:\n" (with-out-str
                         (pprint/pprint err-ctx-queue))
         "\n\nstack:\n" (with-out-str
                         (pprint/pprint err-ctx-stack))

         "\n\nhistory:\n"
         (with-out-str
           (pprint/pprint err-ctx-history))

         "\n\nresume context:\n\n"
         (with-out-str
           (pprint/pprint err-ctx)))))

(def unhandled-error-report-interceptor
  "an interceptor which logs a useful report for an error"
  {::interceptor-chain/name ::unhandled-error-report

   ::interceptor-chain/error
   (fn [context
       err]
     (let [org-err (interceptor-chain/unwrap-original-error err)]

       (af.log/error
        context
        org-err
        (error-context-report err)))

     ;; don't alter the context at all, we're just reporting
     context)})

(interceptor-chain/register-interceptor
 ::unhandled-error-report
 unhandled-error-report-interceptor)

(defn assoc-log-context
  "add the log context into the top-level of
   the interceptor-context and into the
   coeffects - so both the interceptor-context
   and the coeffects can be used as a log-context-src
   in the a-frame.log logging macros"
  [interceptor-context log-context]
  (-> interceptor-context
      (assoc schema/a-frame-log-ctx log-context)
      (assoc-in [schema/a-frame-coeffects
                 schema/a-frame-log-ctx]
                log-context)))

(def set-log-context-interceptor
  "set a log context - either with data, or if no data
   is provided then {:id <uuid>} will be used"
  {::interceptor-chain/name ::set-log-context

   ::interceptor-chain/enter
   (fn
     ([context]
      (let [data {:id #?(:clj (uuid/v1)
                         :cljs (uuid/make-random-uuid))}]
        (info "set-log-context-interceptor" data)
        (assoc-log-context
         context
         data)))
     ([context data]
      (info "set-log-context-interceptor" data)
      (assoc-log-context
       context
       data)))})

(interceptor-chain/register-interceptor
 ::set-log-context
 set-log-context-interceptor)

(defn set-log-context
  "set a log-context based on the provided data-spec

   e.g.

   {:request-id #cofx-path [:yapster.api :request-id]}

   will cause \"[request-id:<request-id>]\" sections to
   be logged in a-frame error reports"
  [log-context-data-spec]
  {::interceptor-chain/key ::set-log-context

   ::interceptor-chain/data
   {::interceptor-chain/enter-data log-context-data-spec}})
