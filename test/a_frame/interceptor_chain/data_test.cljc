(ns a-frame.interceptor-chain.data-test
  (:require
   [promisespromises.test :refer [deftest testing is]]
   [promisespromises.promise :as prpr]
   [a-frame.interceptor-chain.data.data-path :refer [data-path]]
   [a-frame.interceptor-chain.data :as sut]))

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
            (data-path [:foo :bar])
            {:foo {:bar 100}}))))

  (testing "mixed literals and paths"
    (is (= {:some-key ["foo" 200]
            :other-key {:foo 100
                        :bar "bar"}}

           (sut/resolve-data
            {:some-key [(data-path [:a])
                        (data-path [:b])]
             :other-key {:foo (data-path [::blah 1])
                         :bar (data-path [::blah 2 ::evdata])}}

            {:a "foo"
             :b 200

             ::blah
             [::ev 100 {::evdata "bar"}]}))))

  (testing "nil value errors"
    (let [[t v] (try
                  [::ok
                   (sut/resolve-data
                    {:some-key (data-path ::foo)}
                    {::foo nil})]
                  (catch #?(:clj Exception :cljs :default) e
                    [::error e]))]

      (is (= ::error t))
      (is (= "nil data" (ex-message v))))

    (testing "unless maybe?"
      (is (=
           {:some-key nil}
           (sut/resolve-data
            {:some-key (data-path ::foo true)}
            {::foo nil}))))))
