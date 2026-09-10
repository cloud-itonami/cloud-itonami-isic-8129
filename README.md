# cloud-itonami-isic-8129

Open Business Blueprint for **ISIC Rev.5 8129**: other building and
industrial cleaning activities -- industrial-scale cleaning that often
involves hazardous cleaning chemicals, confined-space entry, and
specialized equipment (pressure washing, chemical degreasing) beyond
routine janitorial work, distinct from sibling ISIC 8121's general
building cleaning.

This repository publishes an industrial/building-cleaning-services
operations-COORDINATION actor -- job/site cleaning-completion data
logging, cleaning-crew/equipment dispatch scheduling, cleaning-chemical/
equipment supply-order coordination with registered vendors, and hazmat/
confined-space/chemical-exposure safety-concern flagging -- as an OSS
business that any qualified operator can fork, deploy, run, improve and
sell, so an independent industrial-cleaning contractor never surrenders
its operations data to a closed back-office SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, in-mem/Datomic checkpoints) -- the same actor pattern as
every prior actor in this fleet -- here it is
**IndustrialCleaningOpsAdvisor ⊣ IndustrialCleaningOpsGovernor**. This
blueprint's own `:itonami.blueprint/governor` keyword,
`:industrial-cleaning-ops-governor`, is a distinct, independent build
(confirmed unique across the `cloud-itonami` org, including against
sibling ISIC 8110's own governor keyword).

> **Why an actor layer at all?** An LLM is great at drafting a service-
> completion summary, a crew-dispatch proposal, or a supply-order
> request -- but it has no license to actually finalize a hazmat-
> handling-safety clearance for a site involving degreasing chemicals or
> solvents, no authority to finalize a confined-space-entry authorization
> for a tank, vault or trench, no way to independently confirm a client
> site or a supply-order vendor is actually a registered/verified
> counterparty, and no notion of when a "flag this concern" op quietly
> turns into a claim to have already authorized entry into a confined
> space. Letting it act directly invites an unregistered site's data
> entering the ledger, an unverified vendor receiving a chemical/
> equipment order, or -- worst of all -- a fabricated claim to have
> already cleared a chemical spill or authorized confined-space entry,
> exposing crews and clients to real injury and liability. This project
> seals the IndustrialCleaningOpsAdvisor into a single node and wraps it
> with an independent **IndustrialCleaningOpsGovernor**, a human
> **approval workflow**, and an immutable **audit ledger**.

## Scope: coordination only, never a hazmat-handling-safety or confined-space-entry authority

This actor is **operations coordination only**. It never performs or
authorizes:

- setting or overriding a service price
- directly finalizing a hazmat-handling-safety clearance (certifying a
  chemical-storage/degreasing area as code-compliant, clearing a
  chemical spill as contained, signing off on a hazmat handling permit)
- directly finalizing a confined-space-entry authorization (authorizing,
  granting or issuing confined-space entry, declaring confined-space
  ventilation compliant)
- any other hazmat-handling-safety or confined-space-entry authority
  enforcement (authorizing hazardous waste disposal as compliant)

The governor's `scope-exclusion-violations` check re-scans every
proposal for this failure mode independently of the advisor's own
framing, and treats it as a HARD, permanent block regardless of
confidence or how clean everything else is. Flagging a hazmat/confined-
space/chemical-exposure safety concern for a human to triage is exactly
this actor's job -- `:flag-safety-concern` is never excluded by this
check, only FINALIZING/certifying/authorizing that concern is. **The
closed proposal-op allowlist structurally never includes any op that
directly finalizes a hazmat-handling-safety clearance or a confined-
space-entry authorization -- there is no such op to gate, only one to
permanently exclude.**

### Actuation

**Every proposal this actor generates is `:effect :propose`, never a
direct actuation.** Two independent layers enforce this
(`industrialcleaningops.governor`'s `effect-not-propose-violations` HARD
check and `industrialcleaningops.phase`'s phase table, which never puts
`:flag-safety-concern` in any phase's `:auto` set). A human cleaning-
operations coordinator/hazmat-safety coordinator is always the one who
actually acts on a flagged concern or confirms a high-cost supply order.

## The core contract

```
site-access/vendor registration + operations-coordination request
        |
        v
   ┌───────────────────────┐   proposal      ┌────────────────────────────┐
   │ IndustrialCleaningOps- │ ─────────────▶ │ IndustrialCleaningOps-      │  (independent system)
   │ Advisor (sealed)      │  + citations    │ Governor                    │
   └───────────────────────┘                 │ site-unverified ·           │
          │                 commit ◀┼ vendor-unverified ·                │
          │                         │ effect-not-propose ·               │
    record + ledger        escalate ┼ scope-excluded (hazmat-handling-    │
          │              (ALWAYS for│ safety-clearance / confined-space-  │
          │       :flag-safety-     │ entry-authorization finalization) ·│
          │       concern/high-cost │ op-not-allowed                      │
          │       supply-order)     └────────────────────────────┘
          ▼
      human approval
```

**The IndustrialCleaningOpsAdvisor never commits a proposal the
IndustrialCleaningOpsGovernor would reject, and a hazmat/confined-space/
chemical-exposure safety-concern flag or a high-cost supply order never
commits without a human sign-off.** Hard violations (an unregistered/
unverified site; an unregistered/unverified supply-order vendor; a
non-`:propose` effect; content touching hazmat-handling-safety-clearance
or confined-space-entry-authorization finalization; an op outside the
closed allowlist) force **hold** and *cannot* be approved past.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
may perform physical domain work** (here: pressure washing, chemical
degreasing-machine operation, floor scrubbing, confined-space-adjacent
equipment staging) under human/robot floor operations gated by site
policy. This actor itself does not dispatch robot/hardware actions -- it
is strictly the operations-coordination layer (service-record logging,
crew/equipment dispatch scheduling, supply-order coordination, hazmat/
confined-space/chemical-exposure safety-concern flagging) any physical-
dispatch layer could eventually feed proposals into, always gated the
same way by the independent IndustrialCleaningOpsGovernor.

## Features

- **Closed proposal-op allowlist**: `log-service-record`,
  `schedule-service-operation`, `coordinate-supply-order`,
  `flag-safety-concern` (all `:effect :propose`). No op in this
  allowlist finalizes a hazmat-handling-safety clearance or a confined-
  space-entry authorization.
- **Four HARD governor checks** (permanent, un-overridable):
  1. **Site unverified** -- the target client site's contractor/site-
     access record must exist AND be independently registered/verified
     in the store.
  2. **Vendor unverified** -- for `:coordinate-supply-order` only, the
     named vendor must exist AND be independently registered/verified --
     a supply-chain counterparty-verification gate.
  3. **Effect is :propose** -- any other `:effect` value is rejected.
  4. **Scope exclusion** -- directly finalizing a hazmat-handling-safety
     clearance (certifying chemical-storage compliance, clearing a
     chemical spill as contained, signing off on a handling permit) or a
     confined-space-entry authorization (authorizing, granting or
     issuing confined-space entry), and an op outside the closed
     allowlist, are all permanently blocked.
- **Two ESCALATE (SOFT) gates**, either forces human sign-off:
  - `:flag-safety-concern` -- ALWAYS escalates, regardless of confidence
    or phase. A "flag a concern" op is never auto-commit eligible and
    never finalizes a hazmat-handling-safety-clearance or confined-
    space-entry-authorization decision itself -- it only surfaces the
    concern for a human.
  - `:coordinate-supply-order` above a cost threshold -- a large-value
    procurement proposal always needs a human sign-off.
  - (LLM confidence below the floor also escalates, as with every
    sibling actor.)
- **Staged rollout** (Phase 0→3):
  - Phase 0: read-only
  - Phase 1: service-record logging only (approval-gated)
  - Phase 2: + crew/equipment dispatch scheduling, supply-order proposals
    (approval-gated)
  - Phase 3: auto-commits clean, high-confidence, low-cost proposals
    (safety concerns and high-cost supply orders always escalate)
- **Append-only audit ledger** -- every decision is an immutable log
  entry.
- **langgraph-clj StateGraph** -- one request = one supervised run;
  human-in-the-loop via `interrupt-before`.

### Development

```bash
# Install dependencies (if inside the superproject, use :dev alias for local overrides)
clojure -M:dev -P

# Run tests
clojure -M:test

# Run linter
clojure -M:lint

# Run demo
clojure -M:run
```

### Test suite

- `test/industrialcleaningops/governor_test.kotoba` -- unit tests of
  governor hard checks, scope exclusion, and the self-trip regression
  test
- `test/industrialcleaningops/advisor_test.kotoba` -- advisor proposal
  shape and consistency
- `test/industrialcleaningops/phase_test.kotoba` -- rollout phase logic
- `test/industrialcleaningops/governor_contract_test.kotoba` -- full graph
  integration, audit trail
- `test/industrialcleaningops/store_contract_test.kotoba` -- Store protocol
  and MemStore implementation

### Modules

- `industrialcleaningops.store` -- SSoT (MemStore, String-keyed site/
  vendor directories, append-only ledger)
- `industrialcleaningops.advisor` -- contained intelligence node (mock +
  real-LLM seam)
- `industrialcleaningops.governor` -- independent compliance layer
- `industrialcleaningops.phase` -- staged rollout (0→3)
- `industrialcleaningops.operation` -- langgraph-clj StateGraph
- `industrialcleaningops.sim` -- demo driver

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`8129`).

## Business-process coverage (honest)

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Job/site cleaning-completion data logging (`:log-service-record`) | Real field-service/work-order-system integration |
| Cleaning-crew/equipment dispatch scheduling coordination (`:schedule-service-operation`) | Direct crew time-clock/payroll integration |
| Cleaning-chemical/equipment supply-order coordination with a registered, verified vendor, HARD-gated on vendor verification and a double-actuation-free single-proposal shape (`:coordinate-supply-order`) | Real supplier-ordering-system integration |
| Hazmat/confined-space/chemical-exposure safety-concern flagging, ALWAYS human-gated (`:flag-safety-concern`) | Directly finalizing any hazmat-handling-safety clearance or confined-space-entry authorization -- permanently out of scope, not a gap |
| Immutable audit ledger for every log/schedule/order/flag decision | Daily invoicing/reconciliation -- a follow-up slice, not in this R0 |

Extending coverage is additive: add the next op (e.g. an incident-
report or a client-complaint-escalation check) as its own governed op
with its own HARD checks and tests, following the SAME "an independent
governor re-verifies against the actor's own records before any real-
world act" pattern this repo's flagship checks already establish.

## Maturity

`:implemented` -- `IndustrialCleaningOpsAdvisor` +
`IndustrialCleaningOpsGovernor` run as real, tested code (see
`Development` above), following the SAME governed-actor architecture as
every prior actor across this fleet, with its own distinct,
independently-named governor and its own hazmat-handling-safety-
clearance/confined-space-entry-authorization scope-exclusion check.

## License

Code and implementation templates are AGPL-3.0-or-later.
