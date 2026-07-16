(ns industrialcleaningops.governor
  "IndustrialCleaningOpsGovernor -- the independent compliance layer that
  earns the IndustrialCleaningOpsAdvisor the right to commit. The advisor
  has no notion of whether a client site is actually registered and
  site-access-verified, whether a named cleaning-chemical/equipment
  supply-order vendor is itself a registered/verified counterparty,
  whether its own proposed `:effect` secretly claims a direct actuation
  instead of a mere proposal, or whether it has silently drifted into a
  permanently out-of-scope decision area, so this MUST be a separate
  system able to *reject* a proposal and fall back to HOLD.

  This actor's scope is deliberately narrow -- COORDINATION ONLY (job/
  site cleaning-completion data logging, cleaning-crew/equipment dispatch
  scheduling, cleaning-chemical/equipment supply-order coordination,
  hazmat/confined-space/chemical-exposure safety-concern flagging). It
  NEVER performs or authorizes:
    - setting or overriding a service price
    - directly finalizing a hazmat-handling-safety clearance (certifying
      a chemical-storage/degreasing area as code-compliant, clearing a
      chemical spill as contained, signing off on a hazmat handling
      permit)
    - directly finalizing a confined-space-entry authorization
      (authorizing confined-space entry, granting a confined-space entry
      clearance, issuing a confined-space entry permit, declaring
      confined-space ventilation compliant)
    - hazmat-handling-safety or confined-space-entry authority
      enforcement of any other kind (authorizing hazardous waste
      disposal as compliant)

  Four HARD checks, ALL permanent, un-overridable by any human approval:

    1. Site unverified            -- the target client site's
                                     contractor/site-access record must
                                     exist AND be independently confirmed
                                     `:registered?`/`:verified?` in the
                                     store before ANY proposal for it may
                                     commit or even escalate. Never trusts
                                     a proposal's own claim about the
                                     site -- re-derived from the site's
                                     own record, the same 'ground truth,
                                     not self-report' discipline every
                                     sibling actor's governor uses.
    2. Vendor unverified          -- for `:coordinate-supply-order` ONLY,
                                     the proposal's own drafted `:value`
                                     must name a `:vendor-id` that
                                     resolves to an independently
                                     `:registered?`/`:verified?` vendor
                                     record. A missing vendor-id, or one
                                     that resolves to an unregistered or
                                     unverified vendor, is a HARD block.
    3. Effect not :propose        -- every proposal's `:effect` MUST be
                                     `:propose`. Any other effect value
                                     is, by construction, a claim to
                                     directly actuate/commit outside
                                     governance -- HARD block, not merely
                                     low-confidence.
    4. Scope exclusion            -- ANY proposal (regardless of op)
                                     whose op, summary, rationale, cites
                                     or draft value touches directly
                                     finalizing a hazmat-handling-safety
                                     clearance OR a confined-space-entry
                                     authorization (certifying chemical-
                                     storage compliance, clearing a
                                     chemical spill as contained, signing
                                     off on a handling permit, authorizing/
                                     granting/issuing confined-space
                                     entry) is a HARD, PERMANENT block --
                                     this actor's charter excludes that
                                     territory structurally, not as a
                                     rollout milestone. Evaluated
                                     UNCONDITIONALLY on every proposal. An
                                     op outside the closed four-op
                                     allowlist is the SAME failure mode
                                     (an advisor proposing something it
                                     was never authorized to propose) and
                                     is folded into this same check.
                                     `:flag-safety-concern` itself is
                                     never excluded by this check --
                                     surfacing a hazmat/confined-space/
                                     chemical-exposure concern for a human
                                     is exactly this actor's job; only
                                     FINALIZING/certifying/authorizing
                                     that concern is excluded (see
                                     `scope-excluded-terms` below --
                                     phrased as the finalization/
                                     execution ACTION, never a bare noun
                                     like 'hazmat', 'confined space' or
                                     'chemical', so the default mock
                                     advisor's own `:flag-safety-concern`
                                     rationale never self-trips this
                                     check).

  Two ESCALATE (SOFT) gates, either forces human sign-off:
    - LLM confidence below the floor.
    - The op is `:flag-safety-concern` -- ALWAYS escalates to a human,
      regardless of confidence, regardless of how clean the proposal
      otherwise is. `industrialcleaningops.phase` independently agrees:
      `:flag-safety-concern` is never a member of any phase's `:auto`
      set either -- two layers, not one. This actor NEVER directly
      finalizes a hazmat-handling-safety clearance or a confined-space-
      entry authorization itself -- flagging a concern always routes to
      a human, never to auto-commit.
    - A `:coordinate-supply-order` whose drafted `:value` names an
      `:estimated-cost` above `supply-cost-threshold` -- a large-value
      chemical/equipment procurement proposal always needs a human
      sign-off, even when the governor and phase would otherwise allow
      auto-commit."
  (:require [clojure.string :as str]
            [industrialcleaningops.store :as store]))

