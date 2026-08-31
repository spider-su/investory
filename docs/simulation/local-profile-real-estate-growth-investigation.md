# Local profile real-estate growth investigation

Date: 2026-08-13. Scope: local profile `1`, saved simulation plan `1`.

## Symptom

The live simulation rendered real estate as USD `1,768,203.07` in 2026, then
`3,349,482.30`, `6,507,367.65`, and `12,818,348.44`. This was genuine
compounding, not a display or liquidity error.

## Proven cause

The active database rows for assets `1`, `2`, `4`, and `5` contained
`expected_annual_growth_rate = 1.000000000000`, which is a decimal **100%**
rate. `RetirementSimulationService.activeRate` selects those explicit periods
before the scenario setting, and applies `start * (1 + rate)`. Each of those
four assets therefore doubled each year.

Asset `3` had no active valuation period. It correctly fell back to the saved
plan's `real_estate_return_rate = 0.025000000000` (2.5%).

The four `1.0` rows were repaired in the local DB to `0.01`, after matching
their effect exactly to the rendered live output. Asset `3` was not changed.

## Projection path and 2026 rate table

`long_term_asset_valuation_periods` -> `LongTermAssetProjectionQueryService.projectionInputs`
copies the stored growth rate into `LongTermAssetProjectionInput.Period`.
`ProfileQueryService` copies `annualReturnRate` unchanged into
`ProjectedLongTermAsset.Period`. `RetirementSimulationService.activeRate`
selects the applicable explicit rate; only a missing explicit rate uses the
scenario fallback.

Live PLN-to-USD conversion on the run date: `0.26720106880427521710`.

| Asset | Name | PLN value | Explicit rate before repair | Simulator rate after repair | Source | 2026 USD end after repair |
|---:|---|---:|---:|---:|---|---:|
| 1 | Reduta 26B/44 | 710,000 | 1.0 | 0.01 | explicit | 191,609.89 |
| 2 | Reduta 46/46 | 710,000 | 1.0 | 0.01 | explicit | 191,609.89 |
| 3 | Reduta 42D/36 | 700,000 | none | 0.025 | scenario fallback | 191,716.77 |
| 4 | Lublanska 13/134 | 780,000 | 1.0 | 0.01 | explicit | 210,501.00 |
| 5 | Lublanska 13/168 | 750,000 | 1.0 | 0.01 | explicit | 202,404.81 |

All explicit periods are open-ended. Therefore the effective per-asset rates
in 2026, 2027, and 2028 are unchanged: `0.01`, `0.01`, fallback `0.025`,
`0.01`, and `0.01`, in asset-ID order.

Before repair, the four explicit assets contributed
`(710 + 710 + 780 + 750) * 0.2672010688 * 2 = 1,576,486.30 USD`; asset 3
contributed `700 * 0.2672010688 * 1.025 = 191,716.77 USD`. Their sum is
`1,768,203.07 USD` before normal simulation rounding and exactly explains the
rendered `1,768,203.07 USD` block. Repeating the four 100% rates explains the
next-year doubling.

## Saved plan and FX findings

Plan `1` stores valid decimal rates: inflation `0.025`, cash `0.02`, fixed
income `0.04`, equity `0.06`, real estate `0.025`, other `0.03`, capital-gain
tax `0.19`. No legacy-scaled saved-plan rate was found.

Return rates and tax rates are dimensionless. Current `ProfileQueryService`
does **not** FX-convert `annualReturnRate`; it converts only monetary values
(current value, income, expense, redemption value, and tax base). With the
observed PLN-to-USD FX rate, converting stored `0.01` would incorrectly create
`0.002672...`; this does not happen in current code.

## Live rerun after repair

| Year | Real estate | Locked contractual | Spendable liquid | Passive income | Withdrawal | Net worth | Status |
|---:|---:|---:|---:|---:|---:|---:|---|
| 2026 | 987,842.35 | 250,869.74 | 31,736.47 | 42,770.77 | 137,229.23 | 1,270,448.56 | OK |
| 2027 | 1,000,596.53 | 261,711.82 | 0.00 | 35,652.54 | 148,847.46 | 1,262,308.34 | Failed |
| 2028 | 1,013,550.14 | 180,602.27 | 0.00 | 35,652.54 | 153,459.96 | 1,194,152.41 | Failed |
| 2029 | 1,026,706.97 | 93,227.50 | 0.00 | 35,652.54 | 158,187.78 | 1,119,934.47 | Failed |
| 2030 | 1,040,070.91 | 0.00 | 0.00 | 35,652.54 | 163,033.78 | 1,040,070.91 | Failed |

The remaining failure is spendable-liquidity policy: contractual assets and
real estate are not automatically sold/redeemed before maturity. Accounting
data and that liquidity policy were not changed.

## Safeguards and verification

UI percentage fields convert percentage points to internal decimal rates.
Property-growth values are now validated to the technical range `[-1, 1]`;
out-of-range values fail instead of being clamped. Tests cover explicit-rate
precedence, fallback, five-property 1% growth, monetary-only FX conversion,
and plan/bond percentage conversion.
