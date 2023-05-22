(ns a-frame.events-test
  (:require
   [promisespromises.test :refer [deftest is testing use-fixtures]]
   [promesa.core :as pr]
   [promisespromises.promise :as prpr]
   [a-frame.schema :as schema]
   [a-frame.registry :as registry]
   [a-frame.registry.test :as registry.test]
   [a-frame.fx :as fx]
   [a-frame.cofx :as cofx]
   [a-frame.interceptor-chain :as interceptor-chain]
   [a-frame.events :as sut]))

(use-fixtures :each registry.test/reset-registry)

(deftest flatten-and-remove-nils-test
  (is (= [:foo :bar :baz :blah]
         (sut/flatten-and-remove-nils
          ::id
          [:foo
           nil
           [:bar nil :baz]
           nil
           [:blah]]))))

(deftest register-test
  (testing "registers an event handler"
    (sut/register ::foo [::foo-interceptor nil [::bar-interceptor]])

    (is (= [::foo-interceptor ::bar-interceptor]
           (registry/get-handler schema/a-frame-kind-event ::foo)))))

(deftest coerce-event-options-test

  (is (= {schema/a-frame-event {schema/a-frame-id ::foo :val 100}}
         (sut/coerce-event-options
          {schema/a-frame-id ::foo :val 100})))

  (is (= {schema/a-frame-event {schema/a-frame-id ::foo :val 100}
          schema/a-frame-init-coeffects {::bar 200}}
         (sut/coerce-event-options
          {schema/a-frame-event {schema/a-frame-id ::foo :val 100}
           schema/a-frame-init-coeffects {::bar 200}}))))

