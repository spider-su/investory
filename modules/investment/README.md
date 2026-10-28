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

## Background refresh after imports

The investment import API saves broker facts and audit history before returning. The default
request then persists an `import_refresh_job`; the HTTP response says the import is saved and
refresh is queued. `deferRefresh=true` explicitly skips queueing, for callers managing refresh
separately. The browser queues each uploaded file and polls
`GET /api/v1/portfolios/{portfolioId}/investment/imports/{batchId}/refresh`.

A dedicated scheduler serializes refreshes across pods using a PostgreSQL session advisory lock.
It processes explicit manual-import jobs even when automatic integration scheduling is disabled.
Pending jobs survive restarts; interrupted RUNNING jobs are recovered after the lock is released.
Refresh retains the parser's exact affected-account scope in the queue, including snapshot removals,
then performs price-history coverage. Existing reconciliation/audit scheduling remains separate.

Import status records broker persistence; refresh status is PENDING/RUNNING/COMPLETED/FAILED.
A refresh failure retains the imported ledger. Terminal refresh state and its portfolio-scoped
Telegram notification outbox event commit together. Delivery retries use the existing notification
outbox. Do not re-upload an imported statement solely to retry a failed projection refresh.
