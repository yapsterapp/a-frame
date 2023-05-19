(ns a-frame.interceptor-chain.schema
  (:require
   [malli.core :as m]
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

  (m/schema
   [:map
    [::intc/name {:optional true} :keyword]
    [::intc/enter {:optional true} fn?]
    [::intc/leave {:optional true} fn?]
    [::intc/error {:optional true} fn?]]))

(def InterceptorSpec
  "the interceptor chain is created with a list of InterceptorSpecs. each
   InterceptorSpec is either
   -  simple keyword, referencing a registered interceptor which will cause
      ::intc/enter and ::intc/leave fns to be invoked with 1-arity, or
   - a map with mandatory `::intc/key`

   providing data like this allows a pure-data (in the re-frame sense - roughly
   something which has no opaque objects and is serializable/deserializable)
   interceptor chain to be registered, which has numerous benefits"

  (m/schema
   [:or

    :keyword

    [:map
     [::intc/key :keyword]]]))

(def InterceptorList
  (m/schema
   [:sequential InterceptorSpec]))

(def interceptor-fn-keys
  [::intc/enter ::intc/leave ::intc/error])

(def InterceptorFnKey
  (m/schema
   (into [:enum] interceptor-fn-keys)))

(def interceptor-fn-noop
  ::intc/noop)

(def InterceptorFnHistoryKey
  (-> InterceptorFnKey
       (m/form)
       (conj interceptor-fn-noop)
       (m/schema)))

(def InterceptorHistoryElem
  (m/schema

   [:or

    [:tuple InterceptorSpec InterceptorFnHistoryKey]

    [:tuple InterceptorSpec InterceptorFnHistoryKey :any]]))

(def InterceptorContext
  (m/schema
   [:map
    ;; queue of InterceptorSpecs to ::enter
    [::intc/queue [:vector InterceptorSpec]]
    ;; stack of InterceptorSpecs to ::leave
    [::intc/stack [:sequential InterceptorSpec]]
    ;; history of executed functions
    [::intc/history [:vector InterceptorHistoryElem]]
    ;; any active error
    [::intc/error {:optional true} :any]

    [af.schema/a-frame-app-ctx :any]
    [af.schema/a-frame-router :any]]))
