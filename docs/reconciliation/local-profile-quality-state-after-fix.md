# Quality-state after-fix report

Generated: 2026-08-14

## Change

Applied the quality-state review semantics from
`V01.022__quality_state_review_semantics.sql`.

The change adds per-account quality states and separates semantic reviews from true
unreconciled failures. It does not alter accounting, valuation, imported facts, FX, price
selection, materiality thresholds, or raw reconciliation status.

## Result

| Metric | Before | After |
|---|---:|---:|
| Reconciled accounts | 0 | 0 |
| Review accounts | not exposed | 11 |
| Unreconciled accounts | 11 | 0 |
| Quality state | `CRITICAL` | `REVIEW` |
| Open positions priced | 37/37 | 37/37 |
| Missing prices | 0 | 0 |
| Missing FX | 0 | 0 |

## State membership

| State | Accounts | Reason |
|---|---:|---|
| `REVIEW` | 7 | `MARKET_VALUE_SEMANTIC_REVIEW` |
| `REVIEW` | 4 | `NO_VALIDATION_SNAPSHOT` / empty accounts |
| `UNRECONCILED` | 0 | none |
| `RECONCILED` | 0 | none |

## Production impact

- Accounting formulas changed: **no**
- Imported facts changed: **no**
- Price selection changed: **no**
- FX logic changed: **no**
- Materiality thresholds changed: **no**
- Quality aggregation changed: **yes**

## Regression safety

All core reconciliation defect counts remain zero: cash, material account/day, monthly,
portfolio validation FAIL, settlement valuation, account-statistics VALUE_MISMATCH, and
position valuation defects.

Semantic review populations remain visible through their diagnostic views.
