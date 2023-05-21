(ns a-frame.interceptor-chain
  (:require
   [malli.util :as mu]
   [malli.experimental :as mx]
   [promesa.core :as pr]
   [promisespromises.promise :as prpr]
   [promisespromises.error :as err]
   [taoensso.timbre :refer [warn error]]
   [a-frame.schema :as af.schema]
   [a-frame.registry :as registry]
   [a-frame.interceptor-chain.schema :as intc.schema]
   [a-frame.interceptor-chain.data.tag-readers]))

;; a more data-driven interceptor chain for a-frame
;;
;; the idea being that the interceptor context should be serializable,
;; and when there is an error the complete interceptor context
;; can be pretty-printed and used to resume the failed operation
;; for debugging
;;
;; interceptors are registered in the a-frame registry
;; and referenced by a keyword, much like fx and cofx
;;
;; this makes sense for a-frame, which already maintains a registry of different
;; kinds of handlers. it doesn't necessarily make sense for a general purpose
;; interceptor chain, which remains at promisespromises.interceptor-chain

;; utility fns

(def opaque-context-keys
  "keys which contain opaque data"
  #{af.schema/a-frame-app-ctx
    af.schema/a-frame-router})

(def context-keys
  "The Interceptor specific keys that are added to contexts"
  (->> intc.schema/InterceptorContext
       (mu/keys)
       (filter keyword?)
       set))

(defn dissoc-context-keys
  "Removes all interceptor related keys from `context`"
  [context]
  (apply dissoc context context-keys))

(defn sanitise-context
  "remove impure / opaque data from a context"
  [context]
  (apply dissoc context opaque-context-keys))

(defn pr-loop-context*
  "Helper fn to repeat execution of `step-fn` against `context` inside a promise
  loop.

  `step-fn` should return a tuple of either `[::break <value>]` or `[::recur
  <new context>]`. The former will terminate the loop and return `<value>`, the
  later will pass `<new context>` to the next loop iteration.

  (Note: this mainly exists to abstract away and minimise the platform specific
  aspects of handling promise loops.)"
  [context step-fn]

  #_{:clj-kondo/ignore [:loop-without-recur]}
  (pr/loop [context context]
    (prpr/handle-always
     (step-fn context)
     (fn [[t c] e]
       (cond
         (some? e) (err/wrap-uncaught e)

         (= ::break t) c

         :else
         #_{:clj-kondo/ignore [:invalid-arity]}
         (pr/recur c))))))

(defn pr-loop-context
  [context step-fn]
  (pr/let [r (pr-loop-context* context step-fn)]
    (err/unwrap r)))

(mx/defn assoc-opaque-keys
  "add the opaque keys to the interceptor context

   they are removed from reported contexts by `sanitise-context`"
  [ctx app-ctx a-frame-router]
  (merge
   ctx
   {af.schema/a-frame-app-ctx app-ctx
    af.schema/a-frame-router a-frame-router}))

(defn initiate*
  ([interceptor-chain] (initiate* {} interceptor-chain))
  ([initial-context
    interceptor-chain]

   (merge
    initial-context
    {::queue (vec interceptor-chain)
     ::stack '()
     ::history []})))

(mx/defn ^:always-validate initiate
  :- intc.schema/InterceptorContext
  "Given a sequence of [[InterceptorSpec]]s and a map of `initial-context` values,
  returns a new [[InterceptorContext]] ready to [[execute]]"
  [app-ctx
   a-frame-router
   interceptor-chain :- intc.schema/InterceptorList
   initial-context]

  (->
   initial-context
   (merge {::queue (vec interceptor-chain)
           ::stack '()
           ::history []})
   (assoc-opaque-keys app-ctx a-frame-router)))

(mx/defn ^:always-validate enqueue
  :- intc.schema/InterceptorContext
  "Adds `interceptors` to the end of the interceptor queue within `context`"
  [context :- intc.schema/InterceptorContext
   interceptors :- intc.schema/InterceptorList]
  (update context ::queue into interceptors))

(mx/defn ^:always-validate terminate
  :- intc.schema/InterceptorContext
  "Removes all queued interceptors from `context`"
  [context :- intc.schema/InterceptorContext]
  (assoc context ::queue []))

