# Monthly reconciliation after flow-scope fix

Generated: 2026-08-13 from the local database after the pre-squash migration `01.012`.

## Contract implemented

Canonical monthly profit remains:

```text
SUM(account_daily.daily_profit_amount)
```

The account-level boundary now uses normalized non-performance account flows:

```text
flows = normalized_cash_operations
         WHERE is_external_flow OR is_internal_transfer

expected boundary profit =
    closing equity
  - opening equity
  - account-flow inflows
  + account-flow outflows
```

Internal transfers are included as account balance movements, so they do not
become investment profit. At portfolio scope paired internal transfers remain
neutral. Trade cash, fees, interest, and realized results remain outside the
boundary flow input and remain part of the investment-result accounting.

## Before / after

| Metric | Before | After |
|---|---:|---:|
| Account/day material mismatches | 78 (stale pre-RESULT_ONLY view) | 78 (refresh prerequisite still stale) |
| Maximum account/day difference | 6,713 | 6,713 |
| Monthly material mismatches | 39 | 0 |
| Maximum monthly gap | 10,323 | 0 |
| Total absolute monthly gap | 109,846 | 0 |
| Cash-flow unexplained gaps | 0 | 0 |
| Cash internal-transfer review rows | 282 | 282 |
| Portfolio validation FAIL | 222 | 222 (downstream view not reclassified) |
| Portfolio quality | CRITICAL | CRITICAL |

The monthly view contains rounded display values, so representative corrected
rows show zero displayed difference:

| Account/month | Canonical | Corrected boundary | Difference |
|---|---:|---:|---:|
| `51499241 / 2025-01` | 618.15 | 618.00 | 0.00 |
| `51499241 / 2025-02` | -1,075.12 | -1,075.00 | 0.00 |
| `51822121 / 2025-02` | -135.41 | -135.00 | 0.00 |
| `51822121 / 2025-04` | 504.98 | 505.00 | 0.00 |
| `51993106 / 2025-04` | -166.32 | -166.00 | 0.00 |

## Refresh prerequisite status

The pre-squash database had applied `01.012` successfully. The normal reporting refresh completed.
The account/day materialized view still has the old local definition: its
`pg_get_viewdef` uses `open_quantity` for result-only valuation. The existing
`JdbcRebuildResultOnly` utility was attempted but stopped because dependent
views (`recon_v_reporting_validation_summary`, portfolio quality views, and related
position diagnostics) require a dependency-aware rebuild.

Therefore the account/day and downstream counts above are explicitly **not**
final post-RESULT_ONLY values. No imported facts were changed.

## Tests and verification

- Flyway validation passed for all 12 migrations.
- Pre-squash migration `01.012` was applied successfully.
- Monthly material query returned `0` rows after refresh.
- Added a database contract test asserting the monthly reconciliation view
  reads `normalized_cash_operations` and `is_external_flow`/internal-flow
  scope rather than treating account-daily flow fields as the canonical
  boundary.
- `JdbcRebuildResultOnly` was not changed; its dependency failure is recorded
  above.

## Conclusion

The monthly reconciliation layer is closed: no unexplained material monthly
P/L mismatches remain under the unchanged `$20 / 1%` rule. The next task is to
complete the dependency-aware RESULT_ONLY account/day refresh, then rerun the
full report and investigate the residual portfolio validation failures using
the actual refreshed counts.
