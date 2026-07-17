(ns foundrycoord.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [foundrycoord.actor :as actor]
            [foundrycoord.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-moulder! st {:moulder-id "moulder-1" :name "Kobo Yamada"})
    (store/register-foundry! st {:foundry-id "F-1" :name "Kobo Foundry" :max-supply-cost 2000})
    st))

(deftest commits-a-registered-work-log
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:moulder-id "moulder-1" :op :log-work-record :stake :low
                  :foundry-id "F-1" :task "casting-batch progress log"}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "moulder-1"))))))

(deftest holds-an-unregistered-foundry-proposal
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:moulder-id "moulder-1" :op :log-work-record :stake :low
                  :foundry-id "F-ghost" :task "casting-batch progress log"}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "moulder-1")))))

(deftest interrupts-then-approves-safety-concern-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:moulder-id "moulder-1" :op :flag-safety-concern :stake :low
                  :foundry-id "F-1" :hazard-type :heat-exposure-risk}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "moulder-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "moulder-1")))))))

(deftest holds-a-scope-excluded-op-even-at-high-confidence
  (testing "an actor run can never commit a proposal that would finalize a casting/pouring-execution decision, regardless of disposition path"
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})
          request {:moulder-id "moulder-1" :op :finalize-pour-decision :stake :low
                    :foundry-id "F-1" :task "pour decision"}
          result (actor/run-request! graph request {} "thread-4")]
      (is (= :done (:status result)))
      (is (= :hold (:disposition (:state result))))
      (is (empty? (store/records-of st "moulder-1"))))))
