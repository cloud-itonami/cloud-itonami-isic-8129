# Operator Guide

## First Deployment
1. Register operator, client sites and vendors; independently confirm
   each site's contractor/site-access record and each vendor's
   registration before seeding `industrialcleaningops.store`.
2. Import existing service, crew-dispatch and supply-order history.
3. Run read-only service-record-logging and crew-dispatch dry-runs
   (Phase 0-1).
4. Configure the rollout phase and the `coordinate-supply-order`
   cost-escalation threshold for human sign-off paths.
5. Publish a dry-run hazmat/confined-space/chemical-exposure safety-
   concern flag and audit export.

## Minimum Production Controls
- site-registration/verification check before ANY proposal for that
  site
- vendor-registration/verification check before ANY `:coordinate-
  supply-order` proposal
- governor gate on every proposal before commit
- human sign-off for `:flag-safety-concern` (always) and high-cost
  `:coordinate-supply-order` proposals
- audit export for every commit, hold and approval
- backup manual back-office process

## Certification
Certified operators must prove site/vendor-verification discipline,
governor-bypass resistance, evidence-backed hazmat/confined-space/
chemical-exposure safety-concern reporting and human review for every
escalation-gated action. Certification never covers directly finalizing
a hazmat-handling-safety clearance or a confined-space-entry
authorization -- that decision always stays with a qualified human/
regulatory authority outside this actor.
