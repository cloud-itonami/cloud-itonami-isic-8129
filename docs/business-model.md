# Business Model: Industrial and Building Cleaning Operations Coordination

## Classification
- Repository: `cloud-itonami-isic-8129`
- ISIC Rev.5: `8129` -- other building and industrial cleaning
  activities (industrial-scale cleaning that often involves hazardous
  cleaning chemicals, confined-space entry, and specialized equipment --
  pressure washing, chemical degreasing -- beyond routine janitorial
  work; distinct from sibling ISIC 8121's general building cleaning)
- Social impact: worker safety, public health, transparency

## Customer
- independent industrial/building-cleaning-services contractors needing
  an auditable operations-coordination platform
- multi-site contractors needing consistent crew-dispatch/supply-order/
  hazmat-and-confined-space-safety governance across client sites
- programs that cannot accept closed, unauditable back-office platforms

## Offer
- job/site cleaning-completion data logging
- cleaning-crew/equipment dispatch scheduling coordination
- cleaning-chemical/equipment supply-order coordination with registered,
  verified vendors
- hazmat/confined-space/chemical-exposure safety-concern flagging
  (chemical exposure, confined-space entry conditions, ventilation
  observations) for human triage
- role-based access and immutable audit ledger

## Revenue
- self-host setup fee
- managed hosting subscription per contractor/site
- support retainer with SLA

## Trust Controls
- `:industrial-cleaning-ops-governor` never lets a proposal for an
  unregistered/unverified client site, or a supply order naming an
  unregistered/unverified vendor, commit or even escalate
- every proposal's `:effect` must be `:propose` -- a claim to directly
  actuate is a HARD, un-overridable block
- directly finalizing a hazmat-handling-safety clearance (certifying
  chemical-storage compliance, clearing a chemical spill as contained,
  signing off on a handling permit) or a confined-space-entry
  authorization (authorizing, granting or issuing confined-space entry)
  is permanently out of scope, not a rollout milestone -- the actor may
  only flag a concern for a human
- a `:flag-safety-concern` proposal, and a high-cost `:coordinate-
  supply-order`, always require human sign-off
- sensitive client, employee and supplier data stays outside Git
