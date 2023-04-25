(ns a-frame.cofx.data.tag-readers-test
  (:require
   [promisespromises.test :as t :refer [deftest testing is]]
   [a-frame.schema :as af.schema]
   [a-frame.interceptor-chain.data :as data]
   [a-frame.interceptor-chain.data.data-path :refer [data-path]]
   [a-frame.cofx.data.tag-readers]))

(deftest coeffect-path-reader-test
  (is (= (data-path [af.schema/a-frame-coeffects :foo])
         #a-frame.cofx/path [:foo]))
  (is (= (data-path [af.schema/a-frame-coeffects  :foo])
         #af.cofx/p [:foo])))

(deftest event-path-reader-test
  (is (= (data-path [af.schema/a-frame-coeffects
                      af.schema/a-frame-coeffect-event
                      :foo])
         #a-frame.cofx/event-path [:foo]))
  (is (= (data-path [af.schema/a-frame-coeffects
                      af.schema/a-frame-coeffect-event
                      :foo])
         #af.cofx/evp [:foo])))

(deftest resolve-cofx-data-test
  (testing "coeffects path"
    (is (= 100
           (data/resolve-data
            #a-frame.cofx/path [:foo :bar]
            {af.schema/a-frame-coeffects {:foo {:bar 100}}}))))
  (testing "event path"
    (is (= 100
           (data/resolve-data
            #a-frame.cofx/event-path [1 ::foofoo]
            {af.schema/a-frame-coeffects
             {af.schema/a-frame-coeffect-event [::foo {::foofoo 100}]}}))))
  (testing "mixed literals and paths"
    (is (= {:some-key ["foo" 200]
            :other-key {:foo 100
                        :bar "bar"}}

           (data/resolve-data
            {:some-key [#a-frame.cofx/path [:a]
                        #a-frame.cofx/path [:b]]
             :other-key {:foo #a-frame.cofx/event-path [1]
                         :bar #a-frame.cofx/event-path [2 ::evdata]}}

            {af.schema/a-frame-coeffects
             {:a "foo"
              :b 200

              af.schema/a-frame-coeffect-event
              [::ev 100 {::evdata "bar"}]}})))))
