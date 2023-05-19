(ns a-frame.interceptor-chain-test
  (:require
   [promisespromises.test :refer [deftest tlet testing is use-fixtures]]
   [promesa.core :as pr]
   [promisespromises.promise :as prpr]
   [a-frame.schema :as af.schema]
   [a-frame.registry.test :as registry.test]
   [a-frame.interceptor-chain :as sut]))

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
   {::sut/enter (fn [x icspec]
                  (is (= {::sut/key ::execute-single-interceptor-test}
                         icspec))
                  (assoc x :entered? true))
    ::sut/leave (fn [x icspec]
                  (is (= {::sut/key ::execute-single-interceptor-test}
                         icspec))
                  (assoc x :left? true))})
  (pr/let
      [chain [::execute-single-interceptor-test]
       input {:test (rand-int 9999)}
       r (sut/execute ::app ::a-frame chain input)]
    (is (= (merge
            empty-interceptor-context
            input
            {:entered? true
             :left? true}
            {::sut/history [[::execute-single-interceptor-test ::sut/enter ::sut/execute :_ ::sut/success]
                            [::execute-single-interceptor-test ::sut/leave ::sut/execute :_ ::sut/success]]})
           r))))

(deftest execute-multiple-interceptors-test
  (doseq [[key inter] [[::execute-multiple-interceptors-test-A
                        {::sut/name ::copy-restore
                         ::sut/enter (fn [{t :test :as x} _icspec]
                                       (assoc x :test2 t))
                         ::sut/leave (fn [{t :test2 :as x} _icspec]
                                       (-> x
                                           (assoc :test t)
                                           (dissoc :test2)))}]

                       [::execute-multiple-interceptors-test-B
                        {::sut/name ::mult
                         ::sut/enter (fn [x _icspec]
                                       (update x :test * 2))}]

                       [::execute-multiple-interceptors-test-C
                        {::sut/name ::save-state
                         ::sut/enter (fn [x _icspec]
                                       (update
                                        x
                                        :states (fnil conj [])
                                        (-> (sut/dissoc-context-keys x)
                                            (dissoc :states))))}]

                       [::execute-multiple-interceptors-test-D
                        {::sut/name ::mark-leaving
                         ::sut/leave (fn [x _icspec]
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
                 [[::execute-multiple-interceptors-test-A ::sut/enter ::sut/execute :_ ::sut/success]
                  [::execute-multiple-interceptors-test-B ::sut/enter ::sut/execute :_ ::sut/success]
                  [::execute-multiple-interceptors-test-C ::sut/enter ::sut/execute :_ ::sut/success]
                  [::execute-multiple-interceptors-test-D ::sut/enter ::sut/noop :_ ::sut/success]
                  [::execute-multiple-interceptors-test-D ::sut/leave ::sut/execute :_ ::sut/success]
                  [::execute-multiple-interceptors-test-C ::sut/leave ::sut/noop :_ ::sut/success]
                  [::execute-multiple-interceptors-test-B ::sut/leave ::sut/noop :_ ::sut/success]
                  [::execute-multiple-interceptors-test-A ::sut/leave ::sut/execute :_ ::sut/success]]})
               (dissoc r :leaving-at)))
        (is (<= epoch-before (:leaving-at r) epoch-after)))))

