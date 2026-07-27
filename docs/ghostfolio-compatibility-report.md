# Ghostfolio Compatibility Report

Generated: 2026-07-27

Scope: read-only endpoints currently implemented by Investory under `/api/v1`,
`/api/v2`, and `/api/assets` for the Ghostfolio frontend.

This is a focused compatibility note, not the canonical source of current repository architecture.
If the HTTP surface or runtime invariants change, update `AGENTS.md` first and then adjust this
report as needed.

## Matrix

| Endpoint | Ghostfolio contract | Investory source | Controller | Mapper/orchestrator | Status | Remaining gaps |
| --- | --- | --- | --- | --- | --- | --- |
| `POST /api/v1/auth/anonymous` | Auth token envelope | Local dev compatibility profile | `GhostfolioAuthController` | Controller only | Partial | Dev-only token; not a real Ghostfolio auth service. |
| `GET /api/v1/user` | Current user with settings/accounts | `GhostfolioCompatibilityService.accounts()` | `GhostfolioUserController` | Controller DTO map | Partial | Single local user only. No Ghostfolio user store, access grants, subscription state, or mutable permissions. Permissions are read-only. |
| `GET /api/v1/user/settings` | User settings | Investory base-currency config | `GhostfolioUserController` | Controller DTO map | Partial | Only settings required by frontend bootstrap are exposed. |
| `GET /api/v1/info` | Frontend config | Spring/app config | `GhostfolioInfoController` | Controller DTO map | Partial | Feature flags describe Investory compatibility, not a full Ghostfolio deployment. |
| `GET /api/v1/health` | Health status | Local app availability | `GhostfolioHealthController` | Controller DTO map | Complete | None. |
| `GET /api/assets/{languageCode}/site.webmanifest` | PWA manifest | Static Investory metadata | `GhostfolioAssetsController` | Controller DTO map | Complete | None. |
| `GET /api/v1/watchlist` | `WatchlistResponse`: watchlist array | Static empty compatibility envelope | `GhostfolioBenchmarkController` | Controller DTO map | Partial | Investory has no watchlist store; returns an empty list with the correct Ghostfolio shape. |
| `GET /api/v1/benchmarks` | `BenchmarkResponse`: benchmarks array | Static Ghostfolio-compatible bootstrap list | `GhostfolioBenchmarkController` | Controller DTO map | Partial | Values are compatibility bootstrap metadata, not Investory performance calculations. No benchmark market-data endpoint is implemented. |
| `GET /api/v1/account` | `AccountsResponse`: accounts, activity count, balance/dividend/interest/value totals | `PortfolioService.calculateTotalProfitLoss()`, `account_daily`, ledger repositories | `GhostfolioAccountController` | `GhostfolioCompatibilityService` | Complete for exposed Investory data | Ghostfolio platform metadata is not stored by Investory. |
| `GET /api/v1/account/{id}` | Single account DTO | Same as `/account`, filtered by id | `GhostfolioAccountController` | `GhostfolioCompatibilityService` | Complete for exposed Investory data | 404 if account is absent from Investory portfolio summary. |
| `GET /api/v1/account/{id}/balances` | `AccountBalancesResponse` history | `account_daily` | `GhostfolioAccountController` | `GhostfolioCompatibilityService` | Complete | No synthetic current balance is returned when daily history is empty. |
| `GET /api/v1/activities` | `ActivitiesResponse`: activities, count, filters, sort, pagination | `cash_operations`, open/closed `positions`, `CurrencyRateService`, `assets` | `GhostfolioActivitiesController` | `GhostfolioCompatibilityService` | Complete for Investory ledger | Ghostfolio tags/comments/import source metadata are not exposed because Investory ledger does not provide matching fields. |
| `GET /api/v1/portfolio/holdings` | `PortfolioHoldingsResponse`: holdings list | `PortfolioService.calculateTotalProfitLoss().openPositionValues`, `positions`, `assets`, dividend ledger | `GhostfolioHoldingsController` | `GhostfolioCompatibilityService` | Complete for open holdings | Tags, sectors, and external asset URLs remain empty/null unless present in Investory assets. |
| `GET /api/v1/portfolio/details` | `PortfolioDetails`: accounts map, holdings map, platforms map, summary, createdAt | `PortfolioService`, `/account` rows, `/holdings` rows, ledger first activity | `GhostfolioPortfolioDetailsController` | `GhostfolioCompatibilityService` | Complete for exposed Investory data | Markets and marketsAdvanced are omitted because Investory has no equivalent market aggregation source. |
| `GET /api/v2/portfolio/performance` | `PortfolioPerformanceResponse`: chart, first activity dates, performance summary | `PortfolioService.calculateTotalProfitLoss()`, `account_daily` | `GhostfolioPortfolioController` | `GhostfolioCompatibilityService` | Complete for Investory history | Benchmark series is not returned; Investory benchmark calculation is dashboard-specific and not exposed in Ghostfolio response shape yet. |

## Upstream Contract Notes

- `GET /api/v1/account` requires top-level `accounts`, `activitiesCount`,
  `totalBalanceInBaseCurrency`, `totalDividendInBaseCurrency`,
  `totalInterestInBaseCurrency`, and `totalValueInBaseCurrency`.
- `GET /api/v1/account/{id}/balances` returns only real balance rows from history.
- `GET /api/v1/activities` supports account, activity type, range, symbol, sorting,
  `take`, and `skip`.
- `GET /api/v1/portfolio/holdings` returns a list under `holdings`.
- `GET /api/v1/portfolio/details` returns maps keyed by account id, symbol, and
  platform id.
- `GET /api/v2/portfolio/performance` returns real historical chart points, not a
  duplicate of the current portfolio value.

## Source Rules

- Current account and portfolio totals come from `PortfolioService`.
- Historical account balances and performance chart points come from
  `account_daily`.
- Activities come from Investory ledger tables: `cash_operations` and unified
  `positions`.
- Base-currency conversion for activities uses `CurrencyRateService`.
- Asset metadata comes from `assets`.
- No reflection mapper remains in the Ghostfolio compatibility package.
