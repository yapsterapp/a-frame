(ns prpr3.a-frame.interceptor-chain-test
  (:require
   [prpr3.test :refer [deftest tlet testing is use-fixtures]]
   [prpr3.test.malli :as test.malli]
   [promesa.core :as pr]
   [prpr3.promise :as prpr]
   [prpr3.a-frame.schema :as af.schema]
   [prpr3.a-frame.registry.test :as registry.test]
   [prpr3.a-frame.interceptor-chain :as sut]))

(use-fixtures :once test.malli/instrument-fns-fixture)
(use-fixtures :each registry.test/reset-registry)

(defn epoch
  []
  #?(:clj (System/currentTimeMillis)
     :cljs (.now js/Date)))

(def empty-interceptor-context
  {af.schema/a-frame-app-ctx ::app
   af.schema/a-frame-router ::a-frame
   ::sut/queue []
   ::sut/stack '()
   ::sut/history []})

(deftest execute-empty-chain-test
  (pr/let
      [chain []
       input {:test (rand-int 9999)}
       r (sut/execute ::app ::a-frame chain input)]
    (is (= (merge
            empty-interceptor-context
            input)
           r))))

(deftest execute-single-interceptor-test
  (sut/register-interceptor
   ::execute-single-interceptor-test
   {::sut/enter (fn [x] (assoc x :entered? true))
    ::sut/leave (fn [x] (assoc x :left? true))})
  (pr/let
      [chain [::execute-single-interceptor-test]
       input {:test (rand-int 9999)}
       r (sut/execute ::app ::a-frame chain input)]
    (is (= (merge
            empty-interceptor-context
            input
            {:entered? true
             :left? true}
            {::sut/history [[::execute-single-interceptor-test ::sut/enter]
                            [::execute-single-interceptor-test ::sut/leave]]})
           r))))