(deftest execute-promise-based-interceptors-test
  (doseq [[key inter] [[::execute-promise-based-interceptors-test-A
                        {::sut/enter (fn [x _icspec]
                                       (pr/resolved
                                        (assoc x :success true)))}]
                       [::execute-promise-based-interceptors-test-B
                        {::sut/leave (fn [x _icspec]
                                       (pr/chain
                                        (pr/resolved x)
                                        (fn [x]
                                          (assoc x :chain true))))}]
                       [::execute-promise-based-interceptors-test-C
                        {::sut/enter (fn [x _icspec]
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
           [[::execute-promise-based-interceptors-test-A ::sut/enter ::sut/execute :_ ::sut/success]
            [::execute-promise-based-interceptors-test-B ::sut/enter ::sut/noop :_ ::sut/success]
            [::execute-promise-based-interceptors-test-C ::sut/enter ::sut/execute :_ ::sut/success]
            [::execute-promise-based-interceptors-test-C ::sut/leave ::sut/noop :_ ::sut/success]
            [::execute-promise-based-interceptors-test-B ::sut/leave ::sut/execute :_ ::sut/success]
            [::execute-promise-based-interceptors-test-A ::sut/leave ::sut/noop :_ ::sut/success]]})
         r))))

(deftest execute-queue-alteration-test
  (doseq [[key inter] [[::execute-queue-alteration-test-late-arrival
                        {::sut/enter (fn [x _icspec] (assoc x :arrived :late))
                         ::sut/leave (fn [x _icspec] (assoc x :left :early))}]

                       [::execute-queue-alteration-test-alteration
                        {::sut/enter
                         (fn [x _icspec]
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
             [[::execute-queue-alteration-test-alteration ::sut/enter ::sut/execute :_ ::sut/success]
              [::execute-queue-alteration-test-late-arrival ::sut/enter ::sut/execute :_ ::sut/success]
              [::execute-queue-alteration-test-late-arrival ::sut/leave ::sut/execute :_ ::sut/success]
              [::execute-queue-alteration-test-alteration ::sut/leave ::sut/noop :_ ::sut/success]]})
           r))))

(deftest execute-stack-alteration-test
  (doseq [[key inter] [[::execute-stack-alteration-test-late-arrival
                        {::sut/enter (fn [x _icspec] (assoc x :arrived :late))
                         ::sut/leave (fn [x _icspec] (assoc x :left :early))}]

                       [::execute-stack-alteration-test-alteration
                        {::sut/leave
                         (fn [{[hd & rst :as _stack] ::sut/stack
                               :as x}
                              _icspec]
                           (prpr/always
                            (assoc
                             x
                             ::sut/stack

                             ;; add the new interceptor at second position
                             ;; in the stack list - the current interceptor
                             ;; will be at first position and will be
                             ;; removed after it has run
                             (apply
                              list
                              hd
                              ::execute-stack-alteration-test-late-arrival
                              rst)
                             )))}]]]
    (sut/register-interceptor key inter))

  (pr/let
      [chain [::execute-stack-alteration-test-alteration]
       input {}
       r (sut/execute ::app ::a-frame chain input)]
    (is (= (merge
            empty-interceptor-context
            {:left :early

             ::sut/history
             [[::execute-stack-alteration-test-alteration ::sut/enter ::sut/noop :_ ::sut/success]
              [::execute-stack-alteration-test-alteration ::sut/leave ::sut/execute :_ ::sut/success]
              [::execute-stack-alteration-test-late-arrival ::sut/leave ::sut/execute :_ ::sut/success]]})
           r)))
  )

(deftest execute-error-handling-test
  (tlet [wrap-catch-execute (fn [chain input]
                              (prpr/catch-always
                               (pr/chain
                                (sut/execute*
                                 (sut/initiate ::app ::a-frame chain input))
                                (fn [r] [::ok r]))
                               (fn [e] [::error e])))]

        (testing "captures error in :enter interceptor"
          (doseq [[k i] [[::execute-error-handling-test-enter-boom
                          {::sut/enter
                           (fn [_ _] (throw (ex-info "boom" {:id ::boom})))}]
                         [::execute-error-handling-test-enter-unexpected-boom
                          {::sut/enter
                           (fn [_ _]
                             (throw (ex-info
                                     "unexpected-boom"
                                     {:id ::unexpected-boom})))}]]]
            (sut/register-interceptor k i))
          (pr/let
              [chain [::execute-error-handling-test-enter-boom
                      ::execute-error-handling-test-enter-unexpected-boom]
               [tag r] (wrap-catch-execute chain {})]
            (is (= ::error tag))
            (is (= {:id ::boom} (-> r ex-cause ex-data)))))

        (testing "captures error in :leave interceptor"
          (doseq [[k i] [[::execute-error-handling-test-leave-unexpected-boom
                          {::sut/leave
                           (fn [_ _] (throw (ex-info "unexpected-boom"
                                                     {:id ::unexpected-boom})))}
                          ]

                         [::execute-error-handling-test-leave-boom
                          {::sut/leave
                           (fn [_ _] (throw (ex-info "boom" {:id ::boom})))}]]]
            (sut/register-interceptor k i))

          (pr/let
              [chain [::execute-error-handling-test-leave-unexpected-boom
                      ::execute-error-handling-test-leave-boom]
               [tag r] (wrap-catch-execute chain {})]
            (is (= ::error tag))
            (is (= {:id ::boom} (-> r ex-cause ex-data)))))

        (testing "captures errors in error handlers"
          (let [left-with (atom nil)]

            (doseq [[k i]
                    [[::execute-error-handling-test-error-handler-error-left-with
                      {::sut/error
                       (fn [x _ err] (reset! left-with [::error err]) x)

                       ::sut/leave
                       (fn [x _] (reset! left-with [::leave]) x)}]

                     [::execute-error-handling-test-error-handler-error-error
                      {::sut/error (fn [_ _ _]
                                     (throw (ex-info
                                             "error-error"
                                             {:id ::error-error})))}]

                     [::execute-error-handling-test-error-handler-error-boom
                      {::sut/enter (fn [_ _] (throw
                                              (ex-info
                                               "boom"
                                               {:id ::boom})))}]]]

              (sut/register-interceptor k i))

            (pr/let
                [chain [::execute-error-handling-test-error-handler-error-left-with
                        ::execute-error-handling-test-error-handler-error-error
                        ::execute-error-handling-test-error-handler-error-boom]

                 [tag _r] (wrap-catch-execute chain {})

                 [lw-tag lw-val] @left-with]
              (is (= ::ok tag))
              (is (= ::error lw-tag))
              (is (= {:id ::error-error} (some-> lw-val ex-cause ex-data))))))

        (testing "captures error promises"
          (doseq [[k i]
                  [[::execute-error-handling-test-error-promises-boom
                    {::sut/enter (fn [_ _] (pr/rejected
                                            (ex-info "boom"
                                                     {:id ::boom})))}]

                   [::execute-error-handling-test-error-promises-unexpected-boom
                    {::sut/enter (fn [_ _] (throw
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
            (is (= {:id ::boom} (-> r ex-cause ex-data)))))

        (testing "throws if error not cleared"
          (doseq [[k i] [[::execute-error-handline-not-cleared-clear
                          {::sut/error (fn [c _ _] c)}]
                         [::execute-error-handling-not-cleared-boom
                          {::sut/enter (fn [_ _] (pr/rejected
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

            (is (= ::ok tag))
            (is (= (merge
                    empty-interceptor-context
                    {::sut/history
                     [[::execute-error-handline-not-cleared-clear ::sut/enter ::sut/noop :_ ::sut/success]
                      [::execute-error-handling-not-cleared-boom ::sut/enter ::sut/execute :_ ::sut/error]
                      [::execute-error-handling-not-cleared-boom ::sut/error ::sut/noop :_ ::sut/success]
                      [::execute-error-handline-not-cleared-clear ::sut/error ::sut/execute :_ ::sut/success]]})
                   r))))))

(deftest resume-test

  (tlet [throw?-a (atom true)]

        (doseq [[key inter]
                [[::resume-test-throw-once
                  {::sut/enter (fn [x _]
                                 (if @throw?-a
                                   (do
                                     (reset! throw?-a false)
                                     ;; (prn "THROW")
                                     (throw (ex-info "boo" {})))
                                   x))
                   ::sut/leave (fn [x _] (assoc x :left? true))}]]]
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
