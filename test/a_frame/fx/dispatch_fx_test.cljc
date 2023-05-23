(ns a-frame.fx.dispatch-fx-test
  (:require
   [promisespromises.test
    :refer [deftest is testing use-fixtures with-log-level-fixture]]
   [promesa.core :as pr]
   [promisespromises.stream.transport :as stream.impl]
   [a-frame.schema :as schema]
   [a-frame.registry.test :as registry.test]
   [a-frame.events :as events]
   [a-frame.router :as router]
   [a-frame.util.coeffects-test :refer [assert-cofx]]))

;; these tests are in their own namespace because they
;; exercise a lot more of the machinery than the
;; other fx tests, so they are more likely to be broken
;; for longer by big changes

(use-fixtures :once (with-log-level-fixture :warn))
(use-fixtures :each registry.test/reset-registry)

(def test-app-ctx {::FOO "foo"})

(deftest dispatch-fx-test
  (testing "dispatches without transitive coeffects"
    (let [{event-s schema/a-frame-router-event-stream
           :as router} (router/create-router
                        test-app-ctx
                        {})
          out-a (atom [])

          _ (events/reg-event-fx
             ::dispatch-fx-test-without-transitive-coeffects
             (fn [cofx {n :n :as event}]
               ;; only the first event should have the ::BAR coeffect
               (if (= 0 n)
                 (assert-cofx
                  {schema/a-frame-coeffect-event event
                   ::BAR "bar"}
                  cofx)
                 (assert-cofx {schema/a-frame-coeffect-event event} cofx))

               (swap! out-a conj n)

               (when (<= n 3)

                 {:a-frame/dispatch
                  {schema/a-frame-id ::dispatch-fx-test-without-transitive-coeffects
                   :n (+ n 2)}})))]

      (pr/let [_r (router/dispatch-sync
                   router
                   {schema/a-frame-event
                    {schema/a-frame-id ::dispatch-fx-test-without-transitive-coeffects
                     :n 0}

                    schema/a-frame-init-coeffects {::BAR "bar"}})]

        (is (= [0 2 4] @out-a))

        ;; main stream should not be closed
        (is (not (stream.impl/closed? event-s))))))

  (testing "dispatches with transitive coeffects"
    (let [{event-s schema/a-frame-router-event-stream
           :as router} (router/create-router test-app-ctx {})
          out-a (atom [])

          _ (events/reg-event-fx
             ::dispatch-fx-test-with-transitive-coeffects
             (fn [cofx {n :n :as event}]

               ;; all events should have the ::BAR coeffect
               (assert-cofx
                {schema/a-frame-coeffect-event event
                 ::BAR "bar"}
                cofx)

               (swap! out-a conj n)

               (when (<= n 3)

                 {:a-frame/dispatch
                  {schema/a-frame-event
                   {schema/a-frame-id ::dispatch-fx-test-with-transitive-coeffects
                    :n (+ n 2)}

                   schema/a-frame-event-transitive-coeffects? true}})))]

      (pr/let [_r (router/dispatch-sync
                   router
                   {schema/a-frame-event
                    {schema/a-frame-id ::dispatch-fx-test-with-transitive-coeffects
                     :n 0}

                    schema/a-frame-init-coeffects {::BAR "bar"}})]

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
             (fn [cofx {n :n :as event}]
               ;; only the first event should have the ::BAR coeffect
               (if (= 0 n)
                 (assert-cofx
                  {schema/a-frame-coeffect-event event
                   ::BAR "bar"} cofx)
                 (assert-cofx {schema/a-frame-coeffect-event event} cofx))

               (swap! out-a conj n)

               (when (<= n 3)

                 {:a-frame/dispatch-sync
                  {schema/a-frame-event
                   {schema/a-frame-id ::dispatch-sync-fx-test-without-transitive-coeffects
                    :n (+ n 2)}

                   schema/a-frame-event-transitive-coeffects? false}})))]

      (pr/let [_r (router/dispatch-sync
                   router
                   {schema/a-frame-event
                    {schema/a-frame-id ::dispatch-sync-fx-test-without-transitive-coeffects
                     :n 0}

                    schema/a-frame-init-coeffects {::BAR "bar"}})]

        (is (= [0 2 4] @out-a))

        ;; main stream should not be closed
        (is (not (stream.impl/closed? event-s))))))

  (testing "dispatches with transitive coeffects"
    (let [{event-s schema/a-frame-router-event-stream
           :as router} (router/create-router test-app-ctx {})
          out-a (atom [])

          _ (events/reg-event-fx
             ::dispatch-sync-fx-test-with-transitive-coeffects
             (fn [cofx {n :n :as event}]

               ;; all events should have the ::BAR coeffect
               (assert-cofx
                {schema/a-frame-coeffect-event event
                 ::BAR "bar"} cofx)

               (swap! out-a conj n)

               (when (<= n 3)

                 {:a-frame/dispatch-sync
                  {schema/a-frame-event
                   {schema/a-frame-id ::dispatch-sync-fx-test-with-transitive-coeffects
                    :n (+ n 2)}}})))]

      (pr/let [_r (router/dispatch-sync
                   router
                   {schema/a-frame-event
                    {schema/a-frame-id ::dispatch-sync-fx-test-with-transitive-coeffects
                     :n 0}
                    schema/a-frame-init-coeffects {::BAR "bar"}})]

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
             (fn [cofx {n :n :as event}]
               ;; only the first event should have the ::BAR coeffect
               (if (= 0 n)
                 (assert-cofx
                  {schema/a-frame-coeffect-event event
                   ::BAR "bar"} cofx)
                 (assert-cofx
                  {schema/a-frame-coeffect-event event} cofx))

               (swap! out-a conj n)

               (when (<= n 3)

                 {:a-frame/dispatch-n
                  [{schema/a-frame-id ::dispatch-n-fx-test-without-transitive-coeffects
                    :n (+ n 2)}]})))]

      (pr/let [_r (router/dispatch-sync
                   router
                   {schema/a-frame-event
                    {schema/a-frame-id ::dispatch-n-fx-test-without-transitive-coeffects
                     :n 0}
                    schema/a-frame-init-coeffects {::BAR "bar"}})]

        (is (= [0 2 4] @out-a))

        ;; main stream should not be closed
        (is (not (stream.impl/closed? event-s))))))

  (testing "dispatches with transitive coeffects"
    (let [{event-s schema/a-frame-router-event-stream
           :as router} (router/create-router test-app-ctx {})
          out-a (atom [])

          _ (events/reg-event-fx
             ::dispatch-n-fx-test-with-transitive-coeffects
             (fn [cofx {n :n :as event}]

               ;; all events should have the ::BAR coeffect
               (assert-cofx
                {schema/a-frame-coeffect-event event
                 ::BAR "bar"} cofx)

               (swap! out-a conj n)

               (when (<= n 3)

                 {:a-frame/dispatch-n
                  [{schema/a-frame-event
                    {schema/a-frame-id ::dispatch-n-fx-test-with-transitive-coeffects
                     :n (+ n 2)}

                    schema/a-frame-event-transitive-coeffects? true}]})))]

      (pr/let [_r (router/dispatch-sync
                   router
                   {schema/a-frame-event
                    {schema/a-frame-id ::dispatch-n-fx-test-with-transitive-coeffects
                     :n 0}
                    schema/a-frame-init-coeffects {::BAR "bar"}})]

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
             (fn [cofx {n :n :as event}]
               ;; only the first event should have the ::BAR coeffect
               (if (= 0 n)
                 (assert-cofx
                  {schema/a-frame-coeffect-event event
                   ::BAR "bar"}
                  cofx)
                 (assert-cofx {schema/a-frame-coeffect-event event} cofx))

               (swap! out-a conj n)

               (when (<= n 3)

                 {:a-frame/dispatch-n-sync
                  [{schema/a-frame-event
                    {schema/a-frame-id ::dispatch-n-sync-fx-test-without-transitive-coeffects
                     :n (+ n 2)}

                    schema/a-frame-event-transitive-coeffects? false}]})))]

      (pr/let [_r (router/dispatch-sync
                   router
                   {schema/a-frame-event
                    {schema/a-frame-id ::dispatch-n-sync-fx-test-without-transitive-coeffects
                     :n 0}
                    schema/a-frame-init-coeffects {::BAR "bar"}})]

        (is (= [0 2 4] @out-a))

        ;; main stream should not be closed
        (is (not (stream.impl/closed? event-s))))))

  (testing "dispatches with transitive coeffects"
    (let [{event-s schema/a-frame-router-event-stream
           :as router} (router/create-router test-app-ctx {})
          out-a (atom [])

          _ (events/reg-event-fx
             ::dispatch-n-sync-fx-test-with-transitive-coeffects
             (fn [cofx {n :n :as event}]

               ;; all events should have the ::BAR coeffect
               (assert-cofx
                {schema/a-frame-coeffect-event event
                 ::BAR "bar"} cofx)

               (swap! out-a conj n)

               (when (<= n 3)

                 {:a-frame/dispatch-n-sync
                  [{schema/a-frame-event
                    {schema/a-frame-id ::dispatch-n-sync-fx-test-with-transitive-coeffects
                     :n (+ n 2)}}]})))]
      (pr/let [_r (router/dispatch-sync
                   router
                   {schema/a-frame-event
                    {schema/a-frame-id ::dispatch-n-sync-fx-test-with-transitive-coeffects
                     :n 0}
                    schema/a-frame-init-coeffects {::BAR "bar"}})]

        (is (= [0 2 4] @out-a))

        ;; main stream should not be closed
        (is (not (stream.impl/closed? event-s))))))
  )
