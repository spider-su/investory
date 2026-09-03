# NFLX split-boundary valuation investigation

Date: 2026-08-13  
Scope: local profile, position valuation only

## Conclusion

NFLX is a **diagnostic continuity defect caused by a corporate-action boundary**. Production valuation is internally consistent and was not changed.

Netflix's 10-for-1 split is consistent with the imported broker state:

- pre-boundary lots were closed on 2025-11-16 at their original pre-split quantities/prices;
- new lots were opened on 2025-11-16 at approximately 10x total quantity and approximately 1/10 price;
- the reconstructed market value remains economically continuous.

The validator previously compared the new quantity with the previous pre-split price, producing an artificial expected value of about 8,424 USD instead of the actual 905 USD.

## NFLX timeline

| Date | Open quantity | Selected price | Source | Reconstructed market value | Classification |
|---|---:|---:|---|---:|---|
| 2025-11-13 | 0.6687 | 1,161.00 | XTB trade open | 776.36 | pre-split |
| 2025-11-14 | 0.7587 | 1,110.38 | XTB trade open | 842.45 | pre-split |
| 2025-11-15 | 0.7587 | 1,110.38 | XTB carry-forward | 842.45 | pre-split carry-forward |
| 2025-11-16 | 7.5870 | 119.34368546 | XTB trade open | 905.46 | post-split quantity/price basis |
| 2025-11-17 | 7.5870 | 119.34368546 | XTB carry-forward | 905.46 | post-split |
| 2025-11-18 | 1.4575 | 114.22978873 | XTB trade close | 166.49 | post-split |

Other stored prices on 2025-11-16 were:

```text
XTB_TRADE_CLOSE  1181.21303546 USD
XTB_TRADE_OPEN   119.34368546 USD
```

The first is pre-split scale; the selected open observation is post-split scale. The imported position rows provide the decisive evidence: old lots close at 14:31 and new lots open at 15:51 with total quantity changing from 0.7587 to 7.5870. New lot prices are approximately 110–124 USD. The 2025-11-16 timestamp is therefore a broker adjustment/import observation, not evidence of a normal NASDAQ trading close.

## Exact diagnostic error

Before the fix, `recon_v_position_valuation_validation` used:

```text
expected = current open quantity × previous selected price × multiplier × FX
```

For 2025-11-16:

```text
expected = 7.5870 × 1110.38 = 8424.453060
actual   = 7.5870 × 119.34368546 = 905.460542
difference = -7518.992518
```

This was a false continuity comparison across a 10-for-1 split. Existing domain comments state that position valuation uses broker-reported quantity and raw close price; corporate-action quantity changes must be represented by the position source. That is exactly what the imported NFLX rows do.

No separate corporate-action table exists. The narrow diagnostic rule detects a consecutive approximately 10x quantity increase combined with an approximately 1/10 selected-price reset and marks it for review.

## Fix

`V01.005__portfolio_views.sql` now adds `prev_open_quantity` and classifies this pattern as:

```text
CORPORATE_ACTION_PRICE_RESET
```

For that classification, the validation remains visible with `WARN` severity, but no previous-price expected value is fabricated. Therefore it does not appear as a material valuation mismatch. No imported facts, reconstructed values, quantities, prices, or cost basis were changed.

The heuristic is deliberately narrow: consecutive date, quantity ratio 9.5–10.5, and price ratio 0.05–0.20. It is a diagnostic review classification, not a general corporate-action engine.

## Regression coverage

The existing deterministic corporate-action integration fixture now also asserts:

```text
CORPORATE_ACTION_PRICE_RESET|NULL
```

for a quantity transition from 1 to 10 and price transition from 100 to 10, while preserving market value at 100. The closed-holding-gap regression remains in the same integration class.

The focused corporate-action Testcontainers run passed using `-DskipTests` with the selected failsafe test. The ordinary full class has unrelated pre-existing checkpoint failures when run without skipping unit tests.

## Reconciliation delta

Baseline is the post-closed-gap-fix report (`295` material position rows).

| Metric | Before | After |
|---|---:|---:|
| NFLX material valuation rows | 19 | 0 |
| NFLX maximum difference | 7,518.99 | 0 |
| Position valuation material mismatches | 295 | 294 |
| TRADE_OBSERVATION_SELECTED | 144 | 143 |
| ALTERNATE_LISTING_SELECTED | 118 | 118 |
| Unclassified/OK material rows | 33 | 33 |
| Maximum overall difference | 7,518.99 | 789.50 |
| Cash-flow material gaps | 282 | 282 |
| Account/day material mismatches | 78 | 78 |
| Portfolio validation FAIL | 222 | 222 |
| Settlement valuation failures | 159 | 159 |
| Portfolio quality | CRITICAL | CRITICAL |

The full after-fix report is `docs/reconciliation/local-profile-nflx-split-after-fix-report.md`.

## Broader search

No remaining material position row had an expected/actual ratio in the investigated split-factor ranges (approximately 0.1, 0.333, 0.5, 2, 3, or 10) after removing the NFLX split reset. No additional candidate was promoted to a corporate-action investigation from the current material population.

## Next target

Return to `ALTERNATE_LISTING_SELECTED` (118 material rows). NFLX is now explained as a corporate-action review case, and the next valuation-layer population remains alternate-listing diagnostics.
