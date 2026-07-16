(ns industrialcleaningops.advisor
  "IndustrialCleaningOpsAdvisor -- the *contained intelligence node* for
  the ISIC-8129 'Other building and industrial cleaning activities'
  operations-coordination actor.

  It drafts exactly four kinds of back-office proposal from a closed
  allowlist: job/site cleaning-completion data logging, cleaning-crew/
  equipment dispatch scheduling, cleaning-chemical/equipment supply-order
  coordination, and hazmat/confined-space/chemical-exposure safety-
  concern flagging. CRITICAL: it is a smart-but-untrusted advisor. It
  returns a *proposal* (with a rationale + the fields it cited), never a
  committed record and NEVER a direct actuation -- every proposal's
  `:effect` is always `:propose`. Every output is censored downstream by
  `industrialcleaningops.governor` before anything touches the SSoT.

  This advisor NEVER drafts a service-price decision, a direct hazmat-
  handling-safety-clearance finalization (certifying chemical-storage/
  degreasing-area compliance, clearing a chemical spill as contained,
  signing off on a hazmat handling permit), a direct confined-space-
  entry-authorization finalization (authorizing/granting/issuing
  confined-space entry), or any other hazmat-handling-safety or confined-
  space-entry authority action -- those are permanently out of scope for
  this actor, not merely un-implemented. `industrialcleaningops.governor`'s
  `scope-exclusion-violations` independently re-scans every proposal for
  exactly this failure mode (a compromised or confused advisor drifting
  into scope it must never touch) and HARD-holds it, regardless of
  confidence or op.

  Like every sibling actor's advisor, this is a deterministic mock so the
  actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:op         kw             ; echoes the request op
     :site-id    str
     :summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the scope-exclusion gate
     :cites      [str ..]       ; facts/sources the advisor used -- SCANNED too
     :effect     :propose       ; ALWAYS :propose -- never a direct actuation
     :value      map            ; the draft payload a human/system would review
     :confidence 0..1}")

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

;; ----------------------------- proposal generators -----------------------------

(defn- propose-service-record
  "Draft a job/site cleaning-completion data-log entry. Pure logging of
  observed work (areas cleaned, chemical/consumable usage, labor hours) --
  never a service-price decision."
  [_db {:keys [site-id patch]}]
  {:op         :log-service-record
   :site-id    site-id
   :summary    (str site-id " の清掃完了/使用薬剤・資機材消費量記録を記録: " (pr-str (keys patch)))
   :rationale  "清掃完了状況・使用薬剤/資機材消費量・作業時間の観察記録のみ。安全許可の判断は含まない。"
   :cites      [site-id]
   :effect     :propose
   :value      (merge {:site-id site-id} patch)
   :confidence 0.93})

(defn- propose-service-operation
  "Draft a cleaning-crew/equipment dispatch scheduling proposal (a
  roster/equipment-dispatch entry, never a direct enforcement or
  clearance action)."
  [_db {:keys [site-id patch]}]
  {:op         :schedule-service-operation
   :site-id    site-id
   :summary    (str site-id " の清掃クルー/資機材の派遣予定を提案: " (pr-str (keys patch)))
   :rationale  "清掃クルー配置・圧力洗浄機/薬剤散布機材の派遣調整提案のみ。最終配置は人間が確定する。"
   :cites      [site-id]
   :effect     :propose
   :value      (merge {:site-id site-id} patch)
   :confidence 0.88})

(defn- propose-supply-order
  "Draft a cleaning-chemical/equipment procurement coordination request
  naming a registered vendor -- never a finalized purchase order; a
  human always confirms procurement."
  [_db {:keys [site-id patch]}]
  {:op         :coordinate-supply-order
   :site-id    site-id
   :summary    (str site-id " 向け清掃薬剤/資機材の発注調整を提案: " (pr-str (keys patch)))
   :rationale  "清掃薬剤・脱脂剤・保護具等の仕入先発注調整提案のみ。確定発注は人間が行う。"
   :cites      [site-id]
   :effect     :propose
   :value      (merge {:site-id site-id} patch)
   :confidence 0.90})

(defn- propose-safety-concern
  "Surface an observed hazmat/confined-space/chemical-exposure safety
  concern (chemical exposure, confined-space entry conditions,
  ventilation observation) for HUMAN triage. This op ALWAYS escalates in
  `industrialcleaningops.governor` -- never auto-committed at any phase
  -- regardless of how confident the advisor is that the concern is
  real. Deliberately reports the OBSERVATION only, never a finalization/
  clearance/authorization action, so the default rationale never trips
  the governor's `scope-excluded-terms` (see that var's docstring)."
  [_db {:keys [site-id patch]}]
  {:op         :flag-safety-concern
   :site-id    site-id
   :summary    (str site-id " の危険物取扱い/密閉空間安全懸念フラグ: " (pr-str (:concern patch "unknown")))
   :rationale  "薬剤曝露・密閉空間・換気・保護具に関する懸念の観察事実の報告。常に人間の確認・対応が必要。"
   :cites      [site-id]
   :effect     :propose
   :value      (merge {:site-id site-id} patch)
   :confidence (or (:confidence patch) 0.85)})

;; ----------------------------- default mock advisor -----------------------------

(defn infer
  "Mock advisor: routes to the correct proposal generator."
  [_db {:keys [op out-of-scope?] :as request}]
  (let [proposal (case op
                   :log-service-record (propose-service-record _db request)
                   :schedule-service-operation (propose-service-operation _db request)
                   :coordinate-supply-order (propose-supply-order _db request)
                   :flag-safety-concern (propose-safety-concern _db request)
                   {})]
    ;; Test hook: allow injecting scope-excluded content to exercise the
    ;; governor's scope-exclusion block end-to-end. Must be cleared before
    ;; production use.
    (if out-of-scope?
      (update proposal :rationale str " -- actually authorized the confined-space entry and certified the site as hazmat-compliant")
      proposal)))

(defn trace
  "Audit fact for a proposal generated by this advisor."
  [_request proposal]
  {:t       :advisor-proposal
   :op      (:op proposal)
   :site-id (:site-id proposal)
   :summary (:summary proposal)
   :confidence (:confidence proposal)})

(defn mock-advisor
  "The deterministic default advisor for offline demo/test."
  []
  (reify Advisor
    (-advise [_ _store request]
      (infer nil request))))