(mx/defn ^:always-validate clear-error
  :- intc.schema/InterceptorContext
  "Removes any associated `::error` from `context`"
  [context :- intc.schema/InterceptorContext]
  (dissoc context ::error))

(mx/defn ^:always-validate register-interceptor
  [interceptor-key :- :keyword
   interceptor :- intc.schema/Interceptor]
  (registry/register-handler ::interceptor interceptor-key interceptor))

(defn resolve-interceptor
  [interceptor-key]
  (let [interceptor (registry/get-handler ::interceptor interceptor-key)]
    (when (nil? interceptor)
      (throw (ex-info "no interceptor" {:interceptor-key interceptor-key})))

    interceptor))

(defn normalize-interceptor-spec
  "turns keyword interceptor-specs into maps
   {::key <interceptor-key>}"
  [interceptor-spec]
  (if (keyword? interceptor-spec)
    {::key interceptor-spec}
    interceptor-spec))

(defn wrap-interceptor-error
  [resume-context e]
  (ex-info
   "interceptor failed"
   {::resume (sanitise-context resume-context)}
   e))

(defn unwrap-resume-context-original-error
  "unwrap layers of error wrapping (in case of nested :dispatch)
   to get at the resume context and the causal exception

   returns : [<resume-context> <original-error>]"
  [e]
  (let [{resume-ctx ::resume} (ex-data e)
        cause (ex-cause e)
        {wrapped-resume-ctx ::resume} (ex-data cause)]

    (cond

      ;; there is a resume context wrapping an exception with
      ;; no resume context - it's the original
      (and (some? resume-ctx)
           (some? cause)
           (nil? wrapped-resume-ctx))
      [resume-ctx cause]

      ;; there are nested resume contexts
      (and (some? resume-ctx)
           (some? cause)
           (some? wrapped-resume-ctx))
      (unwrap-resume-context-original-error cause)

      ;; there is no resume context ¯\_(ツ)_/¯
      :else
      [nil e])))

(defn unwrap-original-error
  "unwrap layers of error wrapping (in case of nested :dispatch)
   to get at the causal exception"
  [e]
  (let [[_ cause] (unwrap-resume-context-original-error e)]
    cause))

(defn interceptor-fn-history-thunk
  "returns a [<history-entry> <interceptor-fn-thunk>]

  <history-entry> :-
    [<interceptor-spec>
     <interceptor-fn-key>
     <operation> :- ::execute | ::noop
     <data>
     <outcome> :- ::success | ::error]

  the <data> and <outcome> fields in the <history-entry> tuple
  will be added later. the <interceptor-fn-thunk> returns
  [<data> <next-context>] when it completes normally"
  [interceptor-fn-key
   interceptor-spec
   context
   error]

  (let [{interceptor-kw ::key
         :as norm-interceptor-spec} (normalize-interceptor-spec
                                     interceptor-spec)

        interceptor (resolve-interceptor interceptor-kw)]

    (if-let [f (get interceptor interceptor-fn-key)]

      (condp contains? interceptor-fn-key

        #{::enter ::leave}
        (let [thunk (fn [ctx]
                      ;; if the fn returns a vector/seq, then it's
                      ;; [next-context log], otherwise it's just
                      ;; the next-context
                      (pr/let [nx-ctx-or-nx-ctx-log (f ctx norm-interceptor-spec)]
                        (let [[nx-ctx log] (if (sequential? nx-ctx-or-nx-ctx-log)
                                             nx-ctx-or-nx-ctx-log
                                             [nx-ctx-or-nx-ctx-log])]
                          [(or log :_) nx-ctx])))]

          [[interceptor-spec interceptor-fn-key ::execute]
           thunk])

        #{::error}
        [[interceptor-spec interceptor-fn-key ::execute]
         (fn [ctx]
           (pr/let [nx-ctx-or-nx-ctx-log (f ctx norm-interceptor-spec error)]
             (let [[nx-ctx log] (if (sequential? nx-ctx-or-nx-ctx-log)
                                  nx-ctx-or-nx-ctx-log
                                  [nx-ctx-or-nx-ctx-log])]
               [(or log :_) nx-ctx])))])

      ;; no interceptor fn, so no thunk
      [[interceptor-spec interceptor-fn-key ::noop]
       nil])))

(defn maybe-execute-interceptor-fn-thunk
  [thunk
   context]
  (if (some? thunk)
    (thunk context)
    [:_ context]))

