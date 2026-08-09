(ns industrialcleaningops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave1 Lane A-no-demo): this repo previously had NO demo page and no
  generator at all. This namespace drives the REAL actor stack
  (`industrialcleaningops.operation` -> `industrialcleaningops.governor`
  -> `industrialcleaningops.store`) through a scenario adapted from this
  repo's own `industrialcleaningops.sim` demo driver
  (`clojure -M:dev:run`, confirmed to run correctly against the real
  seeded site/vendor directory before this file was written -- site ids
  site-1/site-2/site-3 and vendor ids vendor-1/vendor-2 DO match
  `industrialcleaningops.store/demo-data`, and the `:commit` node
  genuinely calls `store/commit-record!` so the coordination log grows
  by one real entry per committed op), trimmed to a representative
  subset (auto-commit lifecycle, ALWAYS-escalate + human approval, and
  distinct HARD-hold reasons that never reach a human) and rendered
  deterministically -- no invented numbers, no timestamps in the page
  content, byte-identical across reruns against the same seed (verify
  by diffing two consecutive runs).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [industrialcleaningops.store :as store]
            [industrialcleaningops.advisor :as advisor]
            [industrialcleaningops.operation :as op]
            [langgraph.graph :as g]))

;; ----------------------------- harness --------------------------------

(def ^:private operator
  {:actor-id "op-1" :actor-role :cleaning-ops-coordinator :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach: site-1 clears three write ops that auto-commit
  clean at phase 3 (log-service-record, schedule-service-operation, a
  low-cost coordinate-supply-order naming verified vendor-1); site-1's
  high-cost coordinate-supply-order ALWAYS escalates (estimated-cost
  4100.0 exceeds `governor/supply-cost-threshold` 1000.0) and is approved
  by a human; site-2's flag-safety-concern ALWAYS escalates (per
  `governor/always-escalate-ops`) even though clean, and is approved;
  site-3 (registered but NOT `:verified?` in the seed) HARD-holds on
  `:site-unverified`; a supply-order naming unverified vendor-2
  HARD-holds on `:vendor-unverified`; an advisor that claims direct
  actuation (`:effect :commit`) HARD-holds on `:effect-not-propose`; a
  proposal with `:out-of-scope? true` HARD-holds on `:scope-excluded`.
  Every HARD hold never reaches a human. Returns the resulting store --
  every field read by `render` below is real governor/store output, not
  a hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)]
    (exec! actor "s1-log" {:op :log-service-record :site-id "site-1"
                            :patch {:areas-cleaned 12 :chemical-usage-liters 8.5
                                    :labor-hours 6}})

    (exec! actor "s1-schedule" {:op :schedule-service-operation :site-id "site-1"
                                 :patch {:crew "night-shift-degreasing"
                                         :date "2026-07-20"
                                         :window "22:00-06:00"}})

    (exec! actor "s1-supply-low" {:op :coordinate-supply-order :site-id "site-1"
                                   :patch {:item "industrial degreaser restock"
                                           :quantity 40 :estimated-cost 380.0
                                           :vendor-id "vendor-1"}})

    (exec! actor "s1-supply-high" {:op :coordinate-supply-order :site-id "site-1"
                                    :patch {:item "confined-space ventilation rig lease"
                                            :quantity 1 :estimated-cost 4100.0
                                            :vendor-id "vendor-1"}})
    (approve! actor "s1-supply-high")

    (exec! actor "s2-safety" {:op :flag-safety-concern :site-id "site-2"
                               :patch {:concern "confined-space entry planned for tank interior, low-oxygen reading near degreasing chemical storage"
                                       :confidence 0.92}})
    (approve! actor "s2-safety")

    ;; HARD: site-3 registered but unverified
    (exec! actor "s3-log" {:op :log-service-record :site-id "site-3"
                            :patch {:areas-cleaned 3}})

    ;; HARD: vendor-2 registered but unverified
    (exec! actor "s1-vendor" {:op :coordinate-supply-order :site-id "site-1"
                               :patch {:item "import bulk degreaser" :quantity 30
                                       :estimated-cost 250.0 :vendor-id "vendor-2"}})

    ;; HARD: effect not :propose
    (let [actor-direct (op/build db {:advisor (reify advisor/Advisor
                                                 (-advise [_ _ req]
                                                   (assoc (advisor/infer nil req) :effect :commit)))})]
      (exec! actor-direct "s1-direct" {:op :schedule-service-operation :site-id "site-1"
                                        :patch {:crew "weekday-floor-crew"
                                                :date "2026-07-22"}}))

    ;; HARD: scope-excluded (hazmat/confined-space finalization drift)
    (exec! actor "s1-scope" {:op :log-service-record :site-id "site-1"
                              :out-of-scope? true :patch {}})
    db))

