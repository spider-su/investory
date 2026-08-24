# Retirement Analysis

Analysis is a deterministic interpretation of one prepared forward projection. It reuses the
frozen profile, assumptions, baseline year, and canonical Base evaluation. Calendar years come
from `SimulationYear.year()`. With no forward horizon, derived Analysis is not calculated and the
state is `NO_FORWARD_HORIZON`.

Scenarios change a coherent set of assumptions. Sensitivity changes one assumption at a time while
all other stored assumptions remain fixed. Economic results use **Lower / Base / Higher** and the
measured result identifies the more harmful direction. Shocks are Inflation ±1 pp, Rental growth
±0.5 pp, Bond return ±1 pp, Equity return ±2 pp, and Spending growth ±0.5 pp. Inflation changes
`inflationRate` only; stored rental and spending spreads stay fixed.

Drivers are omitted when ineffective: rental needs projected rental income; Equity needs projected
Equity capital; Bond needs allocation-based fixed-income or capitalized Bond capital. Payout-only
Bonds are excluded. Spending growth needs projected spending and Inflation needs a non-empty
horizon. Property appreciation and `SAFE_RESERVE_YEARS` are not supported economic drivers.

Values are nominal and converted only at presentation time. Materiality thresholds are centralized
in `SimulationSensitivityAnalysisService`: spendable-assets change ≥ 1,000, reserve-coverage
change ≥ 0.5 years, and final-net-worth change ≥ 1% of Base final net worth.

* `CRITICAL`: sustainable Base becomes unsustainable.
* `HIGH`: unfunded amount increases or a recurring funding gap is introduced.
* `MODERATE`: material reserve/liquidity deterioration while sustainable.
* `WEALTH_ONLY`: material final-wealth deterioration without reduced sustainability or liquidity.
* `NEGLIGIBLE`: below thresholds.

Planning levers (recurring spending, pension, retirement-age flexibility, and sustainable-spending
result) remain separate from economic drivers. Historical stress, annual market-path replay,
Monte Carlo, and saved-plan revision changes are outside this slice.
