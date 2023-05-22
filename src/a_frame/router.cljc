(ns a-frame.router
  (:require
   [malli.core :as m]
   [malli.experimental :as mx]
   [promesa.core :as pr]
   [promisespromises.error :as err]
   [promisespromises.promise :as prpr]
   [promisespromises.stream :as stream]
   [promisespromises.stream.transport :as stream.transport]
   [a-frame.schema :as schema]
   [a-frame.events :as events]
   [a-frame.std-interceptors :as std-interceptors]
   [taoensso.timbre :refer [debug info warn error]]))

;; use a record so we can
;; override the print-method to hide the app-context
;; for more readable error messages
(defrecord AFrameRouter [])

#?(:clj
   (defmethod print-method AFrameRouter [x writer]
     (print-method
      (into
       {}
       (assoc x schema/a-frame-app-ctx "<app-ctx-hidden>"))
      writer)))

(mx/defn create-router :- schema/Router
  [app
   {global-interceptors schema/a-frame-router-global-interceptors
    #?@(:clj [executor schema/a-frame-router-executor])
    buffer-size schema/a-frame-router-buffer-size
    :or {buffer-size 100}
    :as opts}]

  (merge
   (->AFrameRouter)
   opts
   {schema/a-frame-app-ctx app

    schema/a-frame-router-global-interceptors
    (or global-interceptors
        std-interceptors/default-global-interceptors)

    schema/a-frame-router-event-stream
    #?(:clj (stream/stream buffer-size nil executor)
       :cljs (stream/stream buffer-size nil))}))

(mx/defn dispatch
  "dispatch an Event or EventOptions"
  [{event-s schema/a-frame-router-event-stream
    :as _router} :- schema/Router
   event-or-event-options :- schema/EventOrEventOptions]

  (info "dispatch" event-or-event-options)

  (stream/put! event-s (events/coerce-event-options event-or-event-options)))

(mx/defn dispatch-n*
  [router :- schema/Router
   event-or-event-options-list  :- schema/EventOrEventOptionsList]

  ;; this schema breaks the fn annotation for some reason, so do a manual check
  (m/coerce schema/EventOrEventOptionsList event-or-event-options-list)

  #_{:clj-kondo/ignore [:loop-without-recur]}
  (pr/loop [evoces event-or-event-options-list]
    (let [[evoce & rest-evoces] evoces]
      (prpr/handle-always
       (dispatch router evoce)
       (fn [_ e]
         (cond
           (some? e)
           (err/wrap-uncaught e)

           (not-empty rest-evoces)
           #_{:clj-kondo/ignore [:invalid-arity]}
           (pr/recur rest-evoces)

           :else true))))))

(defn dispatch-n
  "dispatch a seq of Events or EventOptions in a backpressure sensitive way"
  [router
   event-or-event-options-list]
  (pr/let [r (dispatch-n* router event-or-event-options-list)]
    (err/unwrap r)))

(mx/defn handle-event
  [{app schema/a-frame-app-ctx
    global-interceptors schema/a-frame-router-global-interceptors
    :as router} :- schema/Router
   catch? :- :boolean
   event-options :- schema/EventOptions]

  ;; (warn "handle-event" event-options)

  (let [handle-opts {schema/a-frame-app-ctx app
                     schema/a-frame-router router

                     schema/a-frame-router-global-interceptors
                     global-interceptors}]
    (if catch?
      (prpr/catch-always
       (events/handle handle-opts event-options)
       (fn [err]
         (warn err "handle-event")

         ;; returning an error as a value causes a
         ;; rejected promise on js - so we wrap the error
         ;; in a type which marks it as having been caught
         ;; TODO this might have changed with promesa now
         ;; using its own promise impl
         (err/wrap-caught err)))

      (events/handle handle-opts event-options))))

(mx/defn handle-event-stream
  "handle a regular, infinite, event-stream"
  [{event-s schema/a-frame-router-event-stream
    :as router} :- schema/Router]

  (->> event-s
       (stream/map
        (partial handle-event router true))
       (stream/realize-each)
       (stream/count
        ::handle-event-stream)))

