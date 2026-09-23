# Ryczalt

`ryczalt` is the active native accounting module for JDG/ryczałt functionality. The old
`modules/accounting` tree is outside the Maven runtime reactor and remains historical reference
material until a separate cleanup decision.

```text
ryczalt    = active implementation
accounting = retired historical/reference source
```

Historical Flyway migrations and reference database tables remain intact. Runtime accounting reads
and writes use canonical `ryczalt_*` tables.

## Architecture direction

Investory remains a modular monolith, not a microservice. The accounting month is the primary
processing unit. The domain uses simple canonical models; external/source-specific models must not
leak into it. Future calculators will contain pure accounting and tax math, checkers will contain
validation and reconciliation, application services will orchestrate use cases, ports will define
data requirements, and adapters will provide configurable sources.

Calculations should be persisted rather than repeatedly recomputed. Normally only open or dirty
periods are recalculated; paid and frozen historical periods are immutable facts. Historical
reference data, including FX rates, is persisted. Reopening or correction is explicit. Calculation
results will carry rule/calculator versions. Rounding belongs to versioned calculation rules, not
generic formatting.

The intended flow is:

```text
sources -> adapters -> ports -> AccountingPeriod -> calculators
        -> obligations/results -> checkers -> persisted/frozen accounting state
```

## Staged migration

1. foundation/domain
2. calculators + rules
3. legacy Accounting DB adapters
4. parity testing
5. new persistence
6. data migration
7. checkers/payment lifecycle
8. native integrations
9. application cutover
10. remove accounting

## Current stage: native accounting runtime

Stage 2 added pure calculators over already-normalized facts. Stage 3 added separate JPA persistence;
the one-way legacy import utility has since been removed:

- The old aggregate persistence adapter and domain mapper are removed. Native query services and
  repositories read the canonical `ryczalt_*` tables.
- Legacy import code is not part of the active Ryczalt runtime. Historical legacy tables remain for
  the planned database cleanup.
- JPA entities, repositories, calculation records, FX facts, and profile-scoped constraints are
  under `persistence`.

The versioned rule sets are `RyczaltRules2026`, `VatRules2026`, and `ZusRules2026`. The shared
`RoundingPolicy` exposes named semantic operations for FX, contributions, deductions, ryczałt, and
VAT settlement. Inputs are normalized PLN facts. Source acquisition, persistence, reconciliation, native REST, Web, and mobile-facing accounting contracts are active; remaining work is lifecycle and historical-data cleanup.

Stage 4 added revisioned calculation history, deterministic fingerprints, targeted invalidation,
explicit freeze/reopen/correction services, and audit events. Stage 5 added pure payment and period
completeness checkers, persisted partial payment matches, deterministic automatic settlement, manual
matching, and frozen-settlement protection. `RyczaltAccountingApi` and
`RyczaltAccountingFacade` now own the native application boundary. Native REST uses that boundary.
Native settlement and lifecycle operations run entirely on Ryczalt persistence. Frozen periods are load-only.

The `modules/ryczalt` Maven dependency on `accounting` is removed. Reference/golden tables remain
comparison evidence and are not imported as canonical facts.

Native source capability is incremental. `RyczaltFxRateService` reads persisted historical NBP facts
before calling the reusable `NbpClient`; acquired rates are stored once with the provider reference.
The date decision is in `FxRateDatePolicy`, not in the HTTP client. The direct `integrations`
dependency is for reusable source clients only.

Current capability:

```text
RYCZALT  calculation ✓  persistence ✓  payment detection ✓  manual matching ✓  external verify ✗
VAT      calculation ✓  persistence ✓  payment detection ✓  manual matching ✓  external verify ✗
ZUS      calculation ✓  persistence ✓  payment detection ✓  manual matching ✓  eZUS verify ✗
```

Source integration matrix:

```text
Invoices / KSeF        YES  native candidate/approval and KSeF sync paths persist Ryczalt facts
Transactions / Bank    YES  native CSV import persists canonical Ryczalt transactions
FX / NBP               YES  NbpClient -> NbpFxRateAdapter -> FxRateSourcePort -> ryczalt_fx_rate
ZUS external verify    NO   no reusable production eZUS verification client
```

`docs/cutover-audit.md` is historical audit evidence, not a description of the active runtime.

## Counterparties and learned rules

`Counterparty` is the legal supplier/customer identity. Its profile-scoped identity uses
`taxIdentifier + country` when available; missing tax identifiers are not invented. `alias` is only
a friendly presentation name. Display uses the alias when nonblank, otherwise the legal name.

`CounterpartyRule` stores a reusable accounting decision for a service shape, not one permanent
category for the whole company. Stage 2 persists source/document/service matching signals,
`classification`, `vatTreatment`, VAT deduction ratio, and ryczalt rate. `autoApprove` belongs to
the rule.

Matching is exact and deterministic: every nonblank rule criterion must equal the invoice fact.
No match or multiple matches means `NEEDS_REVIEW`; no arbitrary rule is selected.

Approval is separate from payment evidence. Approval is `NEEDS_REVIEW` or `APPROVED`, with origin
`MANUAL`, `COUNTERPARTY_RULE`, or `MIGRATION`. `PaymentVerificationPolicy` is `REQUIRED` or
`NOT_REQUIRED`. A cash-paid fuel invoice can therefore be approved by a matching fuel rule with
`NOT_REQUIRED` and produces no missing-bank-evidence attention. A `REQUIRED` rule keeps normal
payment matching and unresolved-payment attention.
# Native invoice workflow

Invoice upload follows a native Ryczalt flow:

1. `POST /api/profiles/{profileId}/accounting/invoices/recognize` calls `InvoiceRecognitionPort` and persists source facts as an invoice candidate.
2. The candidate resolves counterparties by tax identifier and country, then evaluates deterministic counterparty rules.
3. `POST /api/profiles/{profileId}/accounting/invoices` accepts the candidate key and user decisions. It validates the authoritative period, writes `ryczalt_invoice`, records provenance, and invalidates affected calculations in one transaction.

Upload identity is the SHA-256 of the file, scoped by profile and source. It is used for idempotency and duplicate detection. Names and aliases do not merge counterparties. Income upload is not yet supported; KSeF remains the native income acquisition path.
