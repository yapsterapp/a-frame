(ns prpr3.a-frame.fx-test
  (:require
   [prpr3.test
    :refer [deftest is testing use-fixtures with-log-level-fixture]]
   [promesa.core :as pr]
   [prpr3.stream.transport :as stream.impl]
   [prpr3.a-frame.schema :as schema]
   [prpr3.a-frame.registry :as registry]
   [prpr3.a-frame.registry.test :as registry.test]
   [prpr3.a-frame.events :as events]
   [prpr3.a-frame.interceptor-chain :as interceptor-chain]
   [prpr3.a-frame.fx :as sut]
   [prpr3.a-frame.router :as router]
   [prpr3.promise :as prpr]))

(use-fixtures :once (with-log-level-fixture :warn))
(use-fixtures :each registry.test/reset-registry)

(deftest reg-fx-test
  (testing "registers an fx handler"
    (let [a (atom nil)
          _ (sut/reg-fx ::reg-fx-test (fn [app data]
                                        (reset! a {:app app
                                                   :data data})))
          h (registry/get-handler schema/a-frame-kind-fx ::reg-fx-test)

          _ (h {schema/a-frame-app-ctx ::app} ::data)]

      (is (= {:app ::app
              :data ::data}
             @a)))))

(deftest reg-fx-ctx-test
  (testing "registers a fx context handler"
    (sut/reg-fx-ctx ::reg-fx-ctx-test ::foo-handler)

    (is (= ::foo-handler
           (registry/get-handler schema/a-frame-kind-fx ::reg-fx-ctx-test)))))

(deftest do-single-effect-test
  (testing "calls a sync fx handler"
    (let [fx-key ::do-single-effect-test-sync
          r-a (atom nil)
          _ (sut/reg-fx fx-key (fn [app data]
                                 (is (= ::app app))
                                 (swap! r-a (constantly data))))]

      (pr/let [_ (sut/do-single-effect
                  {schema/a-frame-app-ctx ::app}
                  fx-key
                  ::foo-data)]

        (is (= ::foo-data @r-a)))))

  (testing "calls an async fx handler"
    (let [fx-key ::do-single-effect-test-async
          r-a (atom nil)
          trigger-pr (pr/deferred)
          _ (sut/reg-fx fx-key (fn [app data]
                                 (is (= ::app app))
                                 (pr/chain
                                  trigger-pr
                                  (fn [_]
                                    (swap! r-a (constantly data))))))

          ;; don't wait for the promise to be resolved
          fx-r-pr (sut/do-single-effect
                   {schema/a-frame-app-ctx ::app}
                   fx-key
                   ::foo-data)

          ;; trigger the fx chain
          _ (pr/resolve! trigger-pr ::trigger)]

      (pr/let [;; and wait for completion
               _ fx-r-pr]


        (is (= ::foo-data @r-a)))))
  )

(deftest do-map-of-effects-test
  (testing "calls multiple fx handlers and returns a map of results"
    (let [foo-a (atom 1)
          bar-a (atom 9)
          foo-fx-key ::do-map-of-effects-test-foo
          _ (sut/reg-fx foo-fx-key (fn [app data]
                                     (is (= ::app app))
                                     (pr/resolved
                                      (swap! foo-a + data))))
          bar-fx-key ::do-map-of-effects-test-bar
          _ (sut/reg-fx bar-fx-key (fn [app data]
                                     (is (= ::app app))
                                     (swap! bar-a + data)))]
      (pr/let [fx-r (sut/do-map-of-effects
                     {schema/a-frame-app-ctx ::app}
                     {foo-fx-key 3
                      bar-fx-key 2})]

        (is (= 4 @foo-a))
        (is (= 11 @bar-a))
        (is (= {foo-fx-key 4
                bar-fx-key 11} fx-r)))))

  (testing "propagates errors"
    (let [foo-a (atom 1)
          bar-a (atom 9)
          foo-fx-key ::do-map-of-effects-test-error-foo
          _ (sut/reg-fx foo-fx-key (fn [app data]
                                     (is (= ::app app))
                                     (pr/resolved
                                      (swap! foo-a + data))))
          bar-fx-key ::do-map-of-effects-test-error-bar
          e (ex-info "bar" {::id ::bar})
          _ (sut/reg-fx bar-fx-key (fn [app data]
                                     (is (= ::app app))
                                     (throw e)))]
      (pr/let [[k r] (prpr/merge-always
                      (sut/do-map-of-effects
                       {schema/a-frame-app-ctx ::app}
                       {foo-fx-key 3
                        bar-fx-key 2}))]

        (is (= 4 @foo-a))
        (is (= 9 @bar-a))
        (is (= ::prpr/error k))
        (is (= e r))))))

