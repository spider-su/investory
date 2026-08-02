# Pipeline Testing Plan (import files → UI)

Goal: prove the whole Investory pipeline stage by stage. Each checkpoint is validated
against the previous one, with **economic-truth** checks, not only internal consistency.
The plan is designed to catch the two defect classes already seen in this repo:

- incomplete imports (a source file silently never loaded), and
- cash misclassification that becomes fake profit (cross-account currency conversion
  classified as `FX_CONVERSION`).

## Pipeline stages (data lineage)

```
files → import (cash_operations, positions, assets, import_history)
      → position reconstruction
      → market prices + FX (assets.market_price_usd, exchange_rates)
      → PortfolioProjectionService.recalculateAll() → account_daily
      → materialized views (account_monthly_mv, portfolio_kpi_summary,
         portfolio_asset_allocation, ...)
      → dashboard UI (HomeController / PortfolioService → dashboard.html)
      → secondary: Ghostfolio API, Yahoo export, Telegram
```

## Test-data strategy

- **Synthetic golden set** — small, deterministic, versioned test resource covering edge
  cases: cross-account currency conversion, cash-only account, `RESULT_ONLY` CFD,
  dividend + withholding tax, inception deposit, multi-file period boundary. Used in CI.
- **Real fixture set** — the actual XTB zips + IBKR CSVs in `src/main/resources`, used for
  staging reconciliation against the local DB.
- **Expected-values manifest** — the source files themselves are the oracle for C0/C1;
  higher checkpoints assert invariants derived from lower ones.

## Checkpoints

Each checkpoint: input → invariant → tool → pass rule.

| ID | Boundary | Key invariants | Pass rule |
|----|----------|----------------|-----------|
| **C0** | files → import_history | every provided file has a `COMPLETED` row, `rows_failed = 0`; account coverage + `max(date)` current | no missing file, no stale account |
| **C1** | files → cash_operations | per account: row count, `sum(amount)`, per-type count+sum, min/max date; dedup by broker id | exact match to source files |
| **C2** | cash_operations → positions | reconstructed open/closed volumes match broker; signed quantity; `settlement_model`; `RESULT_ONLY` not valued at notional | volumes/symbols match broker |
| **C3** | prices + FX | `assets.market_price_usd` present for open symbols; `exchange_rates` cover needed months/pairs | 0 unpriced open positions (or on manual allowlist) |
| **C4** | account_daily | `equity = cash_balance + market_value`; ledger sum = ending cash via FX; every non-trade/non-market cash movement is a flow, never profit | all invariants hold |
| **C5** | matviews | `account_monthly` reconciliation OK; `portfolio_kpi_summary` == roll-up of `account_daily`; `portfolio_asset_allocation` == open positions | reconciliation OK **and** economic-truth passes |
| **C6** | dashboard UI | header totals == `portfolio_kpi_summary`; account table == `account_daily`; P/L-by-period bars == `account_monthly.total_profit`; open-position popup == `portfolio_asset_allocation` | UI == DB within rounding |
| **C7** | secondary | Ghostfolio parity, Yahoo export, Telegram command values == dashboard | optional |

## Economic-truth layer

Internal-consistency views can say "OK" while numbers are economically wrong (the
`FX_CONVERSION` phantom did exactly this). Add independent recompute:

- `monthly P/L = Δequity − external_flows − internal_transfers`, compared to
  `account_monthly.total_profit`.
- "No unexplained profit": each month's profit must be explained by market moves +
  realized results; any residual tied to a non-flow cash category → FAIL.
- Reconcile to broker truth where available: IBKR NAV / Open Positions, XTB closed
  position `Profit` sum vs realized.

## Regression guards (lock in bugs already found)

1. No `currency conversion,% from TA: X to: Y` row classified `FX_CONVERSION`
   (must be `INTERNAL_TRANSFER_*`).
2. Import completeness (C0).
3. Inception month `opening_equity = 0`, no deposit double-count.

## Automation levels

- **L1 fast** — parser + classifier unit/slice tests on synthetic rows (no DB). Every push.
- **L2 integration** — `@SpringBootTest` on Testcontainers Postgres: import golden set via
  `ImportOrchestratorService`, run `recalculateAll()`, assert C1–C5 + guards. CI gate.
- **L3 staging reconciliation** — real files on local DB via `ReconRunner`, emits a
  per-checkpoint PASS/FAIL report. Manual / pre-release.

---

## L3 ReconRunner — C0 & C1 (implemented)

Location: `tools/ReconRunner.java` (standalone; uses Apache POI from the project
classpath + the PostgreSQL JDBC driver). Companion to `tools/DbProbe.java` and
`tools/XlsxRecon.java`.

Run:

```powershell
$cp = (Get-Content target\cp.txt)   # from: mvn dependency:build-classpath -Dmdep.outputFile=target\cp.txt
$jar = "C:\Users\alex\.m2\repository\org\postgresql\postgresql\42.7.11\postgresql-42.7.11.jar"
$url = "jdbc:postgresql://192.168.1.60:5432/inventory?currentSchema=investory"
java -cp "tools;$cp;$jar" ReconRunner "src\main\resources" $url postgres postgres
```

### C0 — completeness
- Scans the source dir for `*.zip` (XTB) and `*.csv` (IBKR).
- Joins each file name to `import_history`.
- Reports: imported?, `status`, `rows_applied`, `rows_failed`.
- FAIL if a source file has no `COMPLETED` row or has `rows_failed > 0`.

### C1 — files → cash_operations (XTB)
- Parses each imported XTB `.xlsx` "Cash Operations" sheet with the same row logic as
  `XtbImportV2Service` (header row containing Type/Time/Amount; rows without a parseable
  Time are footer/total rows and skipped).
- Aggregates expected values per account: row count, `sum(amount)`, per-operation
  count+sum, and `max(date)`.
- Type mapping replicates `CashOperationType.fromString` (type cell, then comment
  fallback), so per-type buckets line up with the DB enum. Notable non-obvious mappings:
  `IKE deposit → DEPOSIT`, `Tax IFTT → TRANSACTION_TAX`, `Free-funds interest tax →
  FREE_FUNDS_INTEREST_TAX` (via comment).
- Compares expected vs live DB per account. PASS when row count matches and
  `|Δ sum(amount)| < 0.01`; per-operation differences are listed for diagnosis.
- Accounts present only in the DB (not produced by any parsed XTB file, e.g. IBKR) are
  reported as `SKIP(non-XTB)` and do not affect pass/fail — IBKR C1 is a follow-up.

### Current result (local DB, all files imported)
- **C0: PASS** — all 6 source files `COMPLETED`, `rows_failed = 0`
  (XTB zips 19151 / 9361 / 3201 / 2090 rows applied; IBKR CSVs 178 / 128).
- **C1: PASS** — all 10 XTB accounts reconcile exactly on rows, `sum(amount)`, and
  `max(date)`; IBKR account 17959259 `SKIP(non-XTB)`.

### Known gaps / next
- **IBKR C1** not implemented yet: the IBKR importer expands one CSV transaction into
  several cash operations (trade + commission + fee), so a raw line-count does not map
  1:1. Add a faithful IBKR expected-model when the IBKR files are imported. Until then
  C0 covers IBKR presence.
- C2–C6 to follow after C0/C1 are green on the full (re-imported) set.


