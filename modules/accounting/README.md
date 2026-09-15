# Accounting

This module owns the POC accounting acquisition boundary, deterministic accounting facts, reconciliation, VAT/JPK output, and accounting-specific review state. Portfolio investment calculations and generic banking/provider transport remain outside it.

## Canonical model

The Jan-Aug 2026 reference tables are an immutable verification oracle. They preserve the trusted historical answer and never feed operational calculations. Canonical facts are the reconstructed operational result.

## Data layers

```text
reference -> source evidence -> staging -> canonical operational -> derived accounting result
```

Reference rows are comparison-only. Source evidence is immutable; staging is reviewable; promotion is the explicit boundary into operational canonical facts.

## Acquisition model

```text
source -> source evidence -> staging -> reconciliation -> explicit promotion -> canonical facts
```

Staged and canonical facts are owned by a portfolio. Canonical matching is always profile-scoped. Document references are not globally unique; facts from another profile must never influence reconciliation.

The persistence and API model carries `profileId`, and the product page is exposed at
`/profiles/{profileId}/accounting`. The Accounting POC is certified only for the primary profile
(`profileId=1`), however. The current profile guard and profile-scoped queries prove the POC boundary;
they are not production certification of arbitrary multi-profile operation or isolation.

## Reconciliation state machine

`PENDING` means not yet evaluated. `MATCH` means one same-profile canonical fact matches. `NEW` means no same-profile candidate exists and the row may be promoted. `MISMATCH` means a candidate exists but accounting values differ. `AMBIGUOUS` means multiple same-profile candidates require review. `PROMOTED` is terminal.

Only `NEW` may promote. `MATCH` never rewrites canonical facts. `MISMATCH` and `AMBIGUOUS` require review. Staging never silently overwrites canonical facts. Promotion is explicit and `PROMOTED` remains terminal and idempotent.

## Tests

`AccountingStagingFlowIT` is the main PostgreSQL persistence contract for:

```text
source -> staging -> reconciliation -> promotion -> canonical
```

The manual E2E sequence is: start on an empty operational workspace, import one Jan-Aug bank CSV, upload or sync source documents, reconcile each month, promote only `NEW` rows, run the normal calculation, and compare each month with its reference rows. A bank file is parsed once and its rows are routed by authoritative `booking_date`; the month open in the UI does not constrain routing.

## POC limitations

The POC keeps its deliberate limitations, including heuristic/non-exclusive payment matching and the existing constrained FX/accounting workflow. It does not add a general payroll, statutory-rate, or workflow-configuration subsystem.

Full multi-profile support and isolation certification are post-POC roadmap work. That work must cover
cross-profile acquisition, staging, canonical facts, filing state, uniqueness constraints,
authorization, and end-to-end isolation tests before the feature is treated as production-qualified.
