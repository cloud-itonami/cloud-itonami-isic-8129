# Governance

`cloud-itonami-isic-8129` is an OSS open-business blueprint for
industrial/building-cleaning-services operations coordination (ISIC
Rev.5 8129 -- other building and industrial cleaning activities).

## Maintainers
Maintainers may merge changes that preserve these invariants:
- a proposal for an unverified/unregistered client site, or a supply
  order naming an unverified/unregistered vendor, can never commit.
- the IndustrialCleaningOpsGovernor remains independent of the advisor.
- hard policy violations (non-`:propose` effect, hazmat-handling-safety-
  clearance or confined-space-entry-authorization finalization content,
  an op outside the closed allowlist) cannot be overridden by human
  approval.
- every service-record log, crew/equipment dispatch schedule, supply-
  order coordination and hazmat/confined-space/chemical-exposure safety-
  concern flag is auditable.
- client, employee and supplier data stays outside Git.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or
license should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is
a separate trust mark and should require security, audit and data-flow
review.

Certified operators can lose certification for:
- bypassing service-record, crew-dispatch, supply-order or hazmat-
  handling-safety/confined-space-entry policy checks
- mishandling client, employee or supplier data
- misrepresenting certification status
- failing to respond to security or hazmat-handling-safety/confined-
  space-entry incidents
