(ns prpr3.a-frame.cofx-test
  (:require
   [promesa.core :as pr]
   [prpr3.test :refer [deftest is testing use-fixtures]]
   [prpr3.a-frame.schema :as schema]
   [prpr3.a-frame.registry :as registry]
   [prpr3.a-frame.registry.test :as registry.test]
   [prpr3.a-frame.interceptor-chain :as interceptor-chain]
   [prpr3.a-frame.cofx :as sut]))

(use-fixtures :each registry.test/reset-registry)

(deftest reg-cofx-test
  (testing "registers an cofx handler"
    (let [cofx-key ::reg-cofx-test]
      (sut/reg-cofx cofx-key ::reg-cofx-test-handler)

      (is (= ::reg-cofx-test-handler
             (registry/get-handler schema/a-frame-kind-cofx cofx-key))))))

(deftest inject-cofx-test
  (testing "0-arg cofx"
    (pr/let [cofx-key ::inject-cofx-test-0-arg
             _ (sut/reg-cofx cofx-key (fn [app cofx]
                                        (is (= ::app app))
                                        (assoc cofx cofx-key 100)))

             init-int-ctx {}

             interceptors  [(sut/inject-cofx cofx-key)]

             int-r (interceptor-chain/execute
                    ::app
                    ::a-frame
                    interceptors
                    init-int-ctx)]


      (is (= (assoc
              init-int-ctx
              schema/a-frame-coeffects {cofx-key 100})
             (apply dissoc int-r interceptor-chain/context-keys)))))

  (testing "1-arg cofx"
    (pr/let [cofx-key ::inject-cofx-test-1-arg
             _ (sut/reg-cofx cofx-key (fn [app cofx arg]
                                        (is (= ::app app))
                                        (assoc cofx cofx-key arg)))

             init-int-ctx {}

             int-r (interceptor-chain/execute
                    ::app
                    ::a-frame
                    [(sut/inject-cofx cofx-key 100)]
                    init-int-ctx)]

      (is (= (assoc
              init-int-ctx
              schema/a-frame-coeffects {cofx-key 100})
             (apply dissoc int-r interceptor-chain/context-keys)))))

  (testing "1-arg cofx with resolver"
    (pr/let [static-cofx-key ::inject-cofx-1-arg-resolver-static
             resolved-cofx-key ::inject-cofx-1-arg-resolver-resolved

             _ (sut/reg-cofx
                static-cofx-key
                (fn [app cofx]
                  (is (= ::app app))
                  (assoc cofx static-cofx-key ::static-val)))

             _ (sut/reg-cofx
                resolved-cofx-key
                (fn [app cofx {_a :a
                              _b :b
                              :as arg}]
                  (is (= ::app app))
                  (assoc cofx resolved-cofx-key arg)))

             init-coeffects {schema/a-frame-coeffect-event
                             [::foo {:event-data :val}]}

             init-int-ctx {schema/a-frame-coeffects init-coeffects}

             int-r (interceptor-chain/execute
                    ::app
                    ::a-frame
                    [(sut/inject-cofx static-cofx-key)
                     (sut/inject-cofx resolved-cofx-key {:a #prpr3.cofx/path [::inject-cofx-1-arg-resolver-static]
                                                         :b #prpr3.cofx/event-path [1]})]
                    init-int-ctx)]

      (is (= (assoc
              init-int-ctx

              schema/a-frame-coeffects
              (merge
               init-coeffects
               {static-cofx-key ::static-val
                resolved-cofx-key {:a ::static-val
                                   :b {:event-data :val}}}))

             (apply dissoc int-r interceptor-chain/context-keys))))))
