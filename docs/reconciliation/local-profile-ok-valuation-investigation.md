# Material position-valuation rows classified `OK`

Generated: 2026-08-13

## Scope and baseline

This investigation covers rows from `investory.recon_v_position_valuation_validation`
that satisfy the existing materiality rule:

```text
abs(difference) > max($20, 1% * max(abs(expected), abs(actual)))
```

The local database currently has 33 such rows with `validation_code = OK`:

| Code | Rows | Absolute difference total | Maximum difference |
|---|---:|---:|---:|
| `OK` | 33 | 3,462.10 | 296.20 |

The rows are concentrated in only two assets:

| Asset | Rows | Absolute difference total | Maximum difference | Selected origin |
|---|---:|---:|---:|---|
| `JGPI.DE` | 30 | 3,347.10 | 296.20 | `MANUAL_WEEKLY` |
| `DTLA.UK` | 3 | 115.00 | 52.00 | `MANUAL_WEEKLY` |

No quantity-change pattern explains these rows. For every inspected row the
current and previous quantity are equal. No missing price, missing FX, or
scale-factor issue was found in the selected valuation input.

## Exact data path

```text
asset_price_history
  -> app_v_canonical_asset_daily_price
  -> app_v_normalized_daily_price
  -> app_v_reconstructed_position_daily
  -> recon_v_position_valuation_validation
  -> ReconciliationReport / dashboard diagnostics
```

`app_v_normalized_daily_price` computes `selection_priority` from price metadata.
It recognizes `price_origin = 'MANUAL'`, but the affected rows use
`price_origin = 'MANUAL_WEEKLY'` and `quality_class = 'MANUAL_WEEKLY_CLOSE'`.
They therefore fall through to priority `9` (`unclassified price source`)
instead of the intended manual-price priority.

`recon_v_position_valuation_validation` then constructs the previous-price expected
value when the dates are consecutive and the source is not one of the already
classified cases. Its effective formula is:

```text
expected = current quantity
         * previous selected price
         * contract multiplier
         * current-date FX rate

actual   = reconstructed current market value in base currency
```

`validation_code = OK` is the final fall-through branch. It does **not** mean
that the value was independently proven correct. It means that no explicit
missing-input, corporate-action, trade-observation, alternate-listing, or
configured price-ratio branch matched.

## Population classification

| Pattern | Rows | Absolute difference total | Interpretation |
|---|---:|---:|---|
| `MANUAL_WEEKLY` after `STALE_CARRY_FORWARD` | 26 | 3,007.76 | Weekly observed refresh replacing a carried-forward price; continuity review |
| `MANUAL_WEEKLY` after `MANUAL_WEEKLY` | 3 | 126.66 | Ordinary weekly observation movement; continuity review |
| `MANUAL_WEEKLY` after `IBKR_TRADE` | 2 | 220.65 | Source transition; continuity review and source comparison |
| **Total** | **33** | **3,462.10** | |

Representative largest row:

```text
JGPI.DE, 2026-03-16
quantity:          359 (previously 359)
previous price:     23.60 EUR, 2026-03-15
selected price:     22.89 EUR, 2026-03-16
FX:                  1.162078625, ESTIMATED
expected value:      9,845.59 USD
actual value:        9,549.39 USD
difference:           -296.20 USD (-3.01%)
```

The largest `DTLA.UK` row is:

```text
DTLA.UK, 2026-05-11
quantity:          400 (previously 400)
previous price:      4.61 USD, 2026-05-10
selected price:      4.48 USD, 2026-05-11
FX:                  1, SAME_CURRENCY
expected value:       1,844.00 USD
actual value:         1,792.00 USD
difference:             -52.00 USD (-2.82%)
```

## Same-day evidence

The local data contains same-day secondary rows for two `JGPI.DE` dates:

| Date | Selected manual value | Same-day secondary value | Assessment |
|---|---:|---:|---|
| 2025-02-24 | 26.48 EUR (converted at 1.039377...) | 26.22815899 USD interpolated XTB | Different source/provenance; not a definitive oracle |
| 2025-03-03 | 25.63 EUR (converted at 1.043732...) | 26.65 USD XTB trade observation | Approximately consistent after conversion |

The remaining 31 rows have no same-day independent price row in the local
price source. The secondary rows are either interpolated or trade observations,
not an authoritative same-listing market-close oracle. They cannot prove that
the selected manual weekly price is wrong.

## Root cause classification

The evidence supports a **diagnostic classification defect**, not a proven
production valuation defect:

1. `OK` is a default/fall-through code despite a material previous/current
   valuation movement.
2. `MANUAL_WEEKLY` is a known observed source class, but its actual
   `MANUAL_WEEKLY` origin is not recognized by the priority expression that
   recognizes only exact `MANUAL`.
3. The movements are continuity checks across weekly observations and stale
   carry-forward/source transitions. They are not independently verified
   valuation failures.
4. Quantity, selected price, FX, and reconstruction data do not show a proven
   production error in this population.

Recommended semantic classification:

```text
MANUAL_WEEKLY_SELECTED / MANUAL_WEEKLY_CONTINUITY_REVIEW
```

It should remain visible as a review signal. It should not be counted as an
unexplained material valuation failure solely because the current weekly
observation differs from the previous selected price.

## Change decision

No production valuation, imported fact, price row, quantity, FX value, or
materiality threshold was changed in this investigation. No reconciliation
SQL was changed yet because the precise policy choice between retaining the
previous-price comparison as a review value and making the expected value
null for manual-weekly observations should be implemented with a focused
regression fixture in the next patch.

The smallest justified next patch is diagnostic-only:

* recognize `MANUAL_WEEKLY` as a manual observed source in
  `app_v_normalized_daily_price`;
* emit an explicit manual-weekly review code in
  `recon_v_position_valuation_validation`;
* preserve selected production price and reconstructed market value;
* keep the previous-price movement visible as review evidence.

## Current reconciliation impact

The current local baseline remains:

| Metric | Current |
|---|---:|
| Position valuation material mismatches | 177 |
| `TRADE_OBSERVATION_SELECTED` material | 144 |
| `ALTERNATE_LISTING_SELECTED` material | 0 |
| Alternate-listing review rows | 118 |
| `OK` material rows | 33 |
| Cash-flow material gaps | 282 |
| Account/day material mismatches | 78 |
| Portfolio validation `FAIL` | 222 |
| Settlement failures | 159 in the supplied baseline report |

No before/after delta exists because this pass made no code or database view
change. The local diagnostic queries reproduced the supplied 33-row population
and current downstream counts; no refresh was necessary for an investigation
without a patch.

## Conclusion and next target

The 33 rows are not 33 unrelated unexplained defects. They are 33 manual
weekly continuity/source-transition review cases hidden behind the `OK`
fall-through code. The position-valuation layer is not yet ready to close,
because the manual-weekly diagnostic classification should be made explicit
and regression-tested first.

Next target before moving to cash-flow: implement and test the narrow
`MANUAL_WEEKLY_CONTINUITY_REVIEW` diagnostic classification, then rerun the
full reconciliation. Do not change production valuation.
