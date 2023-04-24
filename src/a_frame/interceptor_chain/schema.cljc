(ns a-frame.interceptor-chain.schema
  (:require
   [a-frame.schema :as af.schema]
   [a-frame.interceptor-chain :as-alias intc]))

(def Interceptor
  "An Interceptor, all methods are optional but should be implemented as
  follows:

  * `::intc/enter` takes 1 or 2 args:
     - context -> context
     - context -> data -> context

  * `::intc/leave` – takes 1 or 2 args:
     - context -> context
     - context -> data -> context

  * `::intc/error` – takes two args
     - context -> error -> context

  All methods may return either promises or plain values."

  [:map
   [::intc/name {:optional true} :keyword]
   [::intc/enter {:optional true} fn?]
   [::intc/leave {:optional true} fn?]
   [::intc/error {:optional true} fn?]])

(def InterceptorSpec
  "the interceptor chain is created with a list of InterceptorSpecs. each
   InterceptorSpec is either
   -  simple keyword, referencing a registered interceptor which will cause
      ::intc/enter and ::intc/leave fns to be invoked with 1-arity, or
   - a map with mandatory `::intc/key`. if there is data
      (either `::intc/enter-data` or `::intc/leave-data`) then `::intc/enter` and `::intc/leave` will
      be invoked with their 2-arity

   providing data like this allows a pure-data (in the re-frame sense - roughly
   something which has no opaque objects and is serializable/deserializable)
   interceptor chain to be registered, which has numerous benefits"

  [:or

   :keyword

   [:map
    [::intc/key :keyword]
    [::intc/enter-data {:optional true} :any]
    [::intc/leave-data {:optional true} :any]]])

(def InterceptorList
  [:sequential InterceptorSpec])

(def interceptor-fn-keys
  [::intc/enter ::intc/leave ::intc/error])

(def InterceptorFnKey
  (into [:enum] interceptor-fn-keys))

(def interceptor-fn-noop
  ::intc/noop)

(def InterceptorFnHistoryKey
  (conj InterceptorFnKey interceptor-fn-noop))

(def InterceptorHistoryElem
  [:or

   [:tuple InterceptorSpec InterceptorFnHistoryKey]

   [:tuple InterceptorSpec InterceptorFnHistoryKey :any]])

(def InterceptorContext
  [:map
   [af.schema/a-frame-app-ctx :any]
   [af.schema/a-frame-router :any]
   [::intc/queue [:vector InterceptorSpec]]
   [::intc/stack [:sequential InterceptorSpec]]
   [::intc/history [:vector InterceptorHistoryElem]]
   [::intc/errors {:optional true} :any]])
