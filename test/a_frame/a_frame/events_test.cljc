(ns prpr3.a-frame.events-test
  (:require
   [prpr3.test :refer [deftest is testing use-fixtures]]
   [promesa.core :as pr]
   [prpr3.promise :as prpr]
   [prpr3.a-frame.schema :as schema]
   [prpr3.a-frame.registry :as registry]
   [prpr3.a-frame.registry.test :as registry.test]
   [prpr3.a-frame.fx :as fx]
   [prpr3.a-frame.cofx :as cofx]
   [prpr3.a-frame.interceptor-chain :as interceptor-chain]
   [prpr3.a-frame.events :as sut]))

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

(deftest coerce-extended-event-test
  (is (= {schema/a-frame-event [::foo 100]}
         (sut/coerce-extended-event [::foo 100])))
  (is (= {schema/a-frame-event [::foo 100]
          schema/a-frame-coeffects {::bar 200}}
         (sut/coerce-extended-event
          {schema/a-frame-event [::foo 100]
           schema/a-frame-coeffects {::bar 200}}))))

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
                 {[_ev-key ev-data] schema/a-frame-coeffect-event
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
               (is (= [::event-blah 100] event))
               {::fx-foo {::coeffects coeffects
                          ::event event}}))]
      (pr/let [[k v] (prpr/merge-always
                      (sut/handle
                           {schema/a-frame-app-ctx ::app}
                           [::event-blah 100]))

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
                          {schema/a-frame-coeffect-event [::event-blah 100]
                           ::cofx-bar 200}

                          ::event [::event-blah 100]}} @fx-a))


        (is (= ::app h-r-app-ctx))
        (is (= [] h-r-queue))
        (is (= '() h-r-stack))
        (is (= {schema/a-frame-coeffect-event [::event-blah 100]
                ::cofx-bar 200} h-r-coeffects))
        (is (= {::fx-foo {::coeffects
                          {schema/a-frame-coeffect-event [::event-blah 100]
                           ::cofx-bar 200}

                          ::event [::event-blah 100]}}
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
               (is (= [::handle-test-init-ctx 100] event))
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
                            schema/a-frame-router ::a-frame}

                           {schema/a-frame-coeffects {::cofx-init 550}
                            schema/a-frame-event [::handle-test-init-ctx 100]})]

        (is (= ::app h-r-app-ctx))
        (is (= ::a-frame h-r-a-frame-router))
        (is (= [] h-r-queue))
        (is (= '() h-r-stack))
        (is (= {schema/a-frame-coeffect-event [::handle-test-init-ctx 100]
                ::cofx-init 550}
               h-r-coeffects))
        (is (= {::fx-bar {::event [::handle-test-init-ctx 100]
                          ::coeffects {schema/a-frame-coeffect-event
                                       [::handle-test-init-ctx 100]

                                       ::cofx-init 550}}}
               @fx-a
               h-r-effects)))))
  )