(deftest do-seq-of-effects-test
  (testing "calls multiple fx handlers serially and returns a seq of results"
    (let [r-a (atom [])

          foo-fx-key ::do-seq-of-effects-test-foo
          _ (sut/reg-fx foo-fx-key (fn [app data]
                                     (is (= ::app app))
                                     (pr/resolved
                                      (swap! r-a conj data))))
          bar-fx-key ::do-seq-of-effects-test-bar
          _ (sut/reg-fx bar-fx-key (fn [app data]
                                     (is (= ::app app))
                                     (swap! r-a conj data)))]
      (pr/let [fx-r (sut/do-seq-of-effects
                     {schema/a-frame-app-ctx ::app}
                     [{foo-fx-key ::foo-val}
                      {bar-fx-key ::bar-val}])]

        (is (= [::foo-val ::bar-val]
               @r-a))

        (is (= [{foo-fx-key [::foo-val]}
                {bar-fx-key [::foo-val ::bar-val]}]
               fx-r)))))

  (testing "calls multiple maps of fx handlers with multiple fx"
    (let [r-a (atom {})

          foo-fx-key ::do-seq-of-effects-test-multiple-maps-foo
          _ (sut/reg-fx foo-fx-key (fn [app data]
                                     (is (= ::app app))
                                     (swap! r-a assoc ::foo data)
                                     (pr/resolved data)))
          bar-fx-key ::do-seq-of-effects-test-multiple-maps-bar
          _ (sut/reg-fx bar-fx-key (fn [app data]
                                     (is (= ::app app))
                                     (swap! r-a assoc ::bar data)
                                     data))
          blah-fx-key ::do-seq-of-effects-test-multiple-maps-blah
          _ (sut/reg-fx blah-fx-key (fn [app data]
                                      (is (= ::app app))
                                      (swap! r-a assoc ::blah data)
                                      (pr/resolved data)))]
      (pr/let [;; check that the fn works with a seq as well as a vector
               fx-r (sut/do-seq-of-effects
                     {schema/a-frame-app-ctx ::app}
                     (map
                      identity
                      [{foo-fx-key ::foo-val
                        bar-fx-key ::bar-val}
                       {blah-fx-key ::blah-val}]))]

        (is (= {::foo ::foo-val
                ::bar ::bar-val
                ::blah ::blah-val}
               @r-a))

        (is (= [{foo-fx-key ::foo-val
                 bar-fx-key ::bar-val}
                {blah-fx-key ::blah-val}]
               fx-r)))))

  (testing "propagates an error in an fx handler"
    (let [r-a (atom [])

          foo-fx-key ::do-seq-of-effects-test-error-foo
          _ (sut/reg-fx foo-fx-key (fn [app data]
                                     (is (= ::app app))
                                     (pr/resolved
                                      (swap! r-a conj data))))
          bar-fx-key ::do-seq-of-effects-test-error-bar

          e (ex-info "boo" {::id ::bar})
          _ (sut/reg-fx bar-fx-key (fn [app data]
                                     (is (= ::app app))
                                     (throw e)))]
      (pr/let [[k val] (prpr/merge-always
                        (sut/do-seq-of-effects
                         {schema/a-frame-app-ctx ::app}
                         [{foo-fx-key ::foo-val}
                          {bar-fx-key ::bar-val}]))]

        (is (= [::foo-val] @r-a))

        (is (= ::prpr/error k))
        (is (= e val))))))

(deftest do-fx-test
  (testing "calls a seq of maps of multiple fx handlers"
    (let [r-a (atom {})

          foo-fx-key ::do-fx-test-foo
          _ (sut/reg-fx foo-fx-key (fn [app data]
                                     (is (= ::app app))
                                     (swap! r-a assoc ::foo data)
                                     (pr/resolved data)))
          bar-fx-key ::do-fx-test-bar
          _ (sut/reg-fx bar-fx-key (fn [app data]
                                     (is (= ::app app))
                                     (swap! r-a assoc ::bar data)
                                     data))
          blah-fx-key ::do-fx-test-blah
          _ (sut/reg-fx blah-fx-key (fn [app data]
                                      (is (= ::app app))
                                      (swap! r-a assoc ::blah data)
                                      (pr/resolved data)))

          init-int-ctx {schema/a-frame-effects [{foo-fx-key ::foo-val
                                                 bar-fx-key ::bar-val}
                                                {blah-fx-key ::blah-val}]}]
      (pr/let [int-r (interceptor-chain/execute
                      ::app
                      ::a-frame
                      [::sut/do-fx]
                      init-int-ctx)]

        ;; performs the side-effects
        (is (= {::foo ::foo-val
                ::bar ::bar-val
                ::blah ::blah-val}
               @r-a))

        ;; leaves the context untouched
        (is (= init-int-ctx
               (apply dissoc int-r interceptor-chain/context-keys)))))))

(def test-app-ctx {::FOO "foo"})

