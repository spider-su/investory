# Retirement Analysis

Retirement Analysis is a deterministic interpretation of one prepared forward projection. The
projection freezes the reviewed profile, projected assumptions, baseline/as-of year, and one
canonical Base `SimulationEvaluation`. Analysis services reuse that exact Base evaluation at the
canonical point; perturbed sensitivity points and alternative spending or retirement-age
candidates call the canonical deterministic simulator through `SimulationEvaluationService`.

The simulator maps each projected age to `startYear + age - currentAge`. The same calendar years
appear in scenario summaries, sensitivity cells, and retirement-age results. If the current-year
bridge leaves no forward years, Analysis returns `NO_FORWARD_HORIZON`; no sensitivity,
sustainable-spending, or retirement-age calculation runs.

The Analysis page is a presentation of this one context: the Base scenario, sensitivity Base
cells, sustainable-spending evaluation at current spending, and retirement-age evaluation at the
planned age all reconcile to the same canonical evaluation. Monetary materiality is evaluated in
the canonical plan currency before any display-currency conversion. Sensitivity cells carry tested
value, availability, status, failure year, and minimum spendable assets as separate values; the
web layer formats them but never recomputes outcomes.

## Deterministic results

Scenarios change coherent assumption sets: Base, Conservative, Optimistic, and an optional Custom
scenario. Custom is shown only when at least one custom delta is non-zero. Sensitivity is separate
and changes exactly one driver at a time. Historical stress testing replays annual market paths,
and Monte Carlo samples distributions; neither is part of deterministic Analysis. They are future
Analysis wrappers and must not change the frozen Simulation contract.

Economic-driver shocks are:

| Driver | Shock |
| --- | --- |
| Inflation | ±1 percentage point |
| Rental-income growth | ±0.5 percentage points |
| Fixed-income/Bond return | ±1 percentage point |
| Equity return | ±2 percentage points |
| Spending growth | ±0.5 percentage points |

Planning levers are separate: recurring spending ±10%, pension ±10%, sustainable spending, and
retirement-age flexibility. They are not market risks.

Rental-income and spending growth assumptions use `effective growth = inflation + stored spread`.
Sensitivity changes only the stored spread, so inflation stays fixed; Analysis displays the
effective rate. A stored spread must be labelled as a spread wherever it is shown.

Bond-return sensitivity applies to allocation-only planning fixed income and to CAPITALIZE source
Bonds only while an effective capital-return period affects the forward horizon. PAY_OUT-only
source Bonds are excluded even when their principal appears in fixed-income start/end balances.
Mixed PAY_OUT/CAPITALIZE portfolios apply only when at least one active CAPITALIZE period has
effective capital return. Expired or inactive periods do not create applicability.

Allocation-only means the reviewed source snapshot has no Bond/Deposit assets and the planning
allocation contains fixed income. Once reviewed source assets exist, applicability comes from the
source treatment and active periods, not from the fixed-income balance alone.

Each Lower/Higher perturbation is validated independently. An invalid point is unavailable, not
clamped; Base and the valid direction remain visible. If both directions are invalid, the driver
is omitted or marked unavailable.

The more harmful direction is selected lexicographically from actual outcomes: Base failure,
larger total unfunded amount, earlier first failure, recurring funding-gap introduction, lower
minimum spendable assets, lower safe-reserve coverage when applicable, and lower final net worth.
Equal outcomes are reported as Equivalent.

Impact thresholds are centralized in the Analysis service and use canonical currency, independent
of display currency: spendable-assets deterioration ≥ 1,000 monetary units; safe-reserve coverage
deterioration ≥ 0.5 years; final-net-worth deterioration ≥ 1% of Base final net worth. CRITICAL is
new unsustainability; HIGH is more unfunded amount or a new recurring gap; MODERATE is material
reserve or spendable-assets deterioration while sustainable; WEALTH_ONLY is material wealth loss
without sustainability or liquidity deterioration; NEGLIGIBLE is below thresholds. Improvements
are not classified as deterioration.

The UI presents compact Overview, Cash Flow, Risk, and Scenarios tabs. Presentation converts
canonical monetary values and creates structured sensitivity cells; it does not recalculate
financial outcomes. Historical paths and Monte Carlo remain future Analysis wrappers.
