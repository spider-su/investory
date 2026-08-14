# Trade-observation position valuation investigation

Date: 2026-08-13  
Database: local profile, refreshed after the validation-view change

## Result

The investigated population is mixed:

- **DNP.PL** was a diagnostic contract defect. The validation query used `LAG(selected_price)` partitioned only by asset and bridged a closed holding gap. It compared the 2026-01-05 trade observation (41.03 PLN) with the previous selected observation from 2025-05-30 (546.80 PLN), although no position was held between those dates.
- **NFLX.US** was subsequently explained as a 10-for-1 corporate-action boundary. The broker quantity changed from 0.7587 to 7.5870 while the selected price changed from 1110.38 USD to 119.34368546 USD. See `local-profile-nflx-split-investigation.md`.

## Data path and formula

The path is:

```text
asset_price_history
  -> v_normalized_daily_price
  -> v_reconstructed_position_daily
  -> v_position_valuation_validation
  -> reconciliation aliases/report
```

`v_reconstructed_position_daily` is the production valuation source. The validation view reports the selected price, quantity, multiplier, and FX-normalized actual value. Its prior-price continuity check previously used:

```sql
LAG(selected_price) OVER (PARTITION BY asset_id ORDER BY valuation_date)
```

and compared the current selected price with that prior value. The expected value uses the open quantity multiplied by the prior selected price, contract multiplier, and usable FX conversion. Therefore a long inactive interval could create a false material comparison against an unrelated historical holding.

## Narrow fix

`V01.005__portfolio_views.sql` now also carries `prev_valuation_date` and applies prior-price continuity checks only when:

```text
prev_valuation_date = valuation_date - 1 day
```

If the prior valuation is not the immediately preceding calendar day, the validation does not invent an expected value from that stale bridge. Trade observations remain visible through `TRADE_OBSERVATION_SELECTED`; they are not discarded or silently marked correct. Production reconstructed values and imported facts are unchanged.

## Population after the fix

Current material trade-observation rows:

| Measure | Result |
|---|---:|
| Trade-observation rows | 144 |
| With same-day independent non-trade price | 66 |
| Without same-day independent oracle | 78 |
| Within 5% of same-day oracle | 47 |
| Beyond 5% of same-day oracle | 19 |

Current selected-to-previous price-change buckets for these rows are 71 (1–5%), 48 (5–10%), 18 (10–20%), 4 (20–50%), and 1 (>50%). The >50% case is NFLX.US.

## Before / after reconciliation

The before values are from the prior local position-valuation report; the after values are from `local-profile-trade-observation-after-fix-report.md`. The unchanged rows are downstream or unrelated to this diagnostic correction.

| Metric | Before | After | Delta |
|---|---:|---:|---:|
| Position valuation material mismatches | 307 | 295 | -12 |
| Maximum valuation difference | 7518.99 | 7518.99 | 0 |
| TRADE_OBSERVATION_SELECTED | 155 | 144 | -11 |
| ALTERNATE_LISTING_SELECTED | 119 | 118 | -1 |
| Unclassified/OK material rows | 33 | 33 | 0 |
| Cash-flow material gaps | 282 | 282 | 0 |
| Account/day material mismatches | 78 | 78 | 0 |
| Monthly material mismatches | 39 | 39 | 0 |
| Portfolio validation FAIL | 222 | 222 | 0 |
| Settlement valuation failures | 159 | 159 | 0 |
| Portfolio quality | CRITICAL | CRITICAL | unchanged |

The DNP.PL material row disappeared from the valuation mismatch population. The maximum remains NFLX.US, so the correction did not hide the largest remaining review case.

## Classification

| Population | Classification |
|---|---|
| DNP.PL closed-gap row | Diagnostic contract defect fixed |
| NFLX.US trade-observation rows | Validity/review signal; production defect not proven |
| Remaining trade observations | Review population; same-day evidence should be considered where available |
| Alternate listings | Not investigated in this pass |
| Confirmed production valuation errors | None proven by this investigation |
| Unexplained anomalies | No confirmed source error; NFLX remains the strongest review case |

## Tests

`SchemaMigrationCheckpoint2IT.positionValuationDoesNotBridgeAClosedHoldingGap` creates a disposable inactive interval followed by a new trade observation and asserts that the validation view reports `TRADE_OBSERVATION_SELECTED|NULL`. The production selected price and reconstructed valuation remain untouched.

The isolated method passed with PostgreSQL/Testcontainers. The full `SchemaMigrationCheckpoint2IT` class still has unrelated pre-existing checkpoint failures (FX expectation, scale text formatting, corporate-action fixture, and VHYL mapping) and is not a clean signal for this change.

## Next target

Investigate **`ALTERNATE_LISTING_SELECTED`** next. It is the earliest remaining valuation population after trade observations have a defensible review contract and currently contains 118 rows. This task intentionally did not inspect or change it.
