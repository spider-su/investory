# Settlement valuation reconstruction after-fix report

Generated: 2026-08-14

## Change

Applied the pre-squash settlement-valuation patch. The current equivalent is in
`V01.006__reconciliation_views.sql`; the settlement diagnostic now uses
the canonical `exclude_from_import = false` asset scope when building closed lots. Production
cash, position valuation, settlement formulas, imported facts, FX rules, and materiality were
not changed.

## Settlement delta

| Metric | Before | After |
|---|---:|---:|
| `VALUATION_RECONSTRUCTION_FAILED` | 159 | 0 |
| Previous value NULL rows | 156 | 0 |
| Current value zero rows | 3 | 0 |
| Explicit settlement review rows | 0 | 0 |
| True incomplete settlement rows | not proven | 0 |
| Confirmed settlement-accounting defects | 0 | 0 |
| Unexplained valuation-availability rows | 0 proven | 0 |

The 159 rows were outside canonical reconciliation scope. The three opening-boundary rows also
disappeared with the same excluded-asset scope correction; they were not converted to PASS by
changing valuation semantics.

## Other reconciliation results

| Metric | Result |
|---|---:|
| Cash unexplained gaps | 0 |
| Account/day material mismatches | 0 |
| Monthly material mismatches | 0 |
| Portfolio validation FAIL | 0 |
| Account statistics `VALUE_MISMATCH` | 0 |
| Position valuation material defects | 0 |

Raw account/day status counts remain `PASS 3800`, `WARN 2062`, `FAIL 7`; those FAIL rows are
not the material mismatch population.

## Quality state

The refreshed quality view reports `CRITICAL`, with 0 reconciled and 11 unreconciled accounts.
All 11 account-statistics rows are `VALUATION_ASOF_DIFFERENCE`: the current date is
2026-08-14 and the latest imported daily snapshot is 2026-08-13. This is an as-of boundary
diagnostic, not evidence of new accounting defects. Price coverage is complete: 37 of 37 open
positions are priced, with zero missing price and FX counts.

## Verification

- The pre-squash Flyway settlement patch applied successfully.
- Dependency-aware reporting stages completed.
- All 29 open assets have raw and canonical price dates through 2026-08-13.
- No Yahoo backfill rows were needed or inserted.

## Remaining work

Investigate the seven raw account/day FAIL rows and decide whether the current-date
`VALUATION_ASOF_DIFFERENCE` quality classification should be reported separately from true
unreconciled-account defects. No settlement valuation failure remains.
