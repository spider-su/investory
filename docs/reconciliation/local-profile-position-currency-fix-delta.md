# Position currency validation fix — before/after

Database: local profile database (`inventory`, schema `investory`).  The previous report was
preserved as `local-profile-mv-report.md`; the rerun is
`local-profile-mv-report-after-position-currency-fix.md`.

Materiality rule unchanged:

```text
abs(diff) > max($20, 1% * max(abs(expected), abs(actual)))
```

| Metric | Before | After | Delta |
|---|---:|---:|---:|
| `MISSING_ASSET_CURRENCY` | 518 | 0 | -518 |
| Position reconstruction `PASS` | 37,226 | 37,226 | 0 |
| Position reconstruction `WARN` | 4,784 | 4,784 | 0 |
| Position valuation material mismatches | 307 | 307 | 0 |
| Maximum position valuation difference | 7,518.99 | 7,518.99 | 0 |
| Cash-flow material gaps | 282 | 282 | 0 |
| Maximum cash-flow gap | 10,624 | 10,624 | 0 |
| Account/day `FAIL` | 222 | 222 | 0 |
| Account/day `WARN` | 1,855 | 1,855 | 0 |
| Account/day material mismatches | 78 | 78 | 0 |
| Account statistics `VALUE_MISMATCH` | 6 | 5 | -1* |
| Monthly profit material mismatches | 39 | 39 | 0 |
| Portfolio validation `FAIL` | 222 | 222 | 0 |
| Trade settlement `VALUATION_RECONSTRUCTION_FAILED` | 159 | 159 | 0 |
| Portfolio `quality_state` | CRITICAL | CRITICAL | unchanged |

\* This unrelated account-statistics count changed during the rerun window; the corrected view
does not feed that object. It is not attributed to this fix.

## Root cause

`V01.006__reconciliation_views.sql` defines `recon_v_position_currency_validation` with:

```sql
LEFT JOIN investory.assets asset
  ON asset.id = p.asset_id
 AND asset.exclude_from_import = false
```

The same historical-validation boundary was applied in `mixed_groups`. This made valid asset
metadata disappear for positions referencing inactive or import-excluded historical assets.
The local diagnostic proved all 518 affected rows had a non-null `assets.currency`; all 518 were
both inactive and excluded from import.

The fix removes only those filters. Historical validation now resolves:

```text
positions.asset_id -> assets.id -> assets.currency
```

without changing asset data, import behavior, valuation, or accounting facts.

## Propagation result

The defect was diagnostic-only in the measured local database. It did not explain position
valuation, cash-flow, account/day, monthly, portfolio-validation, or settlement failures.

## Next target

**Price / currency / scale contract — specifically `PRICE_CURRENCY_MISMATCH` and
`PRICE_SCALE_MAPPING_MISMATCH`.** These are the earliest remaining broken-layer signals:

- 2,633 price-currency mismatch rows;
- 2,038 price-scale mapping mismatch rows;
- 307 material position valuation mismatches remain.

Investigate whether the price contract or scale mapping is the upstream cause before examining
cash-flow or account/day reconciliation.