(mx/defn handle-sync-event-stream
  "handle events off of the stream until the stream is empty,
   at which point return the interceptor context of the
   very first event off of the stream

   letting errors propagate out of the loop currently causes crashes on
   cljs (cf: stream.operations/reduce) so we catch errors
   inside the loop and wrap them in UncaughtErrorWrapper for
   rethrowing outside the loop"
  [{tmp-event-s schema/a-frame-router-event-stream
    :as tmp-router} :- schema/Router]

  (let [rv-a (atom nil)]

    #_{:clj-kondo/ignore [:loop-without-recur]}
    (pr/loop []

      (prpr/handle-always

       ;; since handle-event parks for events to be fully handled,
       ;; we know that, if the stream is empty, then
       ;; there were no further dispatches and we are done
       (stream/take! tmp-event-s ::default 0 ::timeout)

       (fn [router-ev err]

         (cond

           (some? err)
           (do
             (stream/close! tmp-event-s)
             (err/wrap-uncaught err))

           (nil? (#{::default ::timeout} router-ev))
           (prpr/handle-always

            (handle-event tmp-router false router-ev)

            (fn [r ierr]

              (if (some? ierr)
                (do
                  (stream/close! tmp-event-s)
                  (err/wrap-uncaught ierr))

                (do
                  (swap!
                   rv-a
                   (fn [[_rv :as rv-wrapper] nv]
                     (if (nil? rv-wrapper)
                       [nv]
                       rv-wrapper))
                   r)

                  #_{:clj-kondo/ignore [:invalid-arity]}
                  (pr/recur)))))

           ;; tmp-event-s is empty - close and return
           :else
           (do
             (stream/close! tmp-event-s)
             (let [[rv] @rv-a]
               rv))))))))

(mx/defn dispatch-sync
  "puts the event-v on to a temporary stream,
   handles events from the stream and return
   when the stream is empty.

   returns Promise<interceptor-context> from the handling of
   the event-v, so dispatch-sync can be called to handle
   an event locally, and then extract a result from the
   interceptor context

   errors at any point during the handling of the event-v or
   any dispatches resulting from it will propagate back to
   the caller - if the caller was itself an event then the
   handling of that event will fail"
  [{app schema/a-frame-app-ctx
    :as router} :- schema/Router
   event-or-event-options :- schema/EventOrEventOptions]

  ;; (prn "DISPACTCH-SYNC:start" event-or-event-options)

  ;; create a temp event-stream, with same buffer-size
  ;; and executor as the original
  (pr/let [{tmp-event-s schema/a-frame-router-event-stream
            :as tmp-router} (create-router
                             app (dissoc router schema/a-frame-router))

           _ (stream/put!
              tmp-event-s
              (events/coerce-event-options event-or-event-options))

           r (handle-sync-event-stream tmp-router)]

    ;;(info "dispatch-sync" event-or-event-options)

    ;; unwrap any wrapped exception (throwing if it was an UncaughtErrorWrapper)
    (err/unwrap r)))

(mx/defn dispatch-n-sync
  "puts events onto a temporary stream, handles events from
   the stream, and returns when the stream is empty"
  [{app schema/a-frame-app-ctx
    :as router} :- schema/Router

   event-or-event-options-list ;; :- schema/EventOrEventOptionList
   ]

  (let [;; the schema breaks the fn annotation for some reason, so
        ;; do a manual check
        _ (m/coerce schema/EventOrEventOptionsList event-or-event-options-list)

        event-options-list (map events/coerce-event-options
                                event-or-event-options-list)]
    ;; create a temp event-stream, with same buffer-size
    ;; and executor as the original
    (pr/let [{tmp-event-s schema/a-frame-router-event-stream
              :as tmp-router} (create-router
                               app (dissoc router schema/a-frame-router))

             ;; using transport/put-all! rather than stream/put-all! so
             ;; we don't put a StreamChunk - since we can't incrementally
             ;; take! from StreamChunks during processing
             _ (stream.transport/put-all! tmp-event-s event-options-list)

             r (handle-sync-event-stream tmp-router)]

      ;; (info "dispatch-n-sync" event-or-event-options-list)

      (err/unwrap r))))

(mx/defn run-a-frame-router
  [router :- schema/Router]
  (handle-event-stream
   router))

(mx/defn stop-a-frame-router
  [{event-s schema/a-frame-router-event-stream
    :as _router} :- schema/Router]
  (info "closing a-frame")
  (stream/close! event-s))
