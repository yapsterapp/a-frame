# a-frame

[![Build Status](https://github.com/yapsterapp/a-frame/actions/workflows/clojure.yml/badge.svg)](https://github.com/yapsterapp/a-frame/actions)
[![Clojars Project](https://img.shields.io/clojars/v/com.github.yapsterapp/a-frame.svg)](https://clojars.org/com.github.yapsterapp/a-frame)
[![cljdoc badge](https://cljdoc.org/badge/com.github.yapsterapp/a-frame)](https://cljdoc.org/d/com.github.yapsterapp/a-frame)


TODO - much documentation expansion

A-frame is a port of the [re-frame](https://github.com/day8/re-frame)
event and effect handling machinery to the async domain. It offers a
straightforward separation of pure and effectful code for both
Clojure and ClojureScript.

## why?

Everyone tells you to keep your side-effecting and pure code apart. A-frame
helps you to do just that.

## how

A-frame uses roughly the same event-processing model as re-frame - `events`
are handled in a 3 stage process:

``` text
[gather coeffects] -> [handle event] -> [process effects]
```

This process is implemented with an interceptor chain. Unlike
re-frame, the a-frame interceptor chain is asynchronous - `:enter` or `:leave`
fns in any stage may return a promise of their result.

* Events are handled by an interceptor chain
* The final interceptor in the chain is the `event-handler`
  * an `event-handler` is a pure function
* Prior interceptors are `coeffect` or `effect` handlers
  * `coeffect` and `effect` handlers may have side-effects and may return
    a promise of their result

``` text
-> [enter: coeffect-a] -> [enter: coeffect-b] -> [enter:         ] -> [enter:          ] -> [enter: handle-event] --|
<- [leave:           ] <- [leave:           ] <- [leave: effect-d] <- [leave: effect-c ] <- [leave:             ] <-|
```

## simple data

"simple" data means containing no opaque objects - i.e. no functions or
other opaque types.

## events

Events are simple maps describing something that happened. An event 
map must have an `:a-frame/type` key, which describes the type of the
event and will be used to find a handler for processing the event.

Note that this is different from re-frame, which generally defines 
events as `[<event-key> ...]` vectors.
The reason for this difference is to make it easier to read
parameter extractor declarations for coeffects referencing values from
the event - maps have named slots, whereas vectors only have positions.

## coeffects

Coeffect are a simple data map representing (possibly side-effecting)
inputs gathered from the environment and required by the event-handler.
A particular event handler has a chain of coeffect handlers, each of
which identifies a particular coeffect handler by keyword.

## effects

Effects are a simple data structure (map) describing outputs from the event
handler. Effects are either a map of `{<effect-key> <effect-data>}` or
a vector of such maps. the `<effect-key>` keywords will be used to
find a handler for a particular effect.


``` clojure
(require '[promisespromises.a-frame :as af])

(af/reg-cofx
  ::load-foo
  (fn [app coeffects {id :id}]
    (assoc coeffects ::foo {:id id :name "foo"})))

(af/reg-fx
  :api/response
  (fn [app data]
  ;; do nothing
  ))

(af/reg-event-fx
  ::get-foo
  [(af/inject-cofx ::load-foo {:id #a-frame.cofx/path [:params :query :id]})]
  (fn [{foo ::foo
        :as coeffects} event]
    [{:api/response {:foo foo}}]))


(def router (af/create-router {:api nil}))

(def r (af/dispatch-sync router {:params {:query {:id "1000"}}} [::get-foo]))

;; unpick deref'ing a promise only works on clj
(-> @r :a-frame/effects first :api/response)
;; => {:foo {:id "1000", :name "foo"}}

```
