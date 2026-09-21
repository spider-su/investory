# Ryczalt

`ryczalt` is the replacement accounting module for JDG/ryczałt functionality. It is developed
alongside the existing `accounting` module:

```text
accounting = existing/reference implementation
ryczalt    = new implementation
```

The existing module remains functional during this staged migration and will be removed only after
feature and data parity, cutover, and verification.

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

## Current stage: native source integration and REST/application cutover bridge

Stage 2 added pure calculators over already-normalized facts. Stage 3 adds separate JPA persistence
and a one-way legacy import:

- `RyczaltPersistenceAdapter` loads and saves canonical `AccountingPeriod` facts from `ryczalt_*`
  tables.
- `RyczaltMigrationService` imports operational legacy POC rows once, idempotently, with source
  references. It is not a runtime legacy adapter.
- JPA entities, repositories, calculation records, FX facts, and profile-scoped constraints are
  under `persistence`.

The versioned rule sets are `RyczaltRules2026`, `VatRules2026`, and `ZusRules2026`. The shared
`RoundingPolicy` exposes named semantic operations for FX, contributions, deductions, ryczałt, and
VAT settlement. Inputs are normalized PLN facts; source classification, FX acquisition, database
adapters, persistence, reconciliation, UI, and application cutover remain future stages.

Stage 4 added revisioned calculation history, deterministic fingerprints, targeted invalidation,
explicit freeze/reopen/correction services, and audit events. Stage 5 added pure payment and period
completeness checkers, persisted partial payment matches, deterministic automatic settlement, manual
matching, and frozen-settlement protection. `RyczaltAccountingApi` and
`RyczaltAccountingFacade` now own the native application boundary. Native REST uses that boundary;
the app module owns the temporary `LegacyAccountingApiBridge` for old routes. Native settlement and
lifecycle operations are selected for periods already present in the Ryczalt schema. Frozen periods
are load-only.

The `modules/ryczalt` Maven dependency on `accounting` is removed. Reference/golden tables remain
comparison evidence and are not imported as canonical facts.

Native source capability is incremental. `RyczaltFxRateService` reads persisted historical NBP facts
before calling the reusable `NbpClient`; acquired rates are stored once with the provider reference.
The date decision is in `FxRateDatePolicy`, not in the HTTP client. The direct `integrations`
dependency is for reusable source clients only and does not replace the temporary REST bridge.

Current capability:

```text
RYCZALT  calculation ✓  persistence ✓  payment detection ✓  manual matching ✓  external verify ✗
VAT      calculation ✓  persistence ✓  payment detection ✓  manual matching ✓  external verify ✗
ZUS      calculation ✓  persistence ✓  payment detection ✓  manual matching ✓  eZUS verify ✗
```

Source integration matrix:

```text
Invoices / KSeF        NO   reusable transport exists; Ryczalt FA(3) normalizer not yet native
Transactions / Bank    NO   reusable CSV source exists; Ryczalt sync service not yet wired
FX / NBP               YES  NbpClient -> NbpFxRateAdapter -> FxRateSourcePort -> ryczalt_fx_rate
ZUS external verify    NO   no reusable production eZUS verification client
```

See [docs/cutover-audit.md](docs/cutover-audit.md) for the detailed endpoint, dependency, and
deletion-blocker audit.

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
