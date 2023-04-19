(ns prpr3.a-frame.cofx.data.tag-readers-test
  (:require
   [prpr3.test :as t :refer [deftest testing is]]
   [prpr3.a-frame.schema :as af.schema]
   [prpr3.a-frame.interceptor-chain.data :as data]
   [prpr3.a-frame.interceptor-chain.data.data-path :refer [->DataPath]]
   [prpr3.a-frame.cofx.data.tag-readers]))

(deftest coeffect-path-reader-test
  (is (= (->DataPath [af.schema/a-frame-coeffects :foo])
         #prpr3.cofx/path [:foo]))
  (is (= (->DataPath [af.schema/a-frame-coeffects  :foo])
         #prpr3.cofx/p [:foo])))

(deftest event-path-reader-test
  (is (= (->DataPath [af.schema/a-frame-coeffects
                      af.schema/a-frame-coeffect-event
                      :foo])
         #prpr3.cofx/event-path [:foo]))
  (is (= (->DataPath [af.schema/a-frame-coeffects
                      af.schema/a-frame-coeffect-event
                      :foo])
         #prpr3.cofx/evp [:foo])))

(deftest resolve-cofx-data-test
  (testing "coeffects path"
    (is (= 100
           (data/resolve-data
            #prpr3.cofx/path [:foo :bar]
            {af.schema/a-frame-coeffects {:foo {:bar 100}}}))))
  (testing "event path"
    (is (= 100
           (data/resolve-data
            #prpr3.cofx/event-path [1 ::foofoo]
            {af.schema/a-frame-coeffects
             {af.schema/a-frame-coeffect-event [::foo {::foofoo 100}]}}))))
  (testing "mixed literals and paths"
    (is (= {:some-key ["foo" 200]
            :other-key {:foo 100
                        :bar "bar"}}

           (data/resolve-data
            {:some-key [#prpr3.cofx/path [:a]
                        #prpr3.cofx/path [:b]]
             :other-key {:foo #prpr3.cofx/event-path [1]
                         :bar #prpr3.cofx/event-path [2 ::evdata]}}

            {af.schema/a-frame-coeffects
             {:a "foo"
              :b 200

              af.schema/a-frame-coeffect-event
              [::ev 100 {::evdata "bar"}]}})))))
