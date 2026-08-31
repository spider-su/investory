# Alternate-listing position valuation investigation

Date: 2026-08-13  
Baseline: 118 material `ALTERNATE_LISTING_SELECTED` rows, after the closed-gap and NFLX split fixes

## Conclusion

The 118 rows are **approved alternate-listing review cases**, not proven production valuation errors.

All material rows use reviewed `STOOQ` mappings with `VERIFIED_ALTERNATE_LISTING` quality. The mappings have scale factor `1`, and the selected valuation rows use a usable same-currency conversion. There is no independent same-day primary listing price for these assets in the local price source, so the previous selected price is not a valid economic oracle. The prior validator was comparing:

```text
current approved alternate price × current quantity
vs
previous selected price × current quantity
```

That is a continuity review, not an authoritative valuation comparison.

## Representative cases

### VWRA.UK — largest absolute difference

Mapping:

```text
canonical asset:       VWRA.UK
source:                STOOQ / vwra.uk
mapping:               US -> UK alternate listing
price currency:        USD
scale factor:          1
FX status:             SAME_CURRENCY
```

On 2026-04-01:

```text
quantity:               69
current alternate:      168.14 USD
previous selected:      162.42 USD   (implied by expected value)
actual value:        11,601.66 USD
previous-price value: 11,206.98 USD
difference:             394.68 USD / 3.52%
```

The selected source row is an approved alternate listing. No same-day primary listing oracle exists locally. The movement is plausible for an equity ETF and does not prove a wrong selected price.

### IUVL.UK — largest concentration

Mapping:

```text
canonical asset:       IUVL.UK
source:                STOOQ / iuvl.uk
mapping:               US -> UK alternate listing
price currency:        USD
scale factor:          1
FX status:             SAME_CURRENCY
```

On 2026-06-25:

```text
quantity:               610
current alternate:       19.475 USD
previous selected:       18.840 USD   (implied by expected value)
actual value:        11,879.75 USD
previous-price value: 11,492.40 USD
difference:             387.35 USD / 3.37%
```

IUVL contributes 47 material rows and USD 5,822.57 of absolute difference. The current selected price is cleanly normalized; the difference is a market-movement/continuity signal.

### VHYL.UK — largest relative movement

On 2025-02-14:

```text
quantity:                15
current alternate:       56.095 USD
actual value:            841.43 USD
previous-price value:  1,008.38 USD
difference:            -166.95 USD / -16.56%
```

This is also an approved `STOOQ / vhyl.uk` alternate listing with USD and scale `1`. There is no same-day primary oracle. It remains a review case, not a confirmed mapping defect.

## Population classification

| Asset | Rows | Absolute difference total | Maximum difference |
|---|---:|---:|---:|
| IUVL.UK | 47 | 5,822.57 | 387.35 |
| VWRA.UK | 37 | 3,486.21 | 458.16 |
| JEPG.UK | 26 | 1,714.98 | 215.45 |
| VHYL.UK | 3 | 267.91 | 166.95 |
| HPRD.UK | 4 | 140.00 | 55.25 |
| XDWL.DE | 1 | 79.43 | 79.43 |
| **Total** | **118** | **11,511.10** | **458.16** |

All 118 rows have no independent same-day primary price in the local selected-price source. Their mappings are approved alternate listings. The selected rows inspected have no demonstrated selected-row FX or scale defect. The raw historical diagnostic totals for price-currency and scale metadata remain separate diagnostics and were not joined by asset/date alone.

The differences are continuity movements against the previous selected observation. They are not independent primary-vs-alternate normalized comparisons. Timing differences between the original and matched exchanges are therefore possible and cannot be resolved from the local data alone.

## Narrow fix

`v_position_valuation_validation` now treats `selection_priority = 3` as an approved alternate-listing review path:

- `ALTERNATE_LISTING_SELECTED` remains visible;
- selected price, quantity, FX status, and reconstruction failures remain validated;
- no previous-price expected value is fabricated for the alternate listing;
- the row remains reviewable but no longer becomes a material valuation mismatch solely because the alternate price moved from the previous observation.

No production valuation, mapping, price-history row, imported fact, or materiality threshold changed.

## Regression coverage

Added a PostgreSQL/Testcontainers fixture with an approved same-currency alternate listing whose price changes from 200 to 250. It asserts:

```text
selected production price remains 250
validation code remains ALTERNATE_LISTING_SELECTED
expected value is NULL
```

The existing closed-holding-gap and corporate-action tests remain in place. The focused alternate-listing integration test passed.

## Reconciliation delta

| Metric | Before | After |
|---|---:|---:|
| Position valuation material mismatches | 294 | 177 |
| Maximum valuation difference | 789.50 | 789.50 |
| ALTERNATE_LISTING_SELECTED material rows | 118 | 0 |
| Alternate-listing review rows | 118 | 118 |
| TRADE_OBSERVATION_SELECTED material rows | 143 | 144* |
| Unclassified/OK material rows | 33 | 33 |
| Cash-flow material gaps | 282 | 282 |
| Account/day material mismatches | 78 | 78 |
| Portfolio validation FAIL | 222 | 222 |
| Settlement valuation failures | 159 | 159 |
| Portfolio quality | CRITICAL | CRITICAL |

\* The local rerun exposed one additional trade-observation material row compared with the earlier snapshot; it is unrelated to the alternate-listing change. The current material population is 144 trade rows plus 33 OK rows. No downstream calculation changed.

## Defect classification

| Class | Result |
|---|---|
| Valid approved alternate listing | 118 review rows |
| No same-day primary oracle | 118 rows |
| Timing/session review | Possible; not independently resolvable locally |
| Demonstrably wrong scale | 0 |
| Demonstrably wrong currency/FX | 0 |
| Actual selected-price defect | 0 proven |
| Unresolved alternate valuation errors | 0 proven |

## Next target

Investigate the **33 material rows currently classified `OK`**. Alternate-listing rows now have explicit review semantics and no proven production error remains in that group.
