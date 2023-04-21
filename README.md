# a-frame

[![Build Status](https://github.com/yapsterapp/a-frame/actions/workflows/clojure.yml/badge.svg)](https://github.com/yapsterapp/a-frame/actions)
[![Clojars Project](https://img.shields.io/clojars/v/com.github.yapsterapp/a-frame.svg)](https://clojars.org/com.github.yapsterapp/a-frame)
[![cljdoc badge](https://cljdoc.org/badge/com.github.yapsterapp/a-frame)](https://cljdoc.org/d/com.github.yapsterapp/a-frame)


TODO - much documentation expansion

A-frame is a port of the [re-frame](https://github.com/day8/re-frame)
event and effect handling machinery to the async domain. It offers a
straightforward separation of pure and side-effecting code for both
Clojure and ClojureScript.

## why?

Everyone tells you to keep your side-effecting and pure code apart. A-frame
helps you to do it.

## how

A-frame uses roughly the same event-processing model as re-frame - `events`
are handled in a 3 stage process:

``` text
[gather coeffects] -> [handle event] -> [process effects]
```

Like re-frame, this process is implemented with an interceptor chain but, unlike
re-frame, the a-frame interceptor chain is asynchronous - the `:enter` or
`:leave` fns in any interceptor may return a promise of their result.

* Events are handled by an interceptor chain
* The final interceptor in the chain is the `event-handler`
  * an `event-handler` is always a pure function with no side effects
* Prior interceptors are `coeffect` or `effect` handlers
  * `coeffect` and `effect` handlers may have side-effects and may return
    a promise of their result

``` text
-> [enter:               ] -> [enter: coeffect-a] -> [enter: coeffect-b] -> [enter: handle-event] --|
<- [leave: handle-effects] <- [leave:           ] <- [leave:           ] <- [leave:             ] <-|
```

## simple data

"simple" data means containing no opaque objects - i.e. no functions or
other opaque types.

## events

Events are simple maps describing something that happened. An event
map must have an `:a-frame/id` key, which describes the type of the
event and will be used to find a handler for processing the event.

Events can also be simple vectors of `[<id> ...]`, like they generally are
in re-frame, but this is less preferred because it makes literal
paths referencing the events less easy to understand.

## coeffects

Coeffects are a simple data map representing (possibly side-effecting)
inputs gathered from the environment and required by the event-handler.
A particular event handler has a chain of coeffect handlers, each of
which identifies a particular coeffect handler by keyword.

## effects

Effects are a simple data structure (map) describing outputs from the event
handler. Effects are either a map of `{<effect-key> <effect-data>}` or
a vector of such maps. the `<effect-key>` keywords will be used to
find a handler for a particular effect.

## simple example

``` clojure
(require '[a-frame.core :as af])

(af/reg-cofx
  ::load-foo
  (fn [app coeffects {id :id url :url}]
    (assoc coeffects ::foo {:id (str url "/" id) :name "foo"})))

(af/reg-fx
  :api/response
  (fn [app data]
  ;; do nothing
  ))

(af/reg-event-fx
  ::get-foo

  [(af/inject-cofx
    ::load-foo
    {:id #a-frame.event/path [::foo-id]
     :url #a-frame.cofx/path [:config :api-url]})]

  (fn [{foo ::foo
        :as coeffects}
       event]
    {:api/response {:foo foo}}))

(def router (af/create-router {:api nil}))

(def r (af/dispatch-sync
          router
          {:config {:api-url "http://foo.com/api"}} ;; initial coeffects
          {:a-frame/id ::get-foo
           ::foo-id "1000"} ;; the event
          ))

;; unpick deref'ing a promise only works on clj
(-> @r :a-frame/effects :api/response)
;; => {:foo {:id "http://foo.com/api/1000", :name "foo"}}

```