(deftest execute-single-interceptor-with-data-test
  (sut/register-interceptor
   ::execute-single-interceptor-with-data-test
   {::sut/enter (fn [x data]
                  (is (= "foofoo" data))
                  (assoc x :entered? true))
    ::sut/leave (fn [x data]
                  (is (= "barbar" data))
                  (assoc x :left? true))})

  (pr/let
      [intc-with-data {::sut/key ::execute-single-interceptor-with-data-test
                       ::sut/data {::sut/enter-data #prpr3.ctx/path [::foo]
                                   ::sut/leave-data #prpr3.ctx/path [::bar]}}
       chain [intc-with-data]
       input {:test (rand-int 9999)
              ::foo "foofoo"
              ::bar "barbar"}
       r (sut/execute ::app ::a-frame chain input)]
    (is (= (merge
            empty-interceptor-context
            input
            {:entered? true
             :left? true}
            {::sut/history [[intc-with-data ::sut/enter "foofoo"]
                            [intc-with-data ::sut/leave "barbar"]]})
           r))))

(deftest execute-multiple-interceptors-test
  (doseq [[key inter] [[::execute-multiple-interceptors-test-A
                        {::sut/name ::copy-restore
                         ::sut/enter (fn [{t :test :as x}]
                                       (assoc x :test2 t))
                         ::sut/leave (fn [{t :test2 :as x}]
                                       (-> x
                                           (assoc :test t)
                                           (dissoc :test2)))}]

                       [::execute-multiple-interceptors-test-B
                        {::sut/name ::mult
                         ::sut/enter (fn [x]
                                       (update x :test * 2))}]

                       [::execute-multiple-interceptors-test-C
                        {::sut/name ::save-state
                         ::sut/enter (fn [x]
                                       (update
                                        x
                                        :states (fnil conj [])
                                        (-> (sut/dissoc-context-keys x)
                                            (dissoc :states))))}]

                       [::execute-multiple-interceptors-test-D
                        {::sut/name ::mark-leaving
                         ::sut/leave (fn [x]
                                       (assoc x :leaving-at (epoch)))}]]]
    (sut/register-interceptor key inter))

  (pr/let
      [chain [::execute-multiple-interceptors-test-A
              ::execute-multiple-interceptors-test-B
              ::execute-multiple-interceptors-test-C
              ::execute-multiple-interceptors-test-D]
       {t :test :as input} {:test (rand-int 9999)}
       epoch-before (epoch)
       r (sut/execute ::app ::a-frame chain input)
       epoch-after (epoch)]
    (do (is (= (merge
                empty-interceptor-context
                input
                {:states [{:test (* t 2) :test2 t}]}
                {::sut/history
                 [[::execute-multiple-interceptors-test-A ::sut/enter]
                  [::execute-multiple-interceptors-test-B ::sut/enter]
                  [::execute-multiple-interceptors-test-C ::sut/enter]
                  [::execute-multiple-interceptors-test-D ::sut/noop ::sut/enter]
                  [::execute-multiple-interceptors-test-D ::sut/leave]
                  [::execute-multiple-interceptors-test-C ::sut/noop ::sut/leave]
                  [::execute-multiple-interceptors-test-B ::sut/noop ::sut/leave]
                  [::execute-multiple-interceptors-test-A ::sut/leave]]})
               (dissoc r :leaving-at)))
        (is (<= epoch-before (:leaving-at r) epoch-after)))))

(deftest execute-promise-based-interceptors-test
  (doseq [[key inter] [[::execute-promise-based-interceptors-test-A
                        {::sut/enter (fn [x]
                                       (pr/resolved
                                        (assoc x :success true)))}]
                       [::execute-promise-based-interceptors-test-B
                        {::sut/leave (fn [x]
                                       (pr/chain
                                        (pr/resolved x)
                                        (fn [x]
                                          (assoc x :chain true))))}]
                       [::execute-promise-based-interceptors-test-C
                        {::sut/enter (fn [x]
                                       (pr/let [x' (assoc x :ddo true)]
                                         x'))}]]]
    (sut/register-interceptor key inter))

  (pr/let
      [chain [::execute-promise-based-interceptors-test-A
              ::execute-promise-based-interceptors-test-B
              ::execute-promise-based-interceptors-test-C]
       input {}
       r (sut/execute ::app ::a-frame chain input)]
    (is (=
         (merge
          empty-interceptor-context
          {:chain true
           :success true
           :ddo true

           ::sut/history
           [[::execute-promise-based-interceptors-test-A ::sut/enter]
            [::execute-promise-based-interceptors-test-B ::sut/noop ::sut/enter]
            [::execute-promise-based-interceptors-test-C ::sut/enter]
            [::execute-promise-based-interceptors-test-C ::sut/noop ::sut/leave]
            [::execute-promise-based-interceptors-test-B ::sut/leave]
            [::execute-promise-based-interceptors-test-A ::sut/noop ::sut/leave]]})
         r))))

(deftest execute-queue-alteration-test
  (doseq [[key inter] [[::execute-queue-alteration-test-late-arrival
                        {::sut/enter (fn [x] (assoc x :arrived :late))
                         ::sut/leave (fn [x] (assoc x :left :early))}]

                       [::execute-queue-alteration-test-alteration
                        {::sut/enter
                         (fn [x]
                           (prpr/always
                            (sut/enqueue
                             x
                             [::execute-queue-alteration-test-late-arrival])))}]]]
    (sut/register-interceptor key inter))

  (pr/let
      [chain [::execute-queue-alteration-test-alteration]
       input {}
       r (sut/execute ::app ::a-frame chain input)]
    (is (= (merge
            empty-interceptor-context
            {:arrived :late
             :left :early

             ::sut/history
             [[::execute-queue-alteration-test-alteration ::sut/enter]
              [::execute-queue-alteration-test-late-arrival ::sut/enter]
              [::execute-queue-alteration-test-late-arrival ::sut/leave]
              [::execute-queue-alteration-test-alteration ::sut/noop ::sut/leave]]})
           r))))

(deftest execute-stack-alteration-test
  (doseq [[key inter] [[::execute-stack-alteration-test-late-arrival
                        {::sut/enter (fn [x] (assoc x :arrived :late))
                         ::sut/leave (fn [x] (assoc x :left :early))}]

                       [::execute-stack-alteration-test-alteration
                        {::sut/leave (fn [x]
                                       (prpr/always
                                        (update
                                         x
                                         ::sut/stack
                                         conj
                                         ::execute-stack-alteration-test-late-arrival)))}]]]
    (sut/register-interceptor key inter))

  (pr/let
      [chain [::execute-stack-alteration-test-alteration]
       input {}
       r (sut/execute ::app ::a-frame chain input)]
    (is (= (merge
            empty-interceptor-context
            {:left :early

             ::sut/history
             [[::execute-stack-alteration-test-alteration ::sut/noop ::sut/enter]
              [::execute-stack-alteration-test-alteration ::sut/leave]
              [::execute-stack-alteration-test-late-arrival ::sut/leave]]})
           r))))

(deftest execute-error-handling-test
  (tlet [suppressed-errors (atom [])
         wrap-catch-execute (fn [chain input]
                              (reset! suppressed-errors [])
                              (prpr/catch-always
                               (pr/chain
                                (sut/execute*
                                 (fn [e] (throw e))
                                 (fn [xs] (swap! suppressed-errors concat xs))
                                 (sut/initiate ::app ::a-frame chain input))
                                (fn [r] [::ok r]))
                               (fn [e] [::error e])))]

        (testing "captures error in :enter interceptor"
          (doseq [[k i] [[::execute-error-handling-test-enter-boom
                          {::sut/enter
                           (fn [_] (throw (ex-info "boom" {:id ::boom})))}]
                         [::execute-error-handling-test-enter-unexpected-boom
                          {::sut/enter
                           (fn [_]
                             (throw (ex-info
                                     "unexpected-boom"
                                     {:id ::unexpected-boom})))}]]]
            (sut/register-interceptor k i))
          (pr/let
              [chain [::execute-error-handling-test-enter-boom
                      ::execute-error-handling-test-enter-unexpected-boom]
               [tag r] (wrap-catch-execute chain {})]
            (is (= ::error tag))
            (is (= {:id ::boom} (-> r ex-cause ex-data)))
            (is (empty? @suppressed-errors))))

        (testing "captures error in :leave interceptor"
          (doseq [[k i] [[::execute-error-handling-test-leave-unexpected-boom
                          {::sut/leave
                           (fn [_] (throw (ex-info "unexpected-boom"
                                                  {:id ::unexpected-boom})))}
                          ]

                         [::execute-error-handling-test-leave-boom
                          {::sut/leave
                           (fn [_] (throw (ex-info "boom" {:id ::boom})))}]]]
            (sut/register-interceptor k i))

          (pr/let
              [chain [::execute-error-handling-test-leave-unexpected-boom
                      ::execute-error-handling-test-leave-boom]
               [tag r] (wrap-catch-execute chain {})]
            (is (= ::error tag))
            (is (= {:id ::boom} (-> r ex-cause ex-data)))
            (is (empty? @suppressed-errors))))

        (testing "captures errors in error handlers"
          (let [left-with (atom nil)]

            (doseq [[k i]
                    [[::execute-error-handling-test-error-handler-error-left-with
                      {::sut/error (fn [x _] (reset! left-with ::error) x)
                       ::sut/leave (fn [x] (reset! left-with ::leave) x)}]

                     [::execute-error-handling-test-error-handler-error-error
                      {::sut/error (fn [_ _]
                                     (throw (ex-info
                                             "error-error"
                                             {:id ::error-error})))}]

                     [::execute-error-handling-test-error-handler-error-boom
                      {::sut/enter (fn [_] (throw
                                           (ex-info
                                            "boom"
                                            {:id ::boom})))}]]]

              (sut/register-interceptor k i))

            (pr/let
                [chain [::execute-error-handling-test-error-handler-error-left-with
                        ::execute-error-handling-test-error-handler-error-error
                        ::execute-error-handling-test-error-handler-error-boom]

                 [tag r] (wrap-catch-execute chain {})]
              (is (= ::error @left-with))
              (is (= ::error tag))
              (is (= {:id ::error-error} (-> r ex-cause ex-data)))
              (is (= [{:id ::boom}]
                     (map
                      (comp ex-data ex-cause)
                      @suppressed-errors))))))

        (testing "captures error promises"
          (doseq [[k i]
                  [[::execute-error-handling-test-error-promises-boom
                    {::sut/enter (fn [_] (pr/rejected
                                         (ex-info "boom"
                                                  {:id ::boom})))}]

                   [::execute-error-handling-test-error-promises-unexpected-boom
                    {::sut/enter (fn [_] (throw
                                         (ex-info
                                          "unexpected-boom"
                                          {:id ::unexpected-boom})))}]]]

            (sut/register-interceptor k i))

          (pr/let
              [chain
               [::execute-error-handling-test-error-promises-boom
                ::execute-error-handling-test-error-promises-unexpected-boom]
               [tag r] (wrap-catch-execute chain {})]
            (is (= ::error tag))
            (is (= {:id ::boom} (-> r ex-cause ex-data)))
            (is (empty? @suppressed-errors))))

        (testing "throws if error not cleared"
          (doseq [[k i] [[::execute-error-handline-not-cleared-clear
                          {::sut/error (fn [c _] (sut/clear-errors c))}]
                         [::execute-error-handling-not-cleared-boom
                          {::sut/enter (fn [_] (pr/rejected
                                               (ex-info "boom" {:fail :test})))}]]]
            (sut/register-interceptor k i))

          (pr/let
              [chain [::execute-error-handling-not-cleared-boom]
               [tag _r] (wrap-catch-execute chain {})]
            (is (= ::error tag)))
          (pr/let
              [chain [::execute-error-handline-not-cleared-clear
                      ::execute-error-handling-not-cleared-boom]
               [tag r] (wrap-catch-execute chain {})]
            (do (is (= ::ok tag))
                (is (= (merge
                        empty-interceptor-context
                        {::sut/history
                         [[::execute-error-handline-not-cleared-clear ::sut/noop ::sut/enter]
                          [::execute-error-handling-not-cleared-boom ::sut/enter]
                          [::execute-error-handling-not-cleared-boom ::sut/noop ::sut/error]
                          [::execute-error-handline-not-cleared-clear ::sut/error]]})
                       r)))))))

(deftest resume-test

  (tlet [throw?-a (atom true)]

        (doseq [[key inter]
                [[::resume-test-throw-once
                  {::sut/enter (fn [x]
                                 (if @throw?-a
                                   (do
                                     (reset! throw?-a false)
                                     (prn "THROW")
                                     (throw (ex-info "boo" {})))
                                   x))
                   ::sut/leave (fn [x] (assoc x :left? true))}]]]
          (sut/register-interceptor key inter))

        (testing "can resume after failure"
          (pr/let [[tag err] (prpr/merge-always
                              (sut/execute
                               ::app
                               ::a-frame
                               [::resume-test-throw-once]
                               {}))

                   _ (is (= tag ::prpr/error))

                   [resume-tag
                    {resume-left? :left?
                     :as _resume-val}] (prpr/merge-always
                                        (sut/resume ::app ::a-frame err))]

            (is (= ::prpr/ok resume-tag))
            (is (= true resume-left?))))))
