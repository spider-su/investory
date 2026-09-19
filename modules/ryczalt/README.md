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

## Current stage: REST/application cutover bridge

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
matching, and frozen-settlement protection. The current cutover bridge exposes the existing REST
contract through `RyczaltUserApi`; unsupported operations delegate through the temporary
`LegacyAccountingUserApiAdapter`. Native settlement and lifecycle operations are selected for periods
already present in the Ryczalt schema. Frozen periods are load-only.

The legacy `accounting` dependency is intentionally temporary and must be removed after native REST
capabilities replace the delegated operations. Reference/golden tables remain comparison evidence and
are not imported as canonical facts.

Current capability:

```text
RYCZALT  calculation ✓  persistence ✓  payment detection ✓  manual matching ✓  external verify ✗
VAT      calculation ✓  persistence ✓  payment detection ✓  manual matching ✓  external verify ✗
ZUS      calculation ✓  persistence ✓  payment detection ✓  manual matching ✓  eZUS verify ✗
```
