(ns a-frame.cofx-test
  (:require
   [malli.core :as m]
   [promesa.core :as pr]
   [promisespromises.promise :as prpr]
   [promisespromises.test :refer [deftest is testing use-fixtures]]
   [a-frame.schema :as schema]
   [a-frame.registry :as registry]
   [a-frame.registry.test :as registry.test]
   [a-frame.interceptor-chain :as interceptor-chain]
   [a-frame.multimethods :as mm]
   [a-frame.cofx :as sut]))

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
                     (sut/inject-cofx resolved-cofx-key {:a #a-frame.cofx/path [::inject-cofx-1-arg-resolver-static]
                                                         :b #a-frame.cofx/event-path [1]})]
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

(defmethod mm/validate ::int
  [_schema value]
  (m/validate :int value))

(defmethod mm/validate ::string
  [_schema value]
  (m/validate :string value))

(defmethod mm/validate ::keyword
  [_schema value]
  (m/validate :keyword value))

(defmethod mm/validate ::any
  [_schema value]
  (m/validate :any value))

(defmethod mm/validate ::=100
  [_schema value]
  (m/validate [:= 100] value))

(deftest inject-validated-cofx-test
  (testing "0-arg cofx"
    (pr/let [cofx-key ::inject-validated-cofx-test-0-arg

             _ (sut/reg-cofx cofx-key (fn [app _cofx]
                                        (is (= ::app app))
                                        100))

             init-int-ctx {}

             interceptors  [(sut/inject-validated-cofx
                             cofx-key
                             ::int)]

             int-r (interceptor-chain/execute
                    ::app
                    ::a-frame
                    interceptors
                    init-int-ctx)]

      (is (= (assoc
              init-int-ctx
              schema/a-frame-coeffects {cofx-key 100})
             (apply dissoc int-r interceptor-chain/context-keys)))))

  (testing "invalid 0-arg cofx"
    (pr/let [cofx-key ::inject-validated-cofx-test-0-arg-invalid

             _ (sut/reg-cofx cofx-key (fn [app _cofx]
                                        (is (= ::app app))
                                        100))

             init-int-ctx {}

             interceptors  [(sut/inject-validated-cofx
                             cofx-key
                             ::string)]

             [k e] (prpr/merge-always
                    (interceptor-chain/execute
                     ::app
                     ::a-frame
                     interceptors
                     init-int-ctx))]

      (is (= ::prpr/error k))

      (is (= :a-frame.cofx/invalid-cofx
             (-> e ex-cause ex-data :error/type)))))

  (testing "1-arg cofx"
    (pr/let [cofx-key ::inject-validated-cofx-test-1-arg
             _ (sut/reg-cofx cofx-key (fn [app _cofx arg]
                                        (is (= ::app app))
                                        arg))

             init-int-ctx {}

             int-r (interceptor-chain/execute
                    ::app
                    ::a-frame
                    [(sut/inject-validated-cofx cofx-key 100 ::=100)]
                    init-int-ctx)]

      (is (= (assoc
              init-int-ctx
              schema/a-frame-coeffects {cofx-key 100})
             (apply dissoc int-r interceptor-chain/context-keys)))))

  (testing "1-arg cofx with resolver"
    (pr/let [static-cofx-key ::inject-validated-cofx-1-arg-resolver-static
             resolved-cofx-key ::inject-validated-cofx-1-arg-resolver-resolved

             _ (sut/reg-cofx
                static-cofx-key
                (fn [app _cofx]
                  (is (= ::app app))
                  ::static-val))

             _ (sut/reg-cofx
                resolved-cofx-key
                (fn [app _cofx {_a :a
                                _b :b
                                :as arg}]
                  (is (= ::app app))
                  arg))

             init-coeffects {schema/a-frame-coeffect-event
                             [::foo {:event-data :val}]}

             init-int-ctx {schema/a-frame-coeffects init-coeffects}

             int-r (interceptor-chain/execute
                    ::app
                    ::a-frame
                    [(sut/inject-validated-cofx
                      static-cofx-key
                      ::keyword)

                     (sut/inject-validated-cofx
                      resolved-cofx-key
                      {:a #a-frame.cofx/path [::inject-validated-cofx-1-arg-resolver-static]
                       :b #a-frame.cofx/event-path [1]}
                      ::any)]
                    init-int-ctx)]

      (is (= (assoc
              init-int-ctx

              schema/a-frame-coeffects
              (merge
               init-coeffects
               {static-cofx-key ::static-val
                resolved-cofx-key {:a ::static-val
                                   :b {:event-data :val}}}))

             (apply dissoc int-r interceptor-chain/context-keys)))))
  )
