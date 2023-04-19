(ns prpr3.a-frame.registry.test
  (:require
   [prpr3.a-frame.registry :as registry]))

#?(:cljs
   (def snapshot-a (atom nil)))

(def reset-registry
  "test fixture to restore the registry to it's prior state
   after running tests, which helps in two ways

   1. the registry doesn't get polluted with test handlers
   2. if used as an :each fixture, then tests don't need to
      use unique handler keys"
  #?(:clj
     (fn [f]
       (let [snapshot @registry/registry]
         (f)
         (reset! registry/registry snapshot)))

     :cljs
     {:before (fn [] (reset! snapshot-a @registry/registry))
      :after (fn [] (reset! registry/registry @snapshot-a))}))