(defn maybe-execute-interceptor-fn
  "call an interceptor fn on an interceptor, resolving
   any supplied data"
  [interceptor-fn-key
   interceptor-spec
   context
   error]
  (pr/let [[_ thunk] (interceptor-fn-history-thunk
                      interceptor-fn-key
                      interceptor-spec
                      context
                      error)
           [_data-val next-ctx] (maybe-execute-interceptor-fn-thunk thunk context)]

    next-ctx))

(defn rethrow
  "wrap an error in a marker exception for rethrowing rather
   than wrapping - allows an ::error fn to rethrow an error
   without confusing resume"
  [e]
  (ex-info
   "rethrowing"
   {::rethrow e}))

(defn rethrow?
  "returns an error to rethrow or nil"
  [o]
  (let [{rethrow ::rethrow} (ex-data o)]
    rethrow))

(defn record-interceptor-error
  "wrap an exception and record the error"
  [resume-context
   next-context
   e]
  ;; (prn "record-interceptor-error" e)
  (let [rethrow (rethrow? e)]

    (-> next-context
        terminate
        (assoc
         ::error
         (if (some? rethrow)
           rethrow

           ;; add an error referencing the context,
           ;; which can be used to resume at the
           ;; point of failure
           (wrap-interceptor-error resume-context e))))))

;; processing fns

(defn after-enter-update-context
  "update a context after the enter fn has been processed"
  [{queue ::queue
    stack ::stack
    _history ::history
    :as context}
   _interceptor-fn-key
   interceptor-spec
   history-entry]

  ;; (prn "after-enter-update-context"
  ;;      _interceptor-fn-key
  ;;      interceptor-spec
  ;;      history-entry)

  (-> context
      (assoc ::queue (vec (rest queue)))
      (assoc ::stack (conj stack interceptor-spec))
      (update ::history conj history-entry)))

(mx/defn enter-next
  "Executes the next `::enter` interceptor queued within `context`, returning a
  promise that will resolve to the next [[pr-loop-context]] action to take

  TODO errors in [[resolve-data]] are not handled properly"
  [{queue ::queue
    _stack ::stack
    _history ::history
    :as context} :- intc.schema/InterceptorContext]

  (if (empty? queue)
    [::break context]

    (let [interceptor-spec (first queue)

          [history-entry thunk] (interceptor-fn-history-thunk
                                 ::enter
                                 interceptor-spec
                                 context
                                 nil)]

      (->
       (pr/handle

        (maybe-execute-interceptor-fn-thunk thunk context)

        (fn [[data-val ctx :as _succ] err]

          ;; don't move the interceptor-spec from queue->stack
          ;; until after the fn has executed - so the
          ;; interceptor-spec is visible at the head of the queue
          ;; for the fn to inspect

          (if (some? err)

            (record-interceptor-error
             context
             (after-enter-update-context
              context
              ::catch-enter
              interceptor-spec
              (into history-entry [:_ ::error]))
             err)

            (after-enter-update-context
             ctx
             ::enter
             interceptor-spec
             (into history-entry [data-val ::success])))))

       (pr/then
        (fn [{queue ::queue :as c}]
          (if (empty? queue)
            [::break c]
            [::recur c])))))))

(mx/defn ^:always-validate enter-all
  "Process the `:queue` of `context`, calling each `:enter` `fn` in turn.

  If an error is raised it is captured, stored in the `context`s `:error` key,
  and the queue is cleared (to prevent further processing.)"
  [context :- intc.schema/InterceptorContext]
  (pr-loop-context context enter-next))

(defn after-leave-update-context
  "update a context after a leave fn has been processed"
  [{stack ::stack
    _history ::history
    _error ::error
    :as context}
   interceptor-fn-key
   has-thunk?
   _interceptor-spec
   history-entry]

  ;; (prn "after-leave-update-context"
  ;;      interceptor-fn-key
  ;;      _interceptor-spec
  ;;      history-entry)

  (condp = interceptor-fn-key
    ::leave ;; normal leave fn
    (-> context
        (assoc ::stack (pop stack))
        (update ::history conj history-entry))

    ::error ;; successful error handler
    (cond-> context
      true (assoc ::stack (pop stack))
      true (update ::history conj history-entry)
      ;; only clear the error if there was a thunk which
      ;; executed normally
      has-thunk? (dissoc ::error))

    ::catch-leave ;; catch during leave fn
    (-> context
        ;; don't pop the stack - give a chance to handle
        (update ::history conj history-entry))

    ::catch-error ;; catch during error fn
    (-> context
        ;; pop the stack - had a chance to handle
        (assoc ::stack (pop stack))
        (update ::history conj history-entry))))