(deftest handle-test
  (testing "runs interceptor chain with co-fx, event-handler and fx"
    (let [fx-a (atom {})

          _ (fx/reg-fx
             ::fx-foo
             (fn [app data]
               (is (= ::app app))
               (swap! fx-a assoc ::fx-foo data)
               (pr/resolved data)))

          _ (cofx/reg-cofx
             ::cofx-bar
             (fn [app
                 {{ev-data :val} schema/a-frame-coeffect-event
                  :as coeffects}
                 cofx-data]
               (is (= ::app app))

               (pr/resolved
                (assoc
                 coeffects
                 ::cofx-bar
                 (+ ev-data cofx-data)))))

          _ (sut/reg-event-fx
             ::event-blah
             [(cofx/inject-cofx ::cofx-bar 100)]
             (fn [coeffects event]
               (is (= {schema/a-frame-coeffect-event event
                       ::cofx-bar 200}
                      coeffects))
               (is (= {schema/a-frame-id ::event-blah :val 100} event))
               {::fx-foo {::coeffects coeffects
                          ::event event}}))]
      (pr/let [[k v] (prpr/merge-always
                      (sut/handle
                       {schema/a-frame-app-ctx ::app
                        schema/a-frame-router-global-interceptors [:a-frame.fx/do-fx]}
                       {schema/a-frame-id ::event-blah :val 100}))

               {h-r-app-ctx schema/a-frame-app-ctx
                h-r-queue ::interceptor-chain/queue
                h-r-stack ::interceptor-chain/stack
                h-r-coeffects schema/a-frame-coeffects
                h-r-effects schema/a-frame-effects
                :as _h-r} (when (= ::prpr/ok k)
                            v)]

        (is (= ::prpr/ok k))
        (is (= nil (ex-data v)))
        (is (= nil (ex-message v)))

        (is (= {::fx-foo {::coeffects
                          {schema/a-frame-coeffect-event {schema/a-frame-id ::event-blah :val 100}
                           ::cofx-bar 200}

                          ::event {schema/a-frame-id ::event-blah :val 100}}} @fx-a))


        (is (= ::app h-r-app-ctx))
        (is (= [] h-r-queue))
        (is (= '() h-r-stack))
        (is (= {schema/a-frame-coeffect-event {schema/a-frame-id ::event-blah :val 100}
                ::cofx-bar 200} h-r-coeffects))
        (is (= {::fx-foo {::coeffects
                          {schema/a-frame-coeffect-event
                           {schema/a-frame-id ::event-blah :val 100}

                           ::cofx-bar 200}

                          ::event {schema/a-frame-id ::event-blah :val 100}}}
               @fx-a
               h-r-effects)))))

  (testing "runs interceptor chain with context and coeffects from init-ctx"
    (let [fx-a (atom {})

          _ (fx/reg-fx ::fx-bar (fn [app data]
                                  (is (= ::app app))
                                  (swap! fx-a assoc ::fx-bar data)
                                  (pr/resolved data)))

          _ (sut/reg-event-fx
             ::handle-test-init-ctx
             (fn [coeffects event]
               (is (= {schema/a-frame-coeffect-event event
                       ::cofx-init 550}
                      coeffects))
               (is (= {schema/a-frame-id ::handle-test-init-ctx :val 100} event))
               {::fx-bar {::coeffects coeffects
                          ::event event}}))]
      (pr/let [{h-r-app-ctx schema/a-frame-app-ctx
                h-r-a-frame-router schema/a-frame-router
                h-r-queue ::interceptor-chain/queue
                h-r-stack ::interceptor-chain/stack
                h-r-coeffects schema/a-frame-coeffects
                h-r-effects schema/a-frame-effects
                :as _h-r} (sut/handle
                           {schema/a-frame-app-ctx ::app
                            schema/a-frame-router-global-interceptors [:a-frame.fx/do-fx]
                            schema/a-frame-router ::a-frame}

                           {schema/a-frame-init-coeffects {::cofx-init 550}
                            schema/a-frame-event
                            {schema/a-frame-id ::handle-test-init-ctx :val 100}})]

        (is (= ::app h-r-app-ctx))
        (is (= ::a-frame h-r-a-frame-router))
        (is (= [] h-r-queue))
        (is (= '() h-r-stack))
        (is (= {schema/a-frame-coeffect-event
                {schema/a-frame-id ::handle-test-init-ctx :val 100}
                ::cofx-init 550}
               h-r-coeffects))
        (is (= {::fx-bar {::event {schema/a-frame-id ::handle-test-init-ctx :val 100}
                          ::coeffects {schema/a-frame-coeffect-event
                                       {schema/a-frame-id ::handle-test-init-ctx :val 100}

                                       ::cofx-init 550}}}
               @fx-a
               h-r-effects)))))

  (testing "handles a vector event"
    (let [_ (fx/reg-fx
             ::handle-vec-blah
             (fn [_app data]
               (is (= 100 data))))
          _ (sut/reg-event-fx
             ::event-vector
             (fn [coeffects event]
               (is (= {schema/a-frame-coeffect-event event}
                      coeffects))
               (is (= [::event-vector 100] event))
               {::handle-vec-blah 100}))]
      (pr/let [[k v] (prpr/merge-always
                      (sut/handle
                       {schema/a-frame-app-ctx ::app
                        schema/a-frame-router-global-interceptors [:a-frame.fx/do-fx]}
                       [::event-vector 100]))

               {h-r-app-ctx schema/a-frame-app-ctx
                h-r-queue ::interceptor-chain/queue
                h-r-stack ::interceptor-chain/stack
                h-r-coeffects schema/a-frame-coeffects
                h-r-effects schema/a-frame-effects
                :as _h-r} (when (= ::prpr/ok k)
                            v)]

        (is (= ::prpr/ok k))
        (is (= nil (ex-data v)))
        (is (= nil (ex-message v)))

        (is (= ::app h-r-app-ctx))
        (is (= [] h-r-queue))
        (is (= '() h-r-stack))
        (is (= {schema/a-frame-coeffect-event [::event-vector 100]}
               h-r-coeffects))
        (is (= {::handle-vec-blah 100}
               h-r-effects))))))

;; TODO tests
(deftest malformed-event-test
  (testing "event with no id"))
