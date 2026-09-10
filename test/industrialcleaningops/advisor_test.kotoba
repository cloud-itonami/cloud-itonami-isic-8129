(ns industrialcleaningops.advisor-test
  "Unit tests of `industrialcleaningops.advisor` proposal generation."
  (:require [clojure.test :refer [deftest is testing]]
            [industrialcleaningops.advisor :as adv]
            [industrialcleaningops.store :as store]))

(def db (store/seed-db))

(deftest propose-service-record-shape
  (testing "service-record proposal has correct shape and fields"
    (let [p (adv/infer db {:op :log-service-record
                           :site-id "site-1"
                           :patch {:areas-cleaned 12 :chemical-usage-liters 8.5 :labor-hours 6}})]
      (is (= :log-service-record (:op p)))
      (is (= "site-1" (:site-id p)))
      (is (= :propose (:effect p)))
      (is (<= 0 (:confidence p) 1))
      (is (map? (:value p)))
      (is (contains? (:value p) :site-id)))))

(deftest propose-service-operation-shape
  (testing "service-operation proposal has correct shape"
    (let [p (adv/infer db {:op :schedule-service-operation
                           :site-id "site-2"
                           :patch {:crew "night-shift-degreasing" :date "2026-07-20"}})]
      (is (= :schedule-service-operation (:op p)))
      (is (= "site-2" (:site-id p)))
      (is (= :propose (:effect p))))))

(deftest propose-supply-order-shape
  (testing "supply-order proposal has correct shape"
    (let [p (adv/infer db {:op :coordinate-supply-order
                           :site-id "site-1"
                           :patch {:item "industrial degreaser restock" :quantity 40 :estimated-cost 380.0
                                   :vendor-id "vendor-1"}})]
      (is (= :coordinate-supply-order (:op p)))
      (is (= :propose (:effect p)))
      (is (string? (:summary p)))
      (is (= "vendor-1" (get-in p [:value :vendor-id]))))))

(deftest propose-safety-concern-shape
  (testing "safety-concern proposal always escalates"
    (let [p (adv/infer db {:op :flag-safety-concern
                           :site-id "site-1"
                           :patch {:concern "confined-space entry planned, low-oxygen reading near degreasing chemical storage"}})]
      (is (= :flag-safety-concern (:op p)))
      (is (= :propose (:effect p)))
      (is (string? (:summary p))))))

(deftest all-proposals-effect-is-always-propose
  (testing "every proposal type has :effect :propose, never direct actuation"
    (doseq [op [:log-service-record :schedule-service-operation :coordinate-supply-order
                :flag-safety-concern]]
      (let [p (adv/infer db {:op op :site-id "site-1" :patch {}})]
        (is (= :propose (:effect p))
            (str "op " op " must have :effect :propose"))))))

(deftest rationale-string-is-present
  (testing "every proposal has a rationale explaining the advisor's thinking"
    (doseq [op [:log-service-record :schedule-service-operation :coordinate-supply-order
                :flag-safety-concern]]
      (let [p (adv/infer db {:op op :site-id "site-1" :patch {}})]
        (is (string? (:rationale p))
            (str "op " op " must have a :rationale string"))))))