(def confidence-floor 0.6)

(def supply-cost-threshold
  "Example single-site cleaning-chemical/equipment procurement threshold
  (USD-equivalent units, domain-illustrative -- not a universal
  cross-domain constant). A `:coordinate-supply-order` proposal citing an
  `:estimated-cost` above this value ALWAYS escalates to human sign-off,
  regardless of confidence or rollout phase."
  1000.0)

(def allowed-ops
  "The closed proposal-op allowlist -- an op outside this set is a scope
  violation by construction (see `scope-exclusion-violations`). NOTE: no
  op in this allowlist finalizes a hazmat-handling-safety clearance or a
  confined-space-entry authorization -- those actions are structurally
  excluded from the actor's vocabulary, not merely gated."
  #{:log-service-record :schedule-service-operation
    :coordinate-supply-order :flag-safety-concern})

(def always-escalate-ops
  "Ops that ALWAYS require human sign-off, clean or not."
  #{:flag-safety-concern})

(def scope-excluded-terms
  "Case-insensitive substrings that mark a proposal as touching a
  permanently out-of-scope decision area -- directly finalizing a
  hazmat-handling-safety clearance (certifying chemical-storage/
  degreasing-area compliance, clearing a chemical spill as contained,
  signing off on a handling permit) or a confined-space-entry
  authorization (authorizing/granting/issuing confined-space entry,
  declaring confined-space ventilation compliant), or otherwise
  finalizing/certifying/authorizing a hazmat-safety or confined-space
  matter rather than merely flagging it for a human. Scanned across the
  proposal's op/summary/rationale/cites/value, never trusting the
  advisor's own framing of its intent.

  CRITICAL: every term here is phrased as the finalization/execution
  ACTION (e.g. 'certified the chemical storage area as compliant',
  'authorized the confined-space entry'), never a bare noun like
  'hazmat', 'confined space', 'chemical' or 'degreaser' -- a bare noun
  would accidentally match inside this actor's own legitimate
  `:flag-safety-concern` default proposal text (whose whole job is to
  talk about chemical-exposure/confined-space/ventilation/protective-
  equipment concerns, and whose own printed `:op` keyword literally
  contains the substring 'safety') and self-block the happy path. See
  `industrialcleaningops.governor-test/default-mock-advisor-proposals-never-self-trip-scope-exclusion`
  for the regression test."
  ["finalize the hazmat clearance" "finalized the hazmat clearance" "finalizing the hazmat clearance"
   "finalize the hazmat handling clearance" "finalized the hazmat handling clearance" "finalizing the hazmat handling clearance"
   "certify the site as hazmat-compliant" "certified the site as hazmat-compliant" "certifying the site as hazmat-compliant"
   "certify the chemical storage area as compliant" "certified the chemical storage area as compliant" "certifying the chemical storage area as compliant"
   "certify the degreasing area as code-compliant" "certified the degreasing area as code-compliant" "certifying the degreasing area as code-compliant"
   "clear the chemical spill as contained" "cleared the chemical spill as contained" "clearing the chemical spill as contained"
   "clear the chemical spill as safe" "cleared the chemical spill as safe" "clearing the chemical spill as safe"
   "sign off on the hazmat handling permit" "signed off on the hazmat handling permit" "signing off on the hazmat handling permit"
   "sign off on the hazmat clearance" "signed off on the hazmat clearance" "signing off on the hazmat clearance"
   "authorize the hazardous waste disposal as compliant" "authorized the hazardous waste disposal as compliant" "authorizing the hazardous waste disposal as compliant"
   "finalize the confined-space entry authorization" "finalized the confined-space entry authorization" "finalizing the confined-space entry authorization"
   "authorize the confined-space entry" "authorized the confined-space entry" "authorizing the confined-space entry"
   "grant the confined-space entry clearance" "granted the confined-space entry clearance" "granting the confined-space entry clearance"
   "issue the confined-space entry permit" "issued the confined-space entry permit" "issuing the confined-space entry permit"
   "clear the confined space as safe for entry" "cleared the confined space as safe for entry" "clearing the confined space as safe for entry"
   "declare the confined space ventilation compliant" "declared the confined space ventilation compliant" "declaring the confined space ventilation compliant"
   "confirm the confined-space atmosphere as safe" "confirmed the confined-space atmosphere as safe" "confirming the confined-space atmosphere as safe"
   "危険物取扱いの許可を確定" "危険物取扱いの許可を確定した" "危険物取扱いの許可を確定する"
   "密閉空間立入の許可を確定" "密閉空間立入の許可を確定した" "密閉空間立入の許可を確定する"
   "密閉空間立入許可を発行" "密閉空間立入許可を発行した" "密閉空間立入許可を発行する"
   "薬剤保管区画の適合証明を発行" "薬剤保管区画の適合証明を発行した" "薬剤保管区画の適合証明を発行する"
   "漏出物の安全確認を完了" "漏出物の安全確認を完了した" "漏出物の安全確認を完了する"]
  )

;; ----------------------------- checks -----------------------------

(defn- site-unverified-violations
  "The target client site must exist AND be independently
  `:registered?`/`:verified?` (contractor/site-access record) in the
  store -- never trust the proposal's own `:site-id` claim without a
  store lookup."
  [{:keys [site-id]} st]
  (let [s (store/site-record st site-id)]
    (when-not (and s (:registered? s) (:verified? s))
      [{:rule :site-unverified
        :detail (str site-id " は未登録または未検証の現場アクセス記録 -- いかなる提案も進められない")}])))

(defn- vendor-unverified-violations
  "For `:coordinate-supply-order` ONLY, the proposal's own drafted
  `:value` must name a `:vendor-id` that resolves to an independently
  `:registered?`/`:verified?` vendor record. A missing vendor-id, or one
  that resolves to an unregistered/unverified vendor, is a HARD block --
  never trust the proposal's own vendor claim without a store lookup, the
  SAME 'ground truth, not self-report' discipline as
  `site-unverified-violations`, reapplied to the supply-chain
  counterparty."
  [proposal st]
  (when (= :coordinate-supply-order (:op proposal))
    (let [vendor-id (get-in proposal [:value :vendor-id])
          v (and vendor-id (store/vendor-record st vendor-id))]
      (when-not (and v (:registered? v) (:verified? v))
        [{:rule :vendor-unverified
          :detail (str (or vendor-id "(vendor-id missing)")
                        " は未登録または未検証の仕入先 -- 発注調整提案を進められない")}]))))

(defn- effect-not-propose-violations
  "`:effect` must ALWAYS be `:propose` -- any other value is a claim to
  directly actuate/commit outside governance."
  [proposal]
  (when (not= :propose (:effect proposal))
    [{:rule :effect-not-propose
      :detail (str ":effect は :propose のみ許可されるが " (pr-str (:effect proposal)) " が提案された")}]))

(defn- text-blob
  "Flatten every advisor-authored field on a proposal into one lower-cased
  blob the scope-exclusion scan checks."
  [proposal]
  (str/lower-case (pr-str (select-keys proposal [:op :summary :rationale :cites :value]))))

(defn- scope-exclusion-violations
  "HARD, PERMANENT block: a proposal outside the closed op allowlist, or
  one whose content touches directly finalizing a hazmat-handling-safety
  clearance or a confined-space-entry authorization (certifying chemical-
  storage compliance, clearing a chemical spill as contained, signing off
  on a handling permit, authorizing/granting/issuing confined-space
  entry), regardless of confidence or how clean every other check is.
  Evaluated UNCONDITIONALLY on every proposal."
  [proposal]
  (let [op (:op proposal)
        blob (text-blob proposal)]
    (cond
      (not (contains? allowed-ops op))
      [{:rule :op-not-allowed
        :detail (str (pr-str op) " は許可された操作(closed allowlist)に含まれない")}]

      (some #(str/includes? blob %) scope-excluded-terms)
      [{:rule :scope-excluded
        :detail "危険物取扱い安全許可または密閉空間立入許可の確定行為(hazmat-handling-safety-clearance/confined-space-entry-authorization finalization)に触れる提案は永久に禁止"}])))

(defn- high-cost-supply-order?
  "A `:coordinate-supply-order` proposal citing an `:estimated-cost` above
  `supply-cost-threshold` -- always needs human sign-off (SOFT escalate,
  not a hard block: the order itself is in scope, only its size requires
  a human)."
  [proposal]
  (and (= :coordinate-supply-order (:op proposal))
       (some-> proposal :value :estimated-cost (> supply-cost-threshold))))

(defn check
  "Censors an IndustrialCleaningOpsAdvisor proposal against the governor
  rules. Returns {:ok? bool :violations [..] :confidence c :escalate?
  bool :high-stakes? bool :hard? bool}."
  [request _context proposal store]
  (let [site-id (or (:site-id proposal) (:site-id request))
        hard (into []
                   (concat (site-unverified-violations {:site-id site-id} store)
                           (vendor-unverified-violations proposal store)
                           (effect-not-propose-violations proposal)
                           (scope-exclusion-violations proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (or (always-escalate-ops (:op proposal))
                              (high-cost-supply-order? proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :site-id    (:site-id request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
