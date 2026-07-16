# Contributing

`cloud-itonami-isic-8129` accepts contributions to the OSS blueprint,
capability bindings, policy tests, documentation and operator model.

## Development

```bash
clojure -M:test
clojure -M:lint
```

## Rules
- Do not commit real client, employee, supplier or hazmat-handling-
  safety/confined-space-entry incident data.
- Keep service-record logging, crew/equipment dispatch scheduling,
  supply-order coordination and hazmat/confined-space/chemical-exposure
  safety-concern flagging behind the IndustrialCleaningOpsGovernor.
- Treat industrial-cleaning-operations workflows as high-risk: add tests
  for site/vendor verification, effect discipline, scope exclusion,
  escalation and audit logging.
- Never phrase a governor scope-exclusion term as a bare noun (e.g.
  "hazmat", "confined space", "chemical") -- phrase it as the
  finalization/execution ACTION (e.g. "authorized the confined-space
  entry", "certified the chemical storage area as compliant"), and
  add/extend the
  `default-mock-advisor-proposals-never-self-trip-scope-exclusion`
  regression test for any new term. A bare-noun term will self-trip this
  actor's own legitimate `:flag-safety-concern` happy path -- see
  `industrialcleaningops.governor/scope-excluded-terms`'s docstring.
- Never add an op that directly finalizes a hazmat-handling-safety
  clearance or a confined-space-entry authorization to the closed
  proposal-op allowlist -- those actions are structurally excluded from
  this actor's vocabulary, not merely gated.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests
PRs should describe: what behavior changed, which policy invariant is
affected, how it was tested, whether operator or certification docs need
updates.
