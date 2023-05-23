(ns a-frame.util.coeffects-test
  #?(:cljs (:require-macros [a-frame.util.coeffects-test]))
  (:require
   [promisespromises.test :refer [is]]))

(defmacro assert-cofx
  "check that the `cofx` contain all the `asserted-cofx`

   use a macro so that test reporting points to the call site"

  [assert-cofx cofx]
  `(is (= ~assert-cofx
          (select-keys ~cofx (keys ~assert-cofx)))))
