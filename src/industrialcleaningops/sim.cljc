(ns industrialcleaningops.sim
  "Demo driver -- `clojure -M:run`. Walks a clean service-record logging
  request through intake -> advise -> govern -> decide -> approval ->
  commit at phase 1 (assisted-logging, always approval), then re-runs the
  same op at phase 3 (supervised-auto, clean + high confidence ->
  auto-commit), then a service-operation-dispatch-scheduling request and
  a low-cost supply-order coordination naming a verified vendor (both
  auto-commit clean at phase 3), then a high-cost supply-order (ALWAYS
  escalates regardless of phase), then a hazmat/confined-space/chemical-
  exposure safety-concern flag (ALWAYS escalates, at any phase --
  approve, then commit), then HARD-hold scenarios: an unregistered site,
  a site registered but not yet verified, a supply-order naming an
  unverified vendor, a proposal whose own `:effect` is not `:propose`,
  and a proposal that has drifted into the permanently-excluded hazmat-
  handling-safety-clearance/confined-space-entry-authorization
  finalization scope."
  (:require [langgraph.graph :as g]
            [industrialcleaningops.advisor :as advisor]
            [industrialcleaningops.store :as store]
            [industrialcleaningops.operation :as op]))

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "cleaning-ops-safety-coordinator-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        coordinator-phase-1 {:actor-id "coord-1" :actor-role :cleaning-ops-coordinator :phase 1}
        coordinator-phase-3 {:actor-id "coord-1" :actor-role :cleaning-ops-coordinator :phase 3}
        actor (op/build db)]

    (println "== log-service-record site-1 (phase 1, escalates -- human approves) ==")
    (let [r (exec-op actor "t1" {:op :log-service-record :site-id "site-1"
                                  :patch {:areas-cleaned 12 :chemical-usage-liters 8.5 :labor-hours 6}} coordinator-phase-1)]
      (println r)
      (println "-- human cleaning-ops coordinator approves --")
      (println (approve! actor "t1")))

    (println "\n== log-service-record site-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t2" {:op :log-service-record :site-id "site-1"
                                  :patch {:areas-cleaned 9 :chemical-usage-liters 5.0 :labor-hours 4}} coordinator-phase-3))

    (println "\n== schedule-service-operation site-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t3" {:op :schedule-service-operation :site-id "site-1"
                                  :patch {:crew "night-shift-degreasing" :date "2026-07-20" :window "22:00-06:00"}} coordinator-phase-3))

    (println "\n== coordinate-supply-order site-1, low cost, verified vendor (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t4" {:op :coordinate-supply-order :site-id "site-1"
                                  :patch {:item "industrial degreaser restock" :quantity 40 :estimated-cost 380.0
                                          :vendor-id "vendor-1"}} coordinator-phase-3))

    (println "\n== coordinate-supply-order site-1, HIGH cost (ALWAYS escalates, even at phase 3) ==")
    (let [r (exec-op actor "t5" {:op :coordinate-supply-order :site-id "site-1"
                                 :patch {:item "confined-space ventilation rig lease" :quantity 1 :estimated-cost 4100.0
                                         :vendor-id "vendor-1"}} coordinator-phase-3)]
      (println r)
      (println "-- human cleaning-ops coordinator reviews & approves --")
      (println (approve! actor "t5")))

    (println "\n== flag-safety-concern site-1 (ALWAYS escalates, even at phase 3) ==")
    (let [r (exec-op actor "t6" {:op :flag-safety-concern :site-id "site-1"
                                 :patch {:concern "confined-space entry planned for tank interior, low-oxygen reading near degreasing chemical storage" :confidence 0.92}} coordinator-phase-3)]
      (println r)
      (println "-- human cleaning-ops coordinator reviews & approves --")
      (println (approve! actor "t6")))

    (println "\n== log-service-record site-99 (unregistered site -> HARD hold) ==")
    (println (exec-op actor "t7" {:op :log-service-record :site-id "site-99"
                                  :patch {:areas-cleaned 0}} coordinator-phase-3))

    (println "\n== log-service-record site-3 (registered but unverified -> HARD hold) ==")
    (println (exec-op actor "t8" {:op :log-service-record :site-id "site-3"
                                  :patch {:areas-cleaned 3}} coordinator-phase-3))

    (println "\n== coordinate-supply-order site-1, vendor-2 unverified (-> HARD hold) ==")
    (println (exec-op actor "t9" {:op :coordinate-supply-order :site-id "site-1"
                                  :patch {:item "import bulk degreaser" :quantity 30 :estimated-cost 250.0
                                          :vendor-id "vendor-2"}} coordinator-phase-3))

    (println "\n== schedule-service-operation site-1, advisor attempts direct actuation (:effect :commit) -> HARD hold ==")
    (let [actor-direct (op/build db {:advisor (reify advisor/Advisor
                                                (-advise [_ _ req]
                                                  (assoc (advisor/infer nil req) :effect :commit)))})]
      (println (exec-op actor-direct "t10" {:op :schedule-service-operation :site-id "site-1"
                                           :patch {:crew "weekday-floor-crew" :date "2026-07-22"}} coordinator-phase-3)))

    (println "\n== log-service-record site-1, advisor drifts into hazmat/confined-space authorization scope -> HARD hold, permanent ==")
    (println (exec-op actor "t11" {:op :log-service-record :site-id "site-1"
                                   :out-of-scope? true
                                   :patch {}} coordinator-phase-3))

    (println "\n== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "\n== committed coordination log ==")
    (doseq [r (store/coordination-log db)] (println r))))
