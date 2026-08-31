# Investment & Dashboard — Manual QA Plan

## Scope

Broker accounts, imports, positions, cash ledger, prices/FX, portfolio dashboard and asset detail. UI routes: `/dashboard` and `/dashboard/assets/{symbol}`.

## Before testing

- Use a disposable portfolio or record its ID and baseline values. Never alter production facts to test.
- Have one known-good IBKR CSV and XTB XLSX/ZIP, including a duplicate of each; use deliberately invalid files only in a non-production environment.
- Note the deployed image/version, pod name, test account IDs and the dashboard as-of date.
- For every failure capture browser URL/time, request/response, relevant DB rows, pod logs, and the input-file checksum.

## Test cases

| ID | UI action | Expected result | DB / Kubernetes evidence |
| --- | --- | --- | --- |
| INV-01 | Open `/dashboard`; switch each active account and each period (`1M` … `MAX`). | Account selector and period update the scoped report. Current portfolio value and inception metrics do not change solely because the period changes; period profit and benchmark do. | Compare displayed values with reporting views and `account_daily`; inspect request logs for the selected account/period. |
| INV-02 | Check headline value, cash, net contributions, earnings, ROI, allocation, currency exposure, positions and monthly-performance panels. | Values are internally consistent, formatted naturally, and have a visible as-of context where applicable. | Trace each sampled value to its documented reporting source; record rounding delta only, never accept a material mismatch. |
| INV-03 | Open an asset from the positions panel and test a known symbol plus an unknown/invalid symbol. | Known asset detail shows canonical identity, quantity, valuation/price context and account holdings; unknown symbol shows the dedicated not-found state without a server error. | Verify `assets`, `asset_source_symbols`, `positions`, price and FX inputs correspond to the rendered asset. |
| INV-04 | Import a valid IBKR statement, then refresh dashboard after processing completes. | Import has clear terminal status; normalized facts appear once; dashboard changes match the statement’s economic effect. | `import_history`, `import_source_files`, `import_source_rows`, `cash_operations`, `positions`; importer logs contain file ID/checksum and no unhandled exception. |
| INV-05 | Repeat the exact same broker file. | No duplicate economic rows or doubled dashboard impact; UI/API communicates the linked reprocess/duplicate outcome. | Same broker + SHA-256 is traceable in import tables; counts and portfolio totals remain economically unchanged. |
| INV-06 | Import malformed/unsupported input and a file with unknown or ambiguous asset mapping. | Import fails or becomes partial with an actionable error; no guessed asset is created and valid prior facts remain intact. | Failed/partial status and error are stored; inspect logs; ensure no unintended `assets`, mappings, positions or cash rows were created. |
| INV-07 | Review a dividend, interest, buy, sell, deposit, withdrawal, internal transfer and FX conversion from fixture data. | Deposits/withdrawals affect contributions; internal transfer and FX conversion do not create portfolio profit/contribution; investment earnings follow the accounting contract. | Reconcile signs, currencies and operation classes in `cash_operations`/`positions` to source rows and dashboard metrics. |
| INV-08 | Force or select an asset with manual price fallback, stale/missing price, and stale/missing FX in a safe environment. | Valid fallback is clearly used; missing or stale FX fails closed rather than silently treating amount as target currency. | Inspect `asset_price_history`, `exchange_rates`, price/FX provenance and logs. Confirm no fabricated conversion/value. |
| INV-09 | Wait for or trigger scheduled market/FX refresh in a non-production environment. | Job does not overlap; successful refresh updates expected inputs only; provider failure is visible and does not corrupt last known-good data. | Pod logs, scheduler/job state, `asset_price_history`, `exchange_rates`; inspect restarts, OOM events and error rate. |
| INV-10 | Check mobile/narrow and desktop dashboard rendering; reload/back/forward after filter changes. | Header, account/period controls, charts/tables and tooltips remain usable; no console errors or stale/filter-mismatched panels. | Browser network/console; application logs show no template/render exceptions. |

## Release exit

- At least one clean import per supported broker and an exact-file reprocess completed.
- Sampled dashboard totals reconcile to the reporting layer within display rounding.
- No `ERROR`, restart, OOM, or failed scheduled-job evidence related to the test window.
