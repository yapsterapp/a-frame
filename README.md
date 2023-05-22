# a-frame

[![Build Status](https://github.com/yapsterapp/a-frame/actions/workflows/clojure.yml/badge.svg)](https://github.com/yapsterapp/a-frame/actions)
[![Clojars Project](https://img.shields.io/clojars/v/com.github.yapsterapp/a-frame.svg)](https://clojars.org/com.github.yapsterapp/a-frame)
[![cljdoc badge](https://cljdoc.org/badge/com.github.yapsterapp/a-frame)](https://cljdoc.org/d/com.github.yapsterapp/a-frame)


TODO - much documentation expansion

A-frame was originally a port of the [re-frame](https://github.com/day8/re-frame)
event and effect handling machinery to the async domain. It has developed
to become even more data-driven than re-frame, making logging and debugging
extremely transparent.

## why?

Everyone says you should keep your side-effecting and pure code apart. A-frame
helps you to do it. it's not as posh as a freer monad based thing, but
it is easy to understand, and easy to observe.

## simple data

The term "simple data" is used below - it means data containing no opaque
objects - i.e. no functions or other opaque types. Roughly anything that can
easily be serialised and deserialised to/from EDN or JSON.

## how

A-frame uses roughly the same event-processing model as re-frame - `events`
are handled in a 3 stage process:

``` text
[gather coeffects] -> [handle event] -> [process effects]
```

Like re-frame, this process is implemented with an interceptor chain. Unlike
re-frame, the a-frame interceptor chain is:
* asynchronous - the `:enter` or `:leave` fns in any interceptor may return a
  promise of their result.
* data-driven - interceptor chains are themselves described by simple data.
  Since `events`, `coeffects` and `effects` are also simple data, the state of
  the interceptor chain at any point is fully serialisable.

A typical event-handler interceptor chain looks like this:

``` text
-> [enter:               ] -> [enter: coeffect-a] -> [enter: coeffect-b] -> [enter: handle-event] --|
<- [leave: handle-effects] <- [leave:           ] <- [leave:           ] <- [leave:             ] <-|
```

The final interceptor in the chain contains the `event-handler`.
An `event-handler` is always a pure function with no side effects. Prior
interceptors contain `coeffect` or `effect` handlers, which may have
side-effects and may return a promise of their result.

## events

Events are simple maps describing something that happened. An event
map must have an `:a-frame/id` key, which describes the type of the
event and will be used to find a handler for processing the event.

Events can also be simple vectors of `[<id> ...]`, as they generally are
in re-frame, but this is less preferred because it makes literal
paths referencing data in the events less easy to understand.

Event handler functions have a signature:

`(fn [<coeffects> <event>])`

## coeffects

Coeffects are a simple data map representing
inputs gathered from the environment and required by the event-handler.
A particular event handler has a chain of coeffect handlers, each of
which identifies a particular coeffect handler by keyword.

Coeffect handler functions have a signature:

`(fn ([<app> <coeffects>]) ([<app> <coeffects> <data>]))`

## effects

Effects are a datastructure describing outputs from the event
handler. Effects are either a map of `{<effect-key> <effect-data>}`, indicating
concurrent processing of offects, or a vector of such maps - requiring
sequential processing. The `<effect-key>` keywords are used to
find a handler for a particular effect.

Effect handler functions have a signature:

`(fn [<app> <effect-data>] )`

## simple example

This example defines a `::get-foo` event, which uses a `::load-foo` cofx 
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

;; since we can't deref vars on cljs, and we don't want 
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
  ;; conforms to schema ::foo and set at path ::the-foo 
  ;; in the coeffects
  [(af/inject-validated-cofx
    ::load-foo
    {:id #a-frame.cofx/event-path [::foo-id]
     :url #a-frame.cofx/path [:config :api-url]}
    ::foo
    ::the-foo)]

  (fn [{foo ::the-foo :as coeffects}
       event]
    {:api/response {:foo foo}}))

(def router (af/create-router
              ;; app context for opaque objects like network clients
              {:api ::api}
              ;; global interceptors are prepended to every event chain
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

<!--  TODO -->
<!--  - dispatch-* coeffects - returning a result to a path in the coeffects -->
<!--  - do-fx interceptor should add an :a-frame/effects-results key to -->
<!--    the interceptor-context with all the effect results -->
<!--  - automatic test.check property based testing for event-handlers -->
<!--    from the accumulated schema of inject-validated-cofx -->
