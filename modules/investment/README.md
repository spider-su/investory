# Investment module

`investment` owns the canonical brokerage ledger and its derived valuation, performance, reporting,
projection, import, export, and reconciliation data. It does not own Long-Term assets or retirement
planning.

## Flow

```text
source files / prices / FX
          -> imports and ledger persistence
          -> valuation, projections, reporting
          -> APIs, dashboard, Profile, Retirement, reconciliation
```

## Boundaries

- `api`: public read and application contracts for downstream modules.
- `ledger` and `infrastructure`: canonical brokerage facts and persistence.
- `imports`: auditable, idempotent source ingestion.
- `valuation`, `projection`, `performance`, and `reporting`: derived facts.
- `reconciliation`: independent quality and economic-truth checks.
- `web`: business REST adapters; server-rendered UI remains outside the module.
- `port`: replaceable market, FX, import, and export adapter contracts.

Preserve signed quantities, canonical asset identity, position currency, portfolio scoping, and FX
direction. Source changes must use the existing import/refresh paths rather than writing derived
tables directly.

Detailed guidance: [`docs/kt/investment-module-quick-kt.md`](../../docs/kt/investment-module-quick-kt.md),
[`docs/domain/portfolio-accounting.md`](../../docs/domain/portfolio-accounting.md), and
[`docs/quality/reconciliation.md`](../../docs/quality/reconciliation.md).