(deftest dispatch-fx-test
  (testing "dispatches without transitive coeffects"
    (let [{event-s schema/a-frame-router-event-stream
           :as router} (router/create-router test-app-ctx {})
          out-a (atom [])

          _ (events/reg-event-fx
             ::dispatch-fx-test-without-transitive-coeffects
             (fn [cofx [_ n :as event-v]]
               ;; only the first event should have the ::BAR coeffect
               (if (= 0 n)
                 (is (= {schema/a-frame-coeffect-event event-v
                         ::BAR "bar"} cofx))
                 (is (= {schema/a-frame-coeffect-event event-v} cofx)))

               (swap! out-a conj n)

               (when (<= n 3)

                 {:a-frame/dispatch
                  [::dispatch-fx-test-without-transitive-coeffects
                   (+ n 2)]})))]

      (pr/let [_r (router/dispatch-sync
                   router
                   {schema/a-frame-event
                    [::dispatch-fx-test-without-transitive-coeffects 0]

                    schema/a-frame-coeffects {::BAR "bar"}})]

        (is (= [0 2 4] @out-a))

        ;; main stream should not be closed
        (is (not (stream.impl/closed? event-s))))))

  (testing "dispatches with transitive coeffects"
    (let [{event-s schema/a-frame-router-event-stream
           :as router} (router/create-router test-app-ctx {})
          out-a (atom [])

          _ (events/reg-event-fx
             ::dispatch-fx-test-with-transitive-coeffects
             (fn [cofx [_ n :as event-v]]

               ;; all events should have the ::BAR coeffect
               (is (= {schema/a-frame-coeffect-event event-v
                       ::BAR "bar"} cofx))

               (swap! out-a conj n)

               (when (<= n 3)

                 {:a-frame/dispatch
                  {schema/a-frame-event
                   [::dispatch-fx-test-with-transitive-coeffects
                    (+ n 2)]

                   schema/a-frame-event-transitive-coeffects? true}})))]

      (pr/let [_r (router/dispatch-sync
                   router
                   {schema/a-frame-event
                    [::dispatch-fx-test-with-transitive-coeffects 0]

                    schema/a-frame-coeffects {::BAR "bar"}})]

        (is (= [0 2 4] @out-a))

        ;; main stream should not be closed
        (is (not (stream.impl/closed? event-s))))))
  )

(deftest dispatch-sync-fx-test
  (testing "dispatches without transitive coeffects"
    (let [{event-s schema/a-frame-router-event-stream
           :as router} (router/create-router test-app-ctx {})
          out-a (atom [])

          _ (events/reg-event-fx
             ::dispatch-sync-fx-test-without-transitive-coeffects
             (fn [cofx [_ n :as event-v]]
               ;; only the first event should have the ::BAR coeffect
               (if (= 0 n)
                 (is (= {schema/a-frame-coeffect-event event-v
                         ::BAR "bar"} cofx))
                 (is (= {schema/a-frame-coeffect-event event-v} cofx)))

               (swap! out-a conj n)

               (when (<= n 3)

                 {:a-frame/dispatch-sync
                  {schema/a-frame-event
                   [::dispatch-sync-fx-test-without-transitive-coeffects
                    (+ n 2)]

                   schema/a-frame-event-transitive-coeffects? false}})))]

      (pr/let [_r (router/dispatch-sync
                   router
                   {schema/a-frame-event
                    [::dispatch-sync-fx-test-without-transitive-coeffects 0]

                    schema/a-frame-coeffects {::BAR "bar"}})]

        (is (= [0 2 4] @out-a))

        ;; main stream should not be closed
        (is (not (stream.impl/closed? event-s))))))

  (testing "dispatches with transitive coeffects"
    (let [{event-s schema/a-frame-router-event-stream
           :as router} (router/create-router test-app-ctx {})
          out-a (atom [])

          _ (events/reg-event-fx
             ::dispatch-sync-fx-test-with-transitive-coeffects
             (fn [cofx [_ n :as event-v]]

               ;; all events should have the ::BAR coeffect
               (is (= {schema/a-frame-coeffect-event event-v
                       ::BAR "bar"} cofx))

               (swap! out-a conj n)

               (when (<= n 3)

                 {:a-frame/dispatch-sync
                  {schema/a-frame-event
                   [::dispatch-sync-fx-test-with-transitive-coeffects
                    (+ n 2)]}})))]

      (pr/let [_r (router/dispatch-sync
                   router
                   {schema/a-frame-event
                    [::dispatch-sync-fx-test-with-transitive-coeffects 0]
                    schema/a-frame-coeffects {::BAR "bar"}})]

        (is (= [0 2 4] @out-a))

        ;; main stream should not be closed
        (is (not (stream.impl/closed? event-s)))))))

