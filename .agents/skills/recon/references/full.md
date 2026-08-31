# Full reconciliation

Run all currently implemented Investory reconciliation mechanisms that can reasonably
be executed against the current data.

Use the repository's current reconciliation contract as authoritative.

Include implemented checks across the pipeline, such as:

- source files / import history
- cash ledger
- positions
- prices
- FX
- `account_daily`
- account totals
- portfolio/reporting totals
- independent reconciliation views
- persisted audit/reconciliation data
- economic-truth checks

Do not create fictional checks merely to fill this list.

For every failed reconciliation:

1. identify the affected account, symbol/asset, date, and currency when available
2. show expected value
3. show actual value
4. show residual
5. trace relevant source/import/database records
6. determine the most likely root cause

Classify causes as one of:

- `DATA_IMPORT`
- `FX`
- `PRICE_HISTORY`
- `CASH_ACCOUNTING`
- `POSITION_ACCOUNTING`
- `PROJECTION`
- `ROUNDING`
- `STALE_DATA`
- `RECON_IMPLEMENTATION`
- `EXPECTED_DOMAIN_BEHAVIOR`
- `UNKNOWN`

Do not classify a difference as rounding without checking Investory's configured
precision/tolerance.

Pay particular attention to:

- execution FX versus neutral valuation FX
- `STALE` / `MISSING_RATE`
- estimated FX provenance
- stale carry-forward prices
- external versus internal flows
- result-only instruments
- unexplained profit
- duplicate or orphaned records

Do not implement fixes.

## Output

# Full Reconciliation

Status: `PASS | WARN | FAIL | NOT VERIFIED`

## Summary

| Check | Status | Residual | Note |
|---|---|---:|---|

## Failures

For each failure report:

- Account
- Asset/Symbol
- Date
- Currency
- Expected
- Actual
- Residual
- Cause
- Evidence
- Recommended fix

## Warnings

Report only material warnings.

## Confidence

`HIGH | MEDIUM | LOW`

State what was actually verified and what could not be verified.

## Recommendation

Output exactly one:

- `SAFE FOR GOLDEN`
- `FIX RECONCILIATION FAILURES`
- `INVESTIGATE WARNINGS`
- `INSUFFICIENT DATA`
