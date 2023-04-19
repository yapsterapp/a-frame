(ns prpr3.a-frame.interceptor-chain.data-test
  (:require
   [prpr3.test :refer [deftest testing is]]
   [prpr3.a-frame.interceptor-chain.data.data-path :refer [->DataPath]]
   [prpr3.a-frame.interceptor-chain.data :as sut]))


(deftest resolve-data-test
  (testing "literal values"
    (is (= {:foo 10 :bar [10 20]}
           (sut/resolve-data
            {:foo 10 :bar [10 20]}
            {})))
    (is (= [:a 2 :foo]
           (sut/resolve-data
            [:a 2 :foo]
            {}))))
  (testing "data path"
    (is (= 100
           (sut/resolve-data
            (->DataPath [:foo :bar])
            {:foo {:bar 100}}))))

  (testing "mixed literals and paths"
    (is (= {:some-key ["foo" 200]
            :other-key {:foo 100
                        :bar "bar"}}

           (sut/resolve-data
            {:some-key [(->DataPath [:a])
                        (->DataPath [:b])]
             :other-key {:foo (->DataPath [::blah 1])
                         :bar (->DataPath [::blah 2 ::evdata])}}

            {:a "foo"
             :b 200

             ::blah
             [::ev 100 {::evdata "bar"}]})))))
