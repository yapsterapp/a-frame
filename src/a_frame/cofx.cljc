(ns a-frame.cofx
  (:require
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
;;
;; TODO change the interceptor-chain so that instead of data
;; being resolved in the interceptor-chain and passed to the
;; interceptor-fn, the interceptor-spec data-structure itself
;; is passed to the enter/leave function... leaving it
;; up to the particular interceptor definition to resolve
;; data or do whatever else it wants - it can add anything else
;; useful to the interceptor-spec and handle it when then
;; interceptor is run

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


(defn inject-validated-cofx
  "a cofx with a result with a defined schema to be
   injected at a given path"
  ([id schema path]
   (inject-validated-cofx id schema path nil))
  ([id schema path arg-spec]
   {::interceptor-chain/key ::inject-validated-cofx
    ::interceptor-chain/enter-data (cond-> {::id id}

                                     (some? arg-spec)
                                     (assoc ::arg arg-spec))}))

(def inject-validated-cofx-interceptor
  {::interceptor-chain/name ::inject-validated-cofx

   ::interceptor-chain/enter
   (fn inject-validated-cofx-enter
     [{app schema/a-frame-app-ctx
       coeffects schema/a-frame-coeffects
       :as context}

      {enter-data-spec ::interceptor-chain/enter-data
       :as _interceptor-spec}])})

(interceptor-chain/register-interceptor
 ::inject-validated-cofx
 inject-validated-cofx-interceptor)