(mx/defn leave-next
  "Executes the next `::leave` or `::error` interceptor on the stack within
  `context`, returning a promise that will resolve to the next
  [[pr-loop-context]] action to take"
  [{stack ::stack
    _history ::history
    error ::error
    :as context} :- intc.schema/InterceptorContext]

  (if (empty? stack)
    [::break context]

    (let [interceptor-spec (peek stack)

          interceptor-fn-key (if (some? error) ::error ::leave)

          [history-entry thunk] (interceptor-fn-history-thunk
                                 interceptor-fn-key
                                 interceptor-spec
                                 context
                                 error)

          has-thunk? (some? thunk)]

      (->
       (pr/handle

        (maybe-execute-interceptor-fn-thunk thunk context)

        (fn [[data-val ctx :as _succ] err]

          ;; don't pop the interceptor-spec from the stack
          ;; until the fn has been executed - so that the
          ;; fn can introspect the interceptor-spec from
          ;; the top of the stack

          (if (some? err)

            (record-interceptor-error
             context
             (after-leave-update-context
              context
              (if (= ::leave interceptor-fn-key)
                ::catch-leave
                ::catch-error)
              false
              interceptor-spec
              (into history-entry [:_ ::error]))
             err)

            (after-leave-update-context
             ctx
             interceptor-fn-key
             has-thunk?
             interceptor-spec
             (into history-entry [data-val ::success])))))

       (pr/then
        (fn [{stack ::stack :as c}]
          (if (empty? stack)
            [::break c]
            [::recur c])))))))

(mx/defn ^:always-validate leave-all
  "Process the `::stack` of `context`, calling, in LIFO order.

  If an `::error` is present in the `context` then the `::error` handling `fn`
  of the interceptors will be called otherwise the `:leave` `fn`s will be
  called.

  Any thrown errors will replace the current `::error` with stack unwinding
  continuing from that point forwards."
  [context :- intc.schema/InterceptorContext]
  (pr-loop-context context leave-next))

;; the main interaction fn

(defn final-error-handler
  "if there is still an error after processing, throw it"
  [{err ::error
    :as ctx}]
  (if (some? err)

    (let [[resume-ctx org-err] (unwrap-resume-context-original-error err)]
      (throw

       (ex-info
        "unhandled error"
        {::context ctx
         ::resume (sanitise-context resume-ctx)}
        org-err)))

    ctx))

(mx/defn execute*
  "execute a context"
  [context :- intc.schema/InterceptorContext]

  (pr/chain
   (enter-all context)
   leave-all
   final-error-handler))

(mx/defn ^:always-validate execute
  "Returns a Promise encapsulating the execution of the given [[InterceptorContext]].

  Runs all `:enter` interceptor fns (in FIFO order) and then all `:leave` fns
  (in LIFO order) returning the end result `context` map.

  If an error occurs during execution `:enter` processing is terminated and the
  `:error` handlers of all executed interceptors are called (in LIFO order),
  with the original error wrapped in an ex-info with ex-data containing the
  at the point of failure in the ::resume key - this can be used to resume processing
  from the failure point

  If the resulting `context` _still_ contains an error after this processing it
  will be re-thrown when the execution promise is realised. "
  ([app-ctx
    a-frame-router
    interceptor-chain :- intc.schema/InterceptorList
    initial-context]
   (->> (initiate app-ctx
                  a-frame-router
                  interceptor-chain
                  initial-context)
        (execute*))))

(defn resume
  "resume a failed interceptor chain, from either
   a thrown exception or a logged resume-context"
  [app-ctx
   a-frame-router
   err-or-resume-context]
  (let [ctx (if (map? err-or-resume-context)
              err-or-resume-context
              (get (ex-data err-or-resume-context) ::resume))]

    (when (nil? ctx)
      (throw
       (ex-info
        "no resume context in ex-data"
        {:err-or-resume-context err-or-resume-context})))

    (execute*
     (assoc-opaque-keys ctx app-ctx a-frame-router))))
