# a-frame

[![Build Status](https://github.com/yapsterapp/a-frame/actions/workflows/clojure.yml/badge.svg)](https://github.com/yapsterapp/a-frame/actions)
[![Clojars Project](https://img.shields.io/clojars/v/com.github.yapsterapp/a-frame.svg)](https://clojars.org/com.github.yapsterapp/a-frame)
[![cljdoc badge](https://cljdoc.org/badge/com.github.yapsterapp/a-frame)](https://cljdoc.org/d/com.github.yapsterapp/a-frame)

TODO - much documentation expansion

A-frame started life as a port of the [re-frame](https://github.com/day8/re-frame)
event and effect handling machinery to the async domain. It has developed
to become even more data-driven than re-frame, and to give greater control
over the processing of events.

Logging and debugging are extremely transparent and,
because every stage of event-processing is data-driven, it's easy to split
different parts of the process over different execution environments - e.g.
different Kafka Streams apps.

## why?

Everyone says you should keep your side-effecting and pure code apart. A-frame
helps you to do it. it's not as posh as a freer monad based thing, but
it is easy to understand, and easy to observe.

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

As with re-frame, this process is implemented with an interceptor chain, but unlike
re-frame, the a-frame interceptor chain is:
* asynchronous - the `:enter` or `:leave` fns in any interceptor may return a
  promise of their result
* data-driven - the interceptor chains themselves are described by simple data.
  Since `events`, `coeffects` and `effects` are also simple data, the state of
  the interceptor chain at any point is fully serialisable

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

Events are simple maps describing something that happened. An event
map must have an `:a-frame/id` key, which describes the type of the
event and will be used to find a handler for processing the event.

Events may also be simple vectors of `[<id> ...]`, as they generally are
in re-frame, but this is less preferred because it makes literal
paths referencing data in the events harder to understand.

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

note the second entry:

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

each log entry has the form:

`[<interceptor-spec> <interceptor-fn> <action> <data-arg> <outcome>]`

so you can see both the specification of the cofx data arg `:a-frame.cofx/arg`
and the resolved `<data-arg>`

## error handling

Whenever an error occurs during interceptor-chain processing the
following things happen
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

The `ex-info` includes the full interceptor-context at the time of the error
in its `ex-data`, so the causal exception along with the
`:a-frame.interceptor-chain/history` key can be inspected for clues as to
what went wrong.

It's also possible to try re-executing the problematic operation with, either
by supplying the `ex-info` directly to the `resume` fn

`(a-frame.interceptor-chain/resume
   <app-ctx>
   <a-frame-router>
   <a-frame-ex-info>)`

or by copying a resume context from the `:a-frame.interceptor-chain/resume` key
in the `ex-data` and giving that to the `resume` fn

`(a-frame.interceptor-chain/resume
   <app-ctx>
   <a-frame-router>
   <resume-ctx>)`

Since the interceptor-chain and context are all simple data, the `resume`
context can be resumed in a different VM or even machine from that where
the original failure happened - as long as data references in the resume
context are available.


## effects now or later

The `:a-frame.fx/do-fx` interceptor will handle all effects. It is prepended to
every event's interceptor-chain by the default global interceptors
`a-frame.std-interceptors/default-global-interceptors`.

If you don't want to handle effects immediately, perhaps because you want to
write the effects to a Kafka topic or db table for later handling, then you
can specify the minimal global interceptors
`a-frame.std-interceptors/minimal-global-interceptors` which will do nothing
with effects generated by the event handler.

If you want something inbetween - maybe handling some effects immediately, and
leaving some for later, then you could specify a custom interceptor.

<!--  TODO -->
<!--  - dispatch-* coeffects - returning a result to a path in the coeffects -->
<!--  - do-fx interceptor should add an :a-frame/effects-results key to -->
<!--    the interceptor-context with all the effect results -->
<!--  - automatic test.check property based testing for event-handlers -->
<!--    from the accumulated schema of inject-validated-cofx -->
