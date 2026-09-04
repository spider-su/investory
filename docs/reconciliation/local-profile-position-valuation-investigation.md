# Position valuation investigation

Generated: 2026-08-12

Scope: local profile database after the historical position-currency fix.
Materiality rule:
`abs(diff) > max($20, 1% * max(abs(expected), abs(actual)))`.

## Six apparent price-contract overlaps

The previous report counted six material valuation rows whose asset/date also
appeared in a price currency diagnostic. This is not a causal overlap. The
price-quality view reports any stored source row for the asset/date, while
valuation uses the source selected by `app_v_normalized_daily_price`.

| Account | Asset | Date | Valuation source | Selected currency | Price-quality source flagged | Quality currency |
|---|---|---|---|---|---|---|
| Trading USD | BITCOIN | 2025-11-04 | `XTB_TRADE_OPEN` | USD | `INTERPOLATED_XTB` | EUR |
| Trading USD | BITCOIN | 2025-02-28 | `XTB_TRADE_OPEN` | USD | `XTB_TRADE_CLOSE` | EUR |
| IBKR | VHYL.UK | 2025-03-24 | `IBKR_TRADE` | EUR | `STOOQ` | USD |
| IBKR | HPRD.UK | 2025-03-24 | `IBKR_TRADE` | EUR | `STOOQ` | USD |
| IBKR | JGPI.DE | 2025-02-24 | `MANUAL_WEEKLY` | EUR | `INTERPOLATED_XTB` | USD |
| IBKR | JGPI.DE | 2025-03-03 | `MANUAL_WEEKLY` | EUR | `XTB_TRADE_CLOSE` | USD |

The selected rows have explicit currencies and the valuation FX status is
usable (`SAME_CURRENCY` or `ESTIMATED`). The non-selected rows are valid
diagnostic evidence about source disagreement, but they do not provide the
selected valuation input. No row should be relabeled from this join.

## Six-row valuation result

| Asset/date | Expected | Actual | Difference | Selected source |
|---|---:|---:|---:|---|
| BITCOIN 2025-11-04 | 4,243.89 | 4,067.93 | -175.96 | XTB trade open |
| VHYL.UK 2025-03-24 | 433.56 | 521.59 | +88.04 | IBKR trade |
| JGPI.DE 2025-03-03 | 3,160.37 | 3,076.35 | -84.02 | Manual weekly |
| HPRD.UK 2025-03-24 | 565.99 | 527.03 | -38.96 | IBKR trade |
| JGPI.DE 2025-02-24 | 3,130.45 | 3,165.11 | +34.66 | Manual weekly |
| BITCOIN 2025-02-28 | 767.11 | 745.97 | -21.14 | XTB trade open |

These differences are produced by the valuation view comparing the selected
current price with the previous selected price. They are not explained by the
non-selected currency rows.

## Remaining material valuation mismatches

Current count: **307** rows; maximum absolute difference: **$7,518.99**;
total absolute difference: **$39,594.98**.

| Validation code | Rows | Absolute difference total | Meaning in current view |
|---|---:|---:|---|
| `TRADE_OBSERVATION_SELECTED` | 155 | 24,379.17 | selected XTB/IBKR trade observation fallback |
| `ALTERNATE_LISTING_SELECTED` | 119 | 11,753.71 | approved alternate listing selected |
| `OK` | 33 | 3,462.10 | no special validation code despite material prior/current difference |

Largest concentrations are:

| Asset | Selected source/quality | Rows | Absolute difference total | Maximum |
|---|---|---:|---:|---:|
| NFLX.US | XTB trade open | 19 | 8,455.61 | 7,518.99 |
| IUVL.UK | STOOQ verified alternate | 47 | 5,822.57 | 387.35 |
| VWRA.UK | STOOQ verified alternate | 41 | 3,532.17 | 394.68 |
| JGPI.DE | manual weekly | 30 | 3,347.10 | 296.20 |
| DNP.PL | XTB trade open | 1 | 3,431.87 | 3,431.87 |

All material rows have a selected price and no `MISSING_PRICE` valuation code.
The current view intentionally compares current selected valuation with the
previous selected price. On trade-observation and alternate-listing dates this
is a review signal, not proof that the current price is wrong. The 33 `OK` rows
need separate review because their price source is not classified as a warning
even though the difference exceeds the materiality rule.

## Decision

No position valuation or price data was changed. The evidence does not prove a
wrong currency conversion, scale factor, missing price, or bad selected-source
join. Changing the expected-value formula to suppress these rows would weaken
the diagnostic contract and could hide real price jumps.

The earlier six-row overlap should be treated as a report-correlation limitation:
correlation must include the selected price-history identity/source, not only
`asset_id + valuation_date`.

## Next target

Investigate the **position valuation diagnostic contract**, starting with the
largest `TRADE_OBSERVATION_SELECTED` cases (`NFLX.US`, `DNP.PL`) and determine
whether trade-date observations should be excluded from previous-price
continuity checks or compared against a same-day canonical observation. This is
the earliest remaining valuation-layer group and precedes cash-flow/account-day
investigation.
