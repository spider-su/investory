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

The intended future flow is:

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

## Current stage: calculation layer

Stage 2 adds pure, dependency-free calculators over already-normalized facts:

- `RyczaltCalculator` calculates multi-rate revenue, eligible social/health deductions, taxable
  base, tax, and deduction carry-forward.
- `VatCalculator` settles output VAT, sales corrections, deductible input VAT, and explicit
  adjustments.
- `ZusCalculator` covers the supported JDG/UoP insurance cases and 2026 health bands.

The versioned rule sets are `RyczaltRules2026`, `VatRules2026`, and `ZusRules2026`. The shared
`RoundingPolicy` exposes named semantic operations for FX, contributions, deductions, ryczałt, and
VAT settlement. Inputs are normalized PLN facts; source classification, FX acquisition, database
adapters, persistence, reconciliation, UI, and application cutover remain future stages.

Stage 2 has no dependency on `accounting`, `app`, or `test-support`. Its owned tests use a small
adapted HappyInvestor fixture and compare selected normalized values with the existing reference
oracle. The old `accounting` module remains the operational reference until later parity and
cutover stages are complete.
