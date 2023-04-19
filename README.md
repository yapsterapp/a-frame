# a-frame

[![Build Status](https://github.com/yapsterapp/a-frame/actions/workflows/clojure.yml/badge.svg)](https://github.com/yapsterapp/a-frame/actions)
[![Clojars Project](https://img.shields.io/clojars/v/com.github.yapsterapp/a-frame.svg)](https://clojars.org/com.github.yapsterapp/a-frame)
[![cljdoc badge](https://cljdoc.org/badge/com.github.yapsterapp/a-frame)](https://cljdoc.org/d/com.github.yapsterapp/a-frame)


TODO - much documentation expansion

A port of the [re-frame](https://github.com/day8/re-frame)
event and effect handling machinery to the async domain, offering a 
straightforward separation of pure and effectful code for both 
Clojure and ClojureScript

## a-frame

A-frame is a port of the non-view parts of
[re-frame](https://github.com/day8/re-frame) - event-handling, cofx and
fx - to the async domain. cofx and fx handlers are async functions, while event
handlers remain pure functions. This
makes it straightforward to cleanly separate pure and impure elements of a
program. A-frame was originally developed for a back-end event-driven 
game engine, but it has been found more generally useful and has been 
successfully used for implementing APIs and is perhaps useful client-side too

* cofx handlers are async functions, returning a Promise of updated coeffects
* fx handlers are async functions, returning a Promise of an ignored result
* event handlers are pure, returning a single`{<effect-key> <effect-data>}` map,
or a list of such maps (which will be processed strictly serially)
* based around a pure-data driven async interceptor-chain
[`a-frame.interceptor-chain`](https://github.com/yapsterapp/a-frame/blob/trunk/src/a_frame/interceptor_chain.cljc)
and implemented on top of promesa and
promisespromises.streams. Being pure-data driven leads to some nice
properties
  * interceptor contexts are fully de/serializable
  * errors can include a 'resume-context' allowing for:
    * automatic retry
    * logging of the resume-context, allowing retry in a REPL
* unlike re-frame, where `dispatch-sync` is uncommon,
`promisespromises.a-frame/dispatch-sync` has been perhaps the most used type of dispatch
with a-frame. `dispatch-sync` is actually an async fn, but it does not resolve
the result promise until all effects (including transitive dispatches)
resulting from the event have been processed

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
  [(af/inject-cofx ::load-foo {:id #promisespromises.cofx/path [:params :query :id]})]
  (fn [{foo ::foo
        :as coeffects} event]
    [{:api/response {:foo foo}}]))


(def router (af/create-router {:api nil}))

(def ctx (af/dispatch-sync router {:params {:query {:id "1000"}}} [::get-foo]))

;; unpick deref'ing a promise only works on clj
(-> @ctx :a-frame/effects first :api/response)
;; => {:foo {:id "1000", :name "foo"}}

```
