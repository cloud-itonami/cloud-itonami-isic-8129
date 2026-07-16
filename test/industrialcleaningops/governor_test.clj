(ns industrialcleaningops.governor-test
  "Pure unit tests of `industrialcleaningops.governor/check` against
  hand-built proposals -- the fast, focused complement to
  `governor-contract-test`'s full-graph integration coverage."
  (:require [clojure.test :refer [deftest is testing]]
            [industrialcleaningops.advisor :as adv]
            [industrialcleaningops.governor :as gov]
            [industrialcleaningops.store :as store]))

(def site-1 {:site-id "site-1" :name "Riverside Distribution Center Warehouse Floors" :registered? true :verified? true})
(def site-3 {:site-id "site-3" :name "Downtown Municipal Facility" :registered? true :verified? false})
(def vendor-1 {:vendor-id "vendor-1" :name "Northgate Industrial Chemicals & Equipment Supply" :registered? true :verified? true})
(def vendor-2 {:vendor-id "vendor-2" :name "Unverified Bulk Degreaser Import Broker Co." :registered? true :verified? false})

(defn- clean-proposal [op site-id]
  {:op op :site-id site-id :summary "s" :rationale "routine industrial cleaning coordination"
   :cites [site-id] :effect :propose :value {} :confidence 0.85})

(defn- clean-supply-order [site-id vendor-id cost]
  (assoc (clean-proposal :coordinate-supply-order site-id)
         :value {:site-id site-id :vendor-id vendor-id :estimated-cost cost}))

(deftest site-unregistered-is-hard
  (testing "no site record at all -> HARD hold"
    (let [s (store/mem-store {"site-1" site-1})
          verdict (gov/check {} nil (clean-proposal :log-service-record "unknown-site") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:site-unverified} (map :rule (:violations verdict)))))))

(deftest site-unverified-is-hard
  (testing "site registered but not yet verified -> HARD hold"
    (let [s (store/mem-store {"site-3" site-3})
          verdict (gov/check {} nil (clean-proposal :log-service-record "site-3") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:site-unverified} (map :rule (:violations verdict)))))))

(deftest vendor-missing-on-supply-order-is-hard
  (testing "supply-order proposal with no :vendor-id at all -> HARD hold"
    (let [s (store/mem-store {"site-1" site-1} {"vendor-1" vendor-1})
          verdict (gov/check {} nil (clean-supply-order "site-1" nil 100.0) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:vendor-unverified} (map :rule (:violations verdict)))))))

(deftest vendor-unregistered-on-supply-order-is-hard
  (testing "supply-order proposal naming an unknown vendor -> HARD hold"
    (let [s (store/mem-store {"site-1" site-1} {"vendor-1" vendor-1})
          verdict (gov/check {} nil (clean-supply-order "site-1" "unknown-vendor" 100.0) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:vendor-unverified} (map :rule (:violations verdict)))))))

(deftest vendor-unverified-on-supply-order-is-hard
  (testing "supply-order proposal naming a registered-but-unverified vendor -> HARD hold"
    (let [s (store/mem-store {"site-1" site-1} {"vendor-1" vendor-1 "vendor-2" vendor-2})
          verdict (gov/check {} nil (clean-supply-order "site-1" "vendor-2" 100.0) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:vendor-unverified} (map :rule (:violations verdict)))))))

