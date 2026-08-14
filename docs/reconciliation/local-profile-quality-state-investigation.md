# Quality-state investigation

Generated: 2026-08-14

## Root cause

`v_portfolio_data_quality` counted only latest validation rows with status `PASS` as
reconciled. It treated semantic market-value reviews and accounts with no validation snapshot
as unreconciled. With seven latest market-only reviews and four empty accounts, the aggregate
reported `0` reconciled and `11` unreconciled accounts.

## Quality contract

- `RECONCILED`: latest validation is complete and passes.
- `REVIEW`: semantic market/as-of review, warning, or no validation snapshot for an empty
  account. These remain visible and do not make quality `CRITICAL`.
- `UNRECONCILED`: true material reconciliation failure or missing required price, FX,
  multiplier, or residual valuation input.

The seven market-only account/day rows are identified by
`v_account_daily_market_value_semantic_review` and are not accounting failures.

## Account membership after fix

### `REVIEW` — market-value semantic review (7)

IBKR, Trading USD, IKE Alex, IKE Olga, REITs USD, Dividends, Trading USD Metal.

### `REVIEW` — no validation snapshot / empty account (4)

PLN - Empty, Trading EUR, PLN - Empty, EUR - Empty.

### `UNRECONCILED`

None.

### `RECONCILED`

None. All accounts are currently review-state; no account is falsely reported as reconciled.

## Before / after

| Metric | Before | After |
|---|---:|---:|
| Reconciled accounts | 0 | 0 |
| Review accounts | not exposed | 11 |
| Unreconciled accounts | 11 | 0 |
| Quality state | `CRITICAL` | `REVIEW` |
| Open positions priced | 37/37 | 37/37 |
| Missing prices | 0 | 0 |
| Missing FX | 0 | 0 |

## Safety checks

The following remain zero:

- cash unexplained gaps;
- material account/day mismatches;
- monthly mismatches;
- portfolio validation FAIL;
- settlement valuation failures;
- account statistics `VALUE_MISMATCH`;
- position valuation defects.

Raw account/day semantic review rows remain visible. They are not suppressed or removed.
