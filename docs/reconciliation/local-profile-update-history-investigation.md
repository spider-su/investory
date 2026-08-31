# Local `POST /admin/update-history` investigation

Generated: 2026-08-13

## Endpoint path

The endpoint is already present:

```text
POST /admin/update-history
```

It requires the existing ADMIN authentication and executes synchronously:

```text
MarketService.refreshMarketPricesAndPositions()
PortfolioProjectionService.recalculateAll()
PortfolioProjectionService.refreshReconciliationViews()
```

The reconciliation refresh order is:

```text
reconstructed position daily
reconstructed account market daily
reconstructed cash daily
account/day reconciliation
reconciliation reporting
```

## Call result and performance findings

The local call was made against the configured local database with the existing app. The HTTP
client timed out after 300 seconds without receiving a response. The server-side operation
continued and completed; after it became idle, `latest_reporting_refresh_at` was:

```text
2026-08-13 11:23:07.940664
```

Database activity identified the slow path as the materialized-view refresh stages. During the
observation the active statements advanced through:

```text
refresh_reconstructed_cash_daily
refresh_account_daily_reconciliation
refresh_reconciliation_reporting_views
```

No `pg_stat_activity.wait_event` or lock-wait blocker was observed. Normal refresh does hold
`AccessExclusiveLock` on `mv_reconstructed_cash_daily`, so concurrent readers can still be
blocked while a stage runs. The main demonstrated issue is synchronous request duration, not a
deadlock.

The endpoint has no controller-level timing or asynchronous job response. A client can therefore
report failure/timeout while the refresh is still changing derived state.

## Reconciliation report

The existing generator was run after the refresh and written to:

* [local-profile-update-history-after-completed-report.md](local-profile-update-history-after-completed-report.md)

The generator keeps the established materiality rule:

```text
abs(diff) > max($20, 1% * max(abs(expected), abs(actual)))
```

Its per-query 20-second timeout canceled some expensive report queries. Those checks were rerun
directly after the refresh with longer command limits; the values below are the current status.

## Current status by reconciliation layer

| Layer | Current result |
|---|---|
| FX conversion | 6 ESTIMATED rows; 72 reciprocal mismatches |
| Price contract | 2,633 currency mismatches; 2,038 scale-mapping mismatches; 7 extreme same-date source disagreements |
| Position reconstruction | 42,047 rows: 37,263 PASS, 4,784 WARN |
| Position valuation | 306 material rows; maximum absolute difference 7,518.99 |
| Position currency validation | 0 issues |
| Cash reconstruction | 5,869 PASS rows; 282 material cash-flow gaps; maximum absolute gap 10,624 |
| Account/day reconciliation | 3,792 PASS INFO; 1,855 WARN; 222 FAIL; 78 material mismatches; maximum absolute difference 6,713 |
| Account statistics vs latest daily | 5 VALUE_MISMATCH, 6 OK |
| Monthly profit reconciliation | 164 OK, 40 MISMATCH; 39 material rows; maximum difference 10,323 |
| Portfolio validation | 2,309 PASS, 1,020 WARN, 222 FAIL |
| Portfolio quality | CRITICAL; 11 accounts, 7 reconciled and 4 unreconciled; no missing prices or FX |
| Trade settlement | 2,627 PASS; 159 `VALUATION_RECONSTRUCTION_FAILED`; remaining rows are REVIEW diagnostics |
| Realized-result completeness | 1,574 complete rows when checked directly |
| Non-USD closed-trade diagnostics | 47 `MISSING_PREVIOUS_POSITION`, 1,219 OK |

Position identity, position currency validation, duplicate position lots, cash incomplete rows,
unsupported transaction states, and timezone checks did not add material failures in the report.

## Logical findings

1. The endpoint is a long synchronous maintenance operation. The 300-second client timeout is
   reachable even though the server can finish successfully.
2. The staged reconciliation order is deterministic and was observed progressing in the intended
   order.
3. The refresh stages are independent database calls, but normal MV refresh locking remains
   visible to readers.
4. The endpoint response does not distinguish “still running” from “failed”, so timeout handling
   can mislead the operator. No source financial facts were changed during this check.
5. The local application logged a Flyway warning because the database reports version `01.011`
   while the checked-out migration directory ends at `01.009`. This is a deployment/history
   consistency issue to resolve separately; it did not prevent the endpoint from running.

## Recommended next action

Keep the financial and reconciliation formulas unchanged for this investigation. The smallest
operational improvement is to expose refresh progress/result separately from the HTTP request (or
raise the client timeout with an explicit “refresh still running” response). Before changing that,
capture controller/stage elapsed logs in the running application so exact per-stage durations can
be measured. Investigate the earliest remaining data defect next: price currency/scale contract,
then position valuation.