(deftest dispatch-n-fx-test
  (testing "dispatches without transitive coeffects"
    (let [{event-s schema/a-frame-router-event-stream
           :as router} (router/create-router test-app-ctx {})
          out-a (atom [])

          _ (events/reg-event-fx
             ::dispatch-n-fx-test-without-transitive-coeffects
             (fn [cofx [_ n :as event-v]]
               ;; only the first event should have the ::BAR coeffect
               (if (= 0 n)
                 (is (= {schema/a-frame-coeffect-event event-v
                         ::BAR "bar"} cofx))
                 (is (= {schema/a-frame-coeffect-event event-v} cofx)))

               (swap! out-a conj n)

               (when (<= n 3)

                 {:a-frame/dispatch-n
                  [[::dispatch-n-fx-test-without-transitive-coeffects
                    (+ n 2)]]})))]

      (pr/let [_r (router/dispatch-sync
                   router
                   {schema/a-frame-event
                    [::dispatch-n-fx-test-without-transitive-coeffects 0]
                    schema/a-frame-coeffects {::BAR "bar"}})]

        (is (= [0 2 4] @out-a))

        ;; main stream should not be closed
        (is (not (stream.impl/closed? event-s))))))

  (testing "dispatches with transitive coeffects"
    (let [{event-s schema/a-frame-router-event-stream
           :as router} (router/create-router test-app-ctx {})
          out-a (atom [])

          _ (events/reg-event-fx
             ::dispatch-n-fx-test-with-transitive-coeffects
             (fn [cofx [_ n :as event-v]]

               ;; all events should have the ::BAR coeffect
               (is (= {schema/a-frame-coeffect-event event-v
                       ::BAR "bar"} cofx))

               (swap! out-a conj n)

               (when (<= n 3)

                 {:a-frame/dispatch-n
                  [{schema/a-frame-event
                    [::dispatch-n-fx-test-with-transitive-coeffects
                     (+ n 2)]

                    schema/a-frame-event-transitive-coeffects? true}]})))]

      (pr/let [_r (router/dispatch-sync
                   router
                   {schema/a-frame-event
                    [::dispatch-n-fx-test-with-transitive-coeffects 0]
                    schema/a-frame-coeffects {::BAR "bar"}})]

        (is (= [0 2 4] @out-a))

        ;; main stream should not be closed
        (is (not (stream.impl/closed? event-s)))))))

(deftest dispatch-n-sync-fx-test
  (testing "dispatches without transitive coeffects"
    (let [{event-s schema/a-frame-router-event-stream
           :as router} (router/create-router test-app-ctx {})
          out-a (atom [])

          _ (events/reg-event-fx
             ::dispatch-n-sync-fx-test-without-transitive-coeffects
             (fn [cofx [_ n :as event-v]]
               ;; only the first event should have the ::BAR coeffect
               (if (= 0 n)
                 (is (= {schema/a-frame-coeffect-event event-v
                         ::BAR "bar"} cofx))
                 (is (= {schema/a-frame-coeffect-event event-v} cofx)))

               (swap! out-a conj n)

               (when (<= n 3)

                 {:a-frame/dispatch-n-sync
                  [{schema/a-frame-event
                    [::dispatch-n-sync-fx-test-without-transitive-coeffects
                     (+ n 2)]

                    schema/a-frame-event-transitive-coeffects? false}]})))]

      (pr/let [_r (router/dispatch-sync
                   router
                   {schema/a-frame-event
                    [::dispatch-n-sync-fx-test-without-transitive-coeffects 0]
                    schema/a-frame-coeffects {::BAR "bar"}})]

        (is (= [0 2 4] @out-a))

        ;; main stream should not be closed
        (is (not (stream.impl/closed? event-s))))))

  (testing "dispatches with transitive coeffects"
    (let [{event-s schema/a-frame-router-event-stream
           :as router} (router/create-router test-app-ctx {})
          out-a (atom [])

          _ (events/reg-event-fx
             ::dispatch-n-sync-fx-test-with-transitive-coeffects
             (fn [cofx [_ n :as event-v]]

               ;; all events should have the ::BAR coeffect
               (is (= {schema/a-frame-coeffect-event event-v
                       ::BAR "bar"} cofx))

               (swap! out-a conj n)

               (when (<= n 3)

                 {:a-frame/dispatch-n-sync
                  [{schema/a-frame-event
                    [::dispatch-n-sync-fx-test-with-transitive-coeffects
                     (+ n 2)]}]})))]
      (pr/let [_r (router/dispatch-sync
                   router
                   {schema/a-frame-event
                    [::dispatch-n-sync-fx-test-with-transitive-coeffects 0]
                    schema/a-frame-coeffects {::BAR "bar"}})]

        (is (= [0 2 4] @out-a))

        ;; main stream should not be closed
        (is (not (stream.impl/closed? event-s))))))
  )