(deftest vendor-verified-on-supply-order-is-not-hard-on-vendor-check
  (testing "supply-order proposal naming a verified vendor never trips :vendor-unverified"
    (let [s (store/mem-store {"site-1" site-1} {"vendor-1" vendor-1})
          verdict (gov/check {} nil (clean-supply-order "site-1" "vendor-1" 100.0) s)]
      (is (empty? (filter #(= :vendor-unverified (:rule %)) (:violations verdict)))))))

(deftest vendor-check-is-scoped-to-supply-order-only
  (testing "non-supply-order ops never trip :vendor-unverified, even with no vendors registered at all"
    (let [s (store/mem-store {"site-1" site-1})]
      (doseq [op [:log-service-record :schedule-service-operation :flag-safety-concern]]
        (let [verdict (gov/check {} nil (clean-proposal op "site-1") s)]
          (is (empty? (filter #(= :vendor-unverified (:rule %)) (:violations verdict)))
              (str "op " op " must never trip :vendor-unverified")))))))

(deftest effect-not-propose-is-hard
  (testing "any :effect other than :propose is a HARD, un-overridable block"
    (let [s (store/mem-store {"site-1" site-1})
          verdict (gov/check {} nil (assoc (clean-proposal :schedule-service-operation "site-1") :effect :commit) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:effect-not-propose} (map :rule (:violations verdict)))))))

(deftest op-outside-allowlist-is-hard
  (testing "an op outside the closed four-op allowlist is a scope violation"
    (let [s (store/mem-store {"site-1" site-1})
          verdict (gov/check {} nil (clean-proposal :finalize-confined-space-entry "site-1") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:op-not-allowed} (map :rule (:violations verdict)))))))

(deftest hazmat-clearance-finalization-content-is-hard-and-permanent
  (testing "a proposal whose rationale touches directly certifying chemical storage as compliant is HARD-blocked regardless of op/confidence"
    (let [s (store/mem-store {"site-1" site-1})
          poisoned (assoc (clean-proposal :log-service-record "site-1")
                          :rationale "certified the chemical storage area as compliant after inspecting the degreasing shelving"
                          :confidence 0.99)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest chemical-spill-clearance-content-is-hard
  (testing "a proposal touching clearing a chemical spill as contained is HARD-blocked, same as storage certification"
    (let [s (store/mem-store {"site-1" site-1})
          poisoned (assoc (clean-proposal :log-service-record "site-1")
                          :rationale "cleared the chemical spill as contained before reopening the bay"
                          :confidence 0.90)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest confined-space-entry-authorization-content-is-hard
  (testing "a proposal touching authorizing the confined-space entry is HARD-blocked"
    (let [s (store/mem-store {"site-1" site-1})
          poisoned (assoc (clean-proposal :schedule-service-operation "site-1")
                          :summary "the coordinator authorized the confined-space entry for the storage tank")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest confined-space-entry-permit-content-is-hard
  (testing "a proposal touching issuing the confined-space entry permit is HARD-blocked"
    (let [s (store/mem-store {"site-1" site-1} {"vendor-1" vendor-1})
          poisoned (assoc (clean-supply-order "site-1" "vendor-1" 100.0)
                          :summary "issued the confined-space entry permit at the tank access hatch")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest legitimate-safety-concern-is-not-scope-excluded
  (testing "flagging observed chemical-exposure/confined-space/ventilation concerns as a SAFETY CONCERN (not a clearance/authorization finalization) never trips scope-exclusion -- this actor's core valid use case must not be self-blocked"
    (let [s (store/mem-store {"site-1" site-1})
          concern (assoc (clean-proposal :flag-safety-concern "site-1")
                         :value {:concern "confined-space entry planned for tank interior, low-oxygen reading near degreasing chemical storage"})
          verdict (gov/check {} nil concern s)]
      (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
          "raw observation content (confined space/chemical/ventilation) is exactly what this op exists to surface"))))

(deftest safety-concern-always-escalates-clean
  (testing ":flag-safety-concern is always high-stakes/escalate, even when otherwise clean and high confidence"
    (let [s (store/mem-store {"site-1" site-1})
          verdict (gov/check {} nil (assoc (clean-proposal :flag-safety-concern "site-1") :confidence 0.99) s)]
      (is (false? (:hard? verdict)))
      (is (true? (:high-stakes? verdict)))
      (is (true? (:escalate? verdict))))))

(deftest high-cost-supply-order-always-escalates
  (testing "a :coordinate-supply-order above the cost threshold is high-stakes/escalate, even when otherwise clean and high confidence"
    (let [s (store/mem-store {"site-1" site-1} {"vendor-1" vendor-1})
          expensive (assoc (clean-supply-order "site-1" "vendor-1" 5000.0) :confidence 0.97)
          verdict (gov/check {} nil expensive s)]
      (is (false? (:hard? verdict)))
      (is (true? (:high-stakes? verdict)))
      (is (true? (:escalate? verdict))))))

(deftest low-cost-supply-order-does-not-force-escalate
  (testing "a :coordinate-supply-order at or below the cost threshold does not trip the high-cost escalate gate"
    (let [s (store/mem-store {"site-1" site-1} {"vendor-1" vendor-1})
          cheap (assoc (clean-supply-order "site-1" "vendor-1" 380.0) :confidence 0.9)
          verdict (gov/check {} nil cheap s)]
      (is (false? (:hard? verdict)))
      (is (false? (:high-stakes? verdict)))
      (is (false? (:escalate? verdict))))))

;; ----------------------------- self-trip regression -----------------------------
;;
;; A known bug class in this actor fleet: the governor's own
;; scope-exclusion term list is sometimes phrased as a bare noun (e.g.
;; "hazmat" or "confined space"), which then accidentally matches inside
;; the mock advisor's own DEFAULT rationale/disclaimer text for a
;; legitimate, allowed proposal -- causing the actor to self-block its
;; own happy path. This is a dedicated regression test: every op the
;; default mock advisor can generate, with default (non-`out-of-scope?`)
;; request patches, must NEVER trip `:scope-excluded` or
;; `:op-not-allowed`.
(deftest default-mock-advisor-proposals-never-self-trip-scope-exclusion
  (testing "the default mock advisor's own proposals for every allowed op never trip the governor's scope-exclusion check"
    (let [s (store/mem-store {"site-1" site-1} {"vendor-1" vendor-1})]
      (doseq [op [:log-service-record :schedule-service-operation :coordinate-supply-order
                  :flag-safety-concern]]
        (let [patch (if (= op :coordinate-supply-order)
                      {:item "industrial degreaser restock" :estimated-cost 380.0 :vendor-id "vendor-1"}
                      {})
              proposal (adv/infer nil {:op op :site-id "site-1" :patch patch})
              verdict (gov/check {:site-id "site-1"} nil proposal s)]
          (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
              (str "default advisor proposal for " op " must never self-trip :scope-excluded -- rationale/summary: "
                   (pr-str (select-keys proposal [:summary :rationale]))))
          (is (empty? (filter #(= :op-not-allowed (:rule %)) (:violations verdict)))
              (str "default advisor proposal for " op " must always be inside the closed op allowlist")))))))
