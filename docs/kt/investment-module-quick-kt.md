# Investment module --- quick KT

> **Goal:** safely navigate the source-of-truth ledger and its derived portfolio reporting.

## What it owns

Investment records brokerage facts: assets, accounts, positions, cash
movements, transactions, imports, prices, FX rates, portfolio projections,
performance, and reconciliation. It is the authoritative source for traded
portfolio history. It does not own long-term assets or retirement planning.

``` text
broker files / manual prices / market + FX adapters
                         |
                         v
              import + ledger persistence
                         |
                         v
              valuation / projections / stats
                         |
                         +--> dashboard and asset APIs
                         +--> historical actuals for Profile/Retirement
                         +--> reconciliation report
```

## Where to start

- Public contracts: `investment.api.*`, especially `InvestmentDashboardApi`, `InvestmentAssetApi`, and `InvestmentImportApi`.
- Commands/imports: `investment.imports.*`, `InvestmentMaintenanceApplicationService`.
- Ledger: `investment.ledger.*`.
- Prices and FX: `investment.valuation.price.*` and `investment.valuation.fx.*`.
- Derived data: `investment.projection.*`, `investment.reporting.*`, and `investment.reconciliation.*`.
- MVC presentation belongs in `adapters/web-ui`; business REST controllers under `investment.web` are API adapters.

## Safe-change rules

- Preserve signed quantities, asset identity, position currency, and FX direction; see `docs/domain/asset-identity-and-money.md` and `docs/domain/fx-normalization.md`.
- Treat imports as auditable and idempotent. Do not bypass import orchestration to write ledger rows directly.
- Rebuild derived projections after source changes through the existing refresh path. Do not hand-edit reporting tables.
- Keep reporting and reconciliation read paths portfolio-scoped and fail-closed when required data is missing.

Canonical detail: `docs/domain/portfolio-accounting.md` and `docs/quality/reconciliation.md`.
