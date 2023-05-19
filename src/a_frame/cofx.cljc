(ns a-frame.cofx
  (:require
   [malli.core :as m]
   [promesa.core :as pr]
   [promisespromises.error :as err]
   [a-frame.schema :as schema]
   [a-frame.registry :as registry]
   [a-frame.cofx.data.tag-readers]
   [a-frame.interceptor-chain :as interceptor-chain]
   [a-frame.interceptor-chain.data :as data]
   [taoensso.timbre :refer [info warn]]))

(defn reg-cofx
  [id handler]
  (registry/register-handler schema/a-frame-kind-cofx id handler))

(defn inject-cofx
  "creates an InterceptorSpec data-structure for an event
   interceptor chain"
  ([id]
   {::interceptor-chain/key ::inject-cofx
    ::id id})

  ([id arg-spec]
   {::interceptor-chain/key ::inject-cofx
    ::id id
    ::arg arg-spec}))

;; interceptor

(def inject-cofx-interceptor
  "the interceptor functions to execute the interceptor
   described by an InterceptorSpec produced by inject-cofx"
  {::interceptor-chain/name ::inject-cofx

   ::interceptor-chain/enter
   (fn inject-cofx-enter
     [{app schema/a-frame-app-ctx
       coeffects schema/a-frame-coeffects
       :as context}

      {id ::id
       arg-spec ::arg
       :as interceptor-spec}]

     (let [handler (registry/get-handler schema/a-frame-kind-cofx id)

           has-arg? (contains? interceptor-spec ::arg)
           arg (when has-arg?
                 (data/resolve-data arg-spec context))]

       (if (some? handler)
         (pr/let [coeffects' (if has-arg?
                               (handler app coeffects arg)
                               (handler app coeffects))]
           [(assoc context schema/a-frame-coeffects coeffects')

            ;; second value of response is a log value
            (if has-arg? arg :_)])

         (throw (err/ex-info
                 ::no-cofx-handler
                 {::id id
                  ::arg arg})))))})

(interceptor-chain/register-interceptor
 ::inject-cofx
 inject-cofx-interceptor)

;; now the validated cofx gets the full interceptor-spec, so
;; it can add some data to the interceptor-spec for
;; path and validation

(defn inject-validated-cofx
  "a cofx with a result with a defined schema to be
   injected at a given path"
  ([id schema path]
   (inject-validated-cofx id schema path nil))
  ([id schema path arg-spec]
   (cond->
       {::interceptor-chain/key ::inject-validated-cofx
        ::id id
        ::path path
        ::schema schema}
     (some? arg-spec) (assoc ::arg arg-spec))))

(def inject-validated-cofx-interceptor
  {::interceptor-chain/name ::inject-validated-cofx

   ::interceptor-chain/enter
   (fn inject-validated-cofx-enter
     [{app schema/a-frame-app-ctx
       coeffects schema/a-frame-coeffects
       :as context}

      {id ::id
       arg-spec ::arg

       schema ::schema
       path ::path

       :as interceptor-spec}]

     (let [handler (registry/get-handler schema/a-frame-kind-cofx id)

           has-arg? (contains? interceptor-spec ::arg)
           arg (when has-arg?
                 (data/resolve-data arg-spec context))

           path (if (sequential? path) path [path])]

       (if (some? handler)
         (pr/let [coeffect (if has-arg?
                             (handler app coeffects arg)
                             (handler app coeffects))]

           (when-not (m/validate schema coeffect)
             (throw (err/ex-info
                     ::invalid-cofx
                     {::id id
                      ::arg arg-spec
                      ::path path
                      ::schema schema
                      ::coeffect coeffect})))

           [(assoc-in context
                      (into [schema/a-frame-coeffects] path)
                      coeffect)

            ;; second value of response is a log value
            (if has-arg? arg :_)])

         (throw (err/ex-info
                 ::no-cofx-handler
                 {::id id
                  ::arg arg})))))})

(interceptor-chain/register-interceptor
 ::inject-validated-cofx
 inject-validated-cofx-interceptor)
