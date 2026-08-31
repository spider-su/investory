# Reconciliation & Data Quality — Manual QA Plan

## Scope

Developer-facing reconciliation page `/dashboard/reconciliation`, persisted system audit and pipeline checkpoints C0–C7. This is a release-quality workflow, not an end-user accounting screen.

## Preconditions

- Use a controlled broker corpus with an archive manifest where available; retain the exact input checksums.
- Record database as-of time, deployment version and selected account/portfolio scope.
- Refresh only the documented production reporting materialized views in the documented order. Reconciliation refresh is explicit/on demand.

## Test cases

| ID | Action | Expected result | DB / Kubernetes evidence |
| --- | --- | --- | --- |
| REC-01 | Open reconciliation before running a new audit. | Page makes execution/as-of/mode clear; an unexecuted checkpoint is not presented as PASS. | Latest `system_audit_runs`/`system_audit_issues`; UI must preserve `NOT_CHECKED`. |
| REC-02 | Run a healthy controlled audit and review checkpoints in C0–C7 order. | Required executed checks pass or have documented `REVIEW`; aggregate counts agree with detail rows and first failing checkpoint logic. | Audit run/issue tables; application logs and recon output; record run ID. |
| REC-03 | C0: compare supplied file manifest with import history and source evidence. | Every file is accounted for; completed import has no unexplained failures; checksum links are coherent. | `import_history`, `import_source_files`, `import_source_rows`, provenance issue view. |
| REC-04 | C1/C2: sample source cash and trades to normalized ledger/positions. | Counts, signed amounts, classes, currency/date coverage and open/closed quantities reconcile. | Source evidence, `cash_operations`, `positions`, C1/C2 views/issues. |
| REC-05 | C3: check price/FX requirements for a non-base holding and deliberately missing/stale fixture. | Valid provider/date/provenance is accepted; missing/stale conversion fails closed and becomes a meaningful quality issue, not a fabricated value. | `asset_price_history`, `exchange_rates`, mappings, issue rows and logs. |
| REC-06 | C4/C5: compare selected daily account snapshots with reporting/monthly/portfolio totals. | Equity/cash/market value/flows/investment result reconcile; economic-truth identity is plausible after external flows and portfolio-scope transfers. | `account_daily`, reporting views/materialized views, reconciliation views; retain full-precision differences. |
| REC-07 | C6: sample dashboard values from 2–3 panels against documented reporting sources. | Display equals source within presentation rounding; no dashboard-only calculation changes financial truth. | Screenshot/time/filters plus SQL results and view lineage. |
| REC-08 | C7: compare Yahoo export and notification/secondary payload if exposed. | Matching current adapter payload passes; missing snapshot is `REVIEW`, stale snapshot is `FAIL`. | `yahoo_export_state`, notification events, adapter/audit output. |
| REC-09 | Introduce one controlled failure (e.g., missing required valuation input) and rerun. | Original checkpoint shows `FAIL`; later results display `BLOCKED` only as presentation, while original status/counts remain truthful. | Audit run and issue original/effective status, `firstFailingCheckpoint`, logs. |
| REC-10 | Review account quality classifications and dashboard data-quality indicators. | `RECONCILED`, `REVIEW` and `UNRECONCILED` retain their distinct meaning; review rows are not suppressed to improve counts. | Persisted issues/reporting quality output and rendered dashboard indicator. |

## Operational triage

When a checkpoint fails, capture in this order: audit run ID → earliest original failure → source/checksum → normalized row IDs → valuation inputs → reporting refresh status → rendered/UI evidence → correlated pod logs. Check pod restarts, OOM kills, failed migrations and database connectivity before classifying a computation defect.

## Release exit

- A recorded audit covers required C0–C6 checks; C7 is evaluated when the relevant adapter is enabled.
- No material `FAIL` remains. Every `REVIEW` has an owner, rationale and release decision.
- Reconciliation values are retained at full precision; display rounding never determines PASS/FAIL.