;; ----------------------------- rendering ------------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger site-id]
  (last (filter #(= (:site-id %) site-id) ledger)))

(defn- status-cell [ledger site-id]
  (let [f (last-fact-for ledger site-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (-> f :violations first :rule)]
        (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      (= :approval-rejected (:t f)) "<span class=\"critical\">approval rejected</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- site-row [ledger {:keys [site-id name registered? verified?]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc site-id) (esc name)
          (cond
            (and registered? verified?) "<span class=\"ok\">registered &amp; verified</span>"
            registered? "<span class=\"warn\">registered, unverified</span>"
            :else "<span class=\"critical\">unregistered</span>")
          (status-cell ledger site-id)))

(defn- vendor-row [{:keys [vendor-id name registered? verified?]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc vendor-id) (esc name)
          (cond
            (and registered? verified?) "<span class=\"ok\">registered &amp; verified</span>"
            registered? "<span class=\"warn\">registered, unverified</span>"
            :else "<span class=\"critical\">unregistered</span>")))

(defn- ledger-row [{:keys [t op site-id disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc site-id)
          (esc (or (some->> basis (map #(if (keyword? %) (name %) (str %))) (str/join ", "))
                    (some-> disposition name) ""))))

(defn- coord-row [{:keys [op site-id value]}]
  (format "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name (or op :n-a))) (esc site-id)
          (esc (pr-str (or value {})))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own closed op contract (README
  ;; Ops, industrialcleaningops.governor / .phase) -- documentation of
  ;; fixed behavior, not runtime telemetry.
  ["        <tr><td><code>:log-service-record</code></td><td><span class=\"ok\">phase-3 auto when clean</span> &middot; HARD hold if site unverified</td></tr>"
   "        <tr><td><code>:schedule-service-operation</code></td><td><span class=\"ok\">phase-3 auto when clean</span> &middot; HARD hold if site unverified or effect not :propose</td></tr>"
   "        <tr><td><code>:coordinate-supply-order</code></td><td><span class=\"ok\">phase-3 auto when clean &amp; vendor verified</span> &middot; ALWAYS human approval above USD 1000 estimated cost &middot; HARD hold if vendor unverified</td></tr>"
   "        <tr><td><code>:flag-safety-concern</code></td><td><span class=\"warn\">ALWAYS human approval &middot; never auto, any phase</span> &middot; observation only; finalizing hazmat/confined-space clearance is permanently out of scope</td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        sites (store/all-site-records db)
        vendors (store/all-vendor-records db)
        coords (store/coordination-log db)
        site-rows (str/join "\n" (map (partial site-row ledger) sites))
        vendor-rows (str/join "\n" (map vendor-row vendors))
        coord-rows (str/join "\n" (map coord-row coords))
        ledger-rows (str/join "\n" (map ledger-row ledger))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-8129 &middot; industrial cleaning operations</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Other building and industrial cleaning activities (ISIC 8129) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · never finalizes hazmat clearance or confined-space entry</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Client sites (contractor site-access records)</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>industrialcleaningops.store</code> via <code>industrialcleaningops.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Site</th><th>Name</th><th>Access status</th><th>Last coordination status</th></tr></thead>\n"
     "      <tbody>\n"
     site-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Chemical / equipment vendors</h2>\n"
     "    <p class=\"muted\">Supply-order coordination requires an independently registered and verified vendor — never trust the proposal's own vendor claim.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Vendor</th><th>Name</th><th>Status</th></tr></thead>\n"
     "      <tbody>\n"
     vendor-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Committed coordination log</h2>\n"
     "    <p class=\"muted\">Proposals that actually committed to the SSoT (auto-commit or human-approved). HARD holds never appear here.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Site</th><th>Value</th></tr></thead>\n"
     "      <tbody>\n"
     coord-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (IndustrialCleaningOps Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden. Site access and vendor registration are independently re-derived from the store; <code>:effect</code> must be <code>:propose</code>; finalizing a hazmat-handling-safety clearance or confined-space-entry authorization is permanently out of scope.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Site</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/coordination-log db)) "committed coordination records )")))
