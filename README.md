# a-frame

[![Build Status](https://github.com/yapsterapp/a-frame/actions/workflows/clojure.yml/badge.svg)](https://github.com/yapsterapp/a-frame/actions)
[![Clojars Project](https://img.shields.io/clojars/v/com.github.yapsterapp/a-frame.svg)](https://clojars.org/com.github.yapsterapp/a-frame)
[![cljdoc badge](https://cljdoc.org/badge/com.github.yapsterapp/a-frame)](https://cljdoc.org/d/com.github.yapsterapp/a-frame)

A-frame started life as a port of the [re-frame](https://github.com/day8/re-frame)
event and effect handling machinery to the async domain. It has developed
to become even more data-driven, and to give greater control
over event processing.

Logging and debugging are extremely transparent and,
because every stage of event-processing is data-driven, it's easy to split
different parts of the process over different execution environments - e.g.
different Kafka Streams apps.

## why?

Everyone tells you to keep your side-effecting code minimal and and
away from your pure code. A-frame helps you to do it. It's not as posh as a
freer monad based thing, but it is extremely data-driven, easy to understand,
and easy to observe.

## simple data

The term "simple" data is used below - it means data containing no opaque
objects - i.e. no functions or other opaque types. Roughly anything that can
easily be serialised and deserialised to/from EDN or JSON.

## how

A-frame uses a similar event-processing model to re-frame - `events`
are usually handled in a 3 stage process:

``` text
[gather coeffects] -> [handle event] -> [process effects]
```

As in re-frame, this process is implemented with an interceptor chain. Unlike
re-frame, the a-frame interceptor chain is:
* asynchronous - the `:enter`, `:leave` and `:error` fns in any interceptor 
  may return a promise of their result.
* fully data-driven - the interceptor chains themselves are described by 
  simple data. Since `events`, `coeffects` and `effects` are also simple data,
  the entire state of the interceptor chain at any point is fully serialisable.

A typical event-handler interceptor chain looks like this:

``` text
-> [enter:               ] -> [enter: coeffect-a] -> [enter: coeffect-b] -> [enter: handle-event] --|
<- [leave: handle-effects] <- [leave:           ] <- [leave:           ] <- [leave:             ] <-|
```

The final interceptor in the chain contains the `event-handler`.
An `event-handler` is always a pure function with no side-effects. Prior
interceptors contain `coeffect` or `effect` handlers, which may have
side-effects and may return a promise of their result.

## events

Events are usually simple maps describing something that happened. An event
map must have an `:a-frame/id` key, which describes the type of the
event and will be used to find a handler for processing the event.

Events may also be simple vectors of `[<id> ...]`, as they generally are
in re-frame. This form is less preferred because it makes literal
paths referencing data in the events harder to read.

Event handler functions have a signature:

`(fn [<coeffects> <event>])`

## coeffects

Coeffects are a simple data map representing
inputs gathered from the environment and required by the event-handler.
A particular event handler has a chain of coeffect handlers, each of
which identifies a particular coeffect handler by keyword.

Coeffect handler functions have a signature:

`(fn ([<app> <coeffects>]) ([<app> <coeffects> <data>]))`

The arity providing the `<data>` arg allows data gathered from previous
coeffects and the event to be given to the coeffect handler.

## effects

Effects are a simple datastructure describing outputs from the event
handler. Effects are either a map of `{<effect-key> <effect-data>}`, indicating
concurrent processing of offects, or a vector of such maps - requiring
sequential processing. The `<effect-key>` keywords are used to
find a handler for a particular effect.

Effect handler functions have a signature:

`(fn [<app> <effect-data>] )`

## simple example

This example defines a `::get-foo` event, to service some imaginary
`GET /foo` API, which uses a `::load-foo` cofx
to load the object and then returns the loaded object as an `:api/response`
effect.

``` clojure
(require '[a-frame.core :as af])
(require '[a-frame.std-interceptors :as af.stdintc])
(require '[a-frame.multimethods :as mm])
(require '[malli.core :as m])

(af/reg-cofx
  ::load-foo
  (fn [;; app context
       {api-client :api :as app}
       ;; already defined coeffects
       _coeffects
       ;; data arg
       {id :id url :url}]
     {:id (str url "/" id) :name "foo" :client api-client}))

;; since we can't resolve vars from keywords on cljs, and we don't want
;; any opaque objects in our simple data, we use a multimethod
;; to specify the schema validation in inject-validated-cofx
(defmethod mm/validate ::foo
  [_ value]
  (m/validate
    [:map [:id :string] [:name :string]]
    value))

(af/reg-event-fx
  ::get-foo

  ;; inject the ::load-foo cofx with an arg pulled from
  ;; the event and other cofx, validate the value
  ;; conforms to schema ::foo
  [(af/inject-validated-cofx
    ::load-foo
    {:id #af/event-path ::foo-id
     :url #af/cofx-path [:config :api-url]}
    ::foo)]

  (fn [{foo ::load-foo :as coeffects}
       event]
       ;; uncomment this throw to see error reporting
       ;; (throw (ex-info "boo" {}))
    {:api/response {:foo foo}}))

(def router (af/create-router
              ;; app context for opaque objects like network clients
              {:api ::api}
              ;; global interceptors are prepended to every event's
              ;; interceptor-chain
              {:a-frame.router/global-interceptors
               af.stdintc/minimal-global-interceptors}))

(def r (af/dispatch-sync
          router

          ;; optional initial coeffects
          {:config {:api-url "http://foo.com/api"}}

          ;; the event
          {:a-frame/id ::get-foo
           ::foo-id "1000"}))

;; unpick deref'ing a promise only works on clj
(-> @r :a-frame/effects :api/response)

;; => {:foo {:id "http://foo.com/api/1000", :name "foo" :client :user/api}}
```

of interest is the interceptor log left behind, which details exactly the
interceptor execution history:

``` clojure
(-> @r :a-frame.interceptor-chain/history)

;; =>
      [[:a-frame.std-interceptors/unhandled-error-report
        :a-frame.interceptor-chain/enter
        :a-frame.interceptor-chain/noop
        :_
        :a-frame.interceptor-chain/success]
       [{:a-frame.interceptor-chain/key :a-frame.cofx/inject-validated-cofx,
         :a-frame.cofx/id :user/load-foo,
         :a-frame.cofx/path :user/load-foo,
         :a-frame.cofx/schema :user/foo,
         :a-frame.cofx/arg
         {:id
          #a-frame.ctx/path [:a-frame/coeffects :a-frame.coeffect/event :user/foo-id],
          :url #a-frame.ctx/path [:a-frame/coeffects :config :api-url]}}
        :a-frame.interceptor-chain/enter
        :a-frame.interceptor-chain/execute
        {:id "1000", :url "http://foo.com/api"}
        :a-frame.interceptor-chain/success]
       [{:a-frame.interceptor-chain/key :a-frame.std-interceptors/fx-event-handler,
         :a-frame.std-interceptors/pure-handler-key :user/get-foo}
        :a-frame.interceptor-chain/enter
        :a-frame.interceptor-chain/execute
        :_
        :a-frame.interceptor-chain/success]
       [{:a-frame.interceptor-chain/key :a-frame.std-interceptors/fx-event-handler,
         :a-frame.std-interceptors/pure-handler-key :user/get-foo}
        :a-frame.interceptor-chain/leave
        :a-frame.interceptor-chain/noop
        :_
        :a-frame.interceptor-chain/success]
       [{:a-frame.interceptor-chain/key :a-frame.cofx/inject-validated-cofx,
         :a-frame.cofx/id :user/load-foo,
         :a-frame.cofx/path :user/load-foo,
         :a-frame.cofx/schema :user/foo,
         :a-frame.cofx/arg
         {:id
          #a-frame.ctx/path [:a-frame/coeffects :a-frame.coeffect/event :user/foo-id],
          :url #a-frame.ctx/path [:a-frame/coeffects :config :api-url]}}
        :a-frame.interceptor-chain/leave
        :a-frame.interceptor-chain/noop
        :_
        :a-frame.interceptor-chain/success]
       [:a-frame.std-interceptors/unhandled-error-report
        :a-frame.interceptor-chain/leave
        :a-frame.interceptor-chain/noop
        :_
        :a-frame.interceptor-chain/success]]
```

each log entry has the form:

`[<interceptor-spec> <interceptor-fn> <action> <data-arg> <outcome>]`

so looking at the second entry, which refers to the `::load-foo` cofx:

``` clojure
[{:a-frame.interceptor-chain/key :a-frame.cofx/inject-validated-cofx,
  :a-frame.cofx/id :user/load-foo,
  :a-frame.cofx/path :user/load-foo,
  :a-frame.cofx/schema :user/foo,
  :a-frame.cofx/arg
  {:id
   #a-frame.ctx/path [:a-frame/coeffects :a-frame.coeffect/event :user/foo-id],
   :url #a-frame.ctx/path [:a-frame/coeffects :config :api-url]}}
 :a-frame.interceptor-chain/enter
 :a-frame.interceptor-chain/execute
 {:id "1000", :url "http://foo.com/api"}
 :a-frame.interceptor-chain/success]
```

both the specification of the cofx data arg `:a-frame.cofx/arg`
and the resolved `<data-arg>` can be seen.

## error handling and resumption

Whenever an error occurs during interceptor-chain processing the
following things happen:
* the current operation is halted
* the causal exception is wrapped in an `ex-info` with a full
  description of the state of the interceptor-chain when the error happened,
* the rest (if any) of the queue of interceptors is discarded
* the stack of entered interceptors is unwound, calling `error` instead
  of `leave`, until either the error is handled or there are no remaining
  interceptors on the stack.
* if the error was handled, unwinding proceeds with `leave`
* if the error was not handled the descriptive `ex-info` is thrown

Both the minimal global interceptors and the default global interceptors
include the `a-frame.std-interceptors/unhandled-error-report` interceptor
which logs a human-readable report on the error, and re-throws the
informative `ex-info`.

The `ex-info` includes the full interceptor-context after the error finished
processing, so the causal exception along with the
`:a-frame.interceptor-chain/history` key can be inspected for clues as to
what went wrong. The interceptor-context from just before the error
occured is also included in the `:a-frame.interceptor-chain/resume` key - this
is called the "resume-context".

It is possible to try re-executing the problematic operation either
by supplying the `ex-info` or the resume-context to the `resume` fn:

`(a-frame.interceptor-chain/resume
   <app-ctx>
   <a-frame-router>
   <a-frame-ex-info-or-resume-context>)`

Since the resume-context is just simple data, the operation can be resumed
in a different VM or even machine from that where
the original failure happened - as long as any data references in the resume
context are resolvable.

## effects now or later

The `:a-frame.fx/do-fx` interceptor will handle all effects. It is prepended to
every event's interceptor-chain by the default global interceptors
`a-frame.std-interceptors/default-global-interceptors`.

If you don't want to handle effects immediately, perhaps because you want to
write the effects to a Kafka topic or db table for later handling, then you
can specify the minimal global interceptors
`a-frame.std-interceptors/minimal-global-interceptors` which will do nothing
with effects generated by the event handler.

If you want something in-between - maybe handling some effects immediately, and
leaving some for later, then you could specify a custom interceptor.

## logging

Logging can be difficult with asynchronous operations - stack traces get
erased and dymamic variables don't work reliably, so when multiple
operations are proceeding concurrently it can be difficult to narrow a
log stream to just the lines relating to a single logical operation.

A-frame provides a `set-log-context` interceptor, which adds a log
context value into the interceptor chain. The logging macros in
`a-frame.log` can then be used to log with context.

`taoensso.timbre` is currently used for logging, since it's the only common
clojure/script logging library which supports logging with context.

You can call `a-frame.log.timbre/configure-timbre` to add an output-fn
to timbre's println appender which will print the context value, leading
to log entries like this one produced by the
`a-frame.std-interceptors/unhandled-error-report` interceptor:

``` text
2023-05-23T11:12:53.852Z ERROR [a-frame.std-interceptors:168] [ForkJoinPool.commonPool-worker-15] [id:c35eb490-f95a-11ed-b7a0-ccb8a4033a35] - a-frame unhandled error:
```

the context value - in this case `[id:c35eb490-f95a-11ed-b7a0-ccb8a4033a35]` -
will be present on all log entries for the interceptor chain, no matter that
individual interceptor functions are executed on different threads.

## further work

- Support for OpenTelemetry tracing and logging would make sense, maybe via
  [clj-otel](https://github.com/steffan-westcott/clj-otel)
- the `dispatch-sync` fx can model tail-recursion, but a `dispatch-sync` cofx
  could be used to model regular recursion, returning a result to the
  coeffects


<!--  TODO -->
<!--  - dispatch-* coeffects - returning a result to a path in the coeffects -->
<!--  - do-fx interceptor should add an :a-frame/effects-results key to -->
<!--    the interceptor-context with all the effect results -->
<!--  - automatic test.check property based testing for event-handlers -->
<!--    from the accumulated schema of inject-validated-cofx -->
