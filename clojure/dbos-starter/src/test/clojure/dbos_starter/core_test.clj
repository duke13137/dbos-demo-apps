(ns dbos-starter.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [dbos-starter.core :refer [run-example-workflow steps-event]]))


(deftest example-workflow-test
  (testing "executes all steps in order"
    (let [calls (atom [])
          run-step (fn [_step step-name]
                     (swap! calls conj [:run-step step-name]))
          set-event (fn [event value]
                      (swap! calls conj [:set-event event value]))]
      (run-example-workflow run-step set-event "test-workflow-id")
      (is (= [[:run-step "stepOne"]
              [:set-event steps-event (Integer/valueOf 1)]
              [:run-step "stepTwo"]
              [:set-event steps-event (Integer/valueOf 2)]
              [:run-step "stepThree"]
              [:set-event steps-event (Integer/valueOf 3)]]
             @calls)))))
