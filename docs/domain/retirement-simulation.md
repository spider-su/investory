# Deterministic retirement planning and simulation

Retirement planning consumes normalized economic contracts: planned cash flows, liquid reserve,
capital projections/availability, liabilities where applicable, and planning assumptions owned by
the supplying domain. It does not consume concrete asset implementations and does not calculate
rental yield, bond interest, tax, investment return, maturity, or asset valuation owned by another
domain.

The core temporal rule is:

```text
Past     -> immutable reviewed facts
Current  -> live derived state
Future   -> deterministic projection from an immutable reviewed plan revision
```

## Ownership and dependency direction

```text
Retirement/Simulation -> Long-Term public API
Retirement/Simulation -> Investment public API
```

Retirement owns the requested period, lifecycle assumptions, generic flow aggregation, funding gap,
reserve withdrawal, capital-funding orchestration, timeline, and reporting. Long-Term Assets owns
rental and bond/deposit projections, maturity, redemption, reinvestment, availability, and asset
capital values. Investment owns portfolio valuation, return, withdrawal, and end value. Neither
asset module depends on Retirement.

Taxes and costs are already reflected in supplied net flow values.

Long-Term bond returns remain distinct from cash income. `PAY_OUT` interest is emitted as bond cash
income and participates in the funding gap; `CAPITALIZE` interest is exposed as capitalized bond
return and increases Long-Term capital without entering cash income. Investment return is likewise
portfolio value change, not cash income. Retirement may explicitly select SOURCE (the frozen
Long-Term baseline) or MANUAL rental/bond cash-income planning assumptions; these choices are stored
on the immutable revision and never read live source state during Future projection.

The public contracts form an anti-corruption boundary. Retirement must not inspect whether capital
came from an ETF, brokerage cash position, individual bond, deposit, property, rental contract, or
another implementation-specific source. The owning module converts those details to economic meaning
before the Retirement boundary.

Conceptually the boundary exposes normalized planning information such as:

```text
balances
cash flows
reserve
capital available for withdrawal
requested / actual withdrawal
ending capital
```

Exact Java records may be narrower and split by responsibility. This is a semantic contract, not a
requirement for one universal DTO.

## Inflation and growth spreads

Inflation is the economy-wide nominal baseline. A saved plan stores rental and spending growth as
spreads relative to that baseline, not as independent nominal rates:

```text
effective rental growth  = inflation + rental growth spread
effective spending growth = inflation + spending growth spread
```

For example, inflation `+2.5%`, rental spread `+0.5%`, and spending spread `+1.5%` produce
nominal rental growth of `+3.0%` and nominal spending growth of `+4.0%`. Negative spreads are
valid when the effective multiplicative rate remains at least `-100%`.

Simulation executes one prepared set of assumptions. Analysis may prepare alternative assumptions
before calling Simulation; scenario selection is not a branch inside the yearly engine. Physical
plan columns retain their legacy `*_growth_rate` names for compatibility, but their values are
spreads.

All values cross the plan boundary in the plan currency. Source-currency asset values are converted
once, using the shared target-currency-first conversion service, before they become a flow, capital
projection, or page view value. A display-currency change only formats the already-normalized value.

## Planned cash flows

The generic flow contract is:

```text
PlannedCashFlow(id, category, direction, cadence, amount, effectiveDate, source)
```

`direction` is `INCOME` or `EXPENSE`; `cadence` is `MONTHLY`, `ANNUAL`, or `ONE_OFF`; `source` is
`ACTUAL` or `PROJECTED`. `category` is reporting metadata only. Employment is an ordinary planned
income flow while working and is absent after employment ends. Pension, rental, bond income, living
costs, events, and vacations use the same contract.

For any period:

```text
periodIncome  = all applicable income
periodExpense = all applicable expenses
netCashFlow   = periodIncome - periodExpense
fundingGap    = max(-netCashFlow, 0)
surplus       = max(netCashFlow, 0)
```

For a full year this becomes:

```text
12 * monthlyIncome - 12 * monthlyExpense
+ annualIncome - annualExpense
+ eventIncome - eventExpense
```

The same aggregation service handles annual, quarterly, and partial periods. Actual flows replace
projected flows with the same stable identity and covered date; they are never added twice.

Future simulation uses projected flows only. Review uses actual flows through the review date and
projected flows for the remaining period.

## Reserve

Reserve is a generic planning balance. Simulation may withdraw it and, when the active
RetirementFundingPolicy permits, refill it from explicitly reported capital transfers. Future
simulation uses:

```text
reserveWithdrawal = min(reserveStart + explicitReserveTransfers, fundingGap)
reserveEnd = reserveStart + explicitReserveTransfers - reserveWithdrawal
```

Review may apply an explicit, separately reported reserve adjustment. External contributions do not
exist inside future simulation. Unused Long-Term and Investment capital remains with its owning module.
The active RetirementFundingPolicy uses `reserveTargetYears * recurringFundingGap` as a refill
target. Funding order is configurable at the economic level (`RESERVE -> LONG_TERM -> INVESTMENT`;
legacy `CASH,BONDS,STOCKS` values are mapped at the boundary). When permitted, positive Investment
gain at or above the policy threshold may transfer a capped share to Reserve. This transfer is capital,
not income, and is tracked separately from spending withdrawal.

## Capital projections

The conceptual capital result contains:

```text
year, startValue, annualIncome, annualReturn,
availableForWithdrawal, requestedWithdrawal, actualWithdrawal, endValue, source
```

`annualIncome` is a cash flow and enters aggregation. `annualReturn` changes capital value only and
does not reduce the funding gap. The owning module decides availability, withdrawal, and end value;
actual withdrawal cannot exceed availability or value.

Long-Term decides maturity, redemption/reinvestment, tax, income and availability. Its annual
planning boundary exposes a non-consuming `quote` containing current-year cash flows and capital
available for withdrawal. `plan` is the single committed transition: it applies the request,
reinvests unused maturity capital, and returns the only valid `endState` for the next year.
Retirement may quote once and execute once; it never uses quote state as a future state. It returns
ordinary annual income flows, explicit reserve transfers, actual capital provided, end capital,
and its next state. Retirement does not construct bond/deposit inputs or inspect maturity rules.

Investment is a black box. Simulation sees no equity, fixed-income, market-cash, allocation, or
portfolio internals.

The same rule applies to Long-Term. Simulation sees no rental-contract implementation, bond terms,
deposit implementation, property subtype, or persistence model. Those details remain inside
Long-Term.

## Plan input snapshot

Current state and reviewed future plans deliberately use different lifecycles.

The current page reads live normalized contracts from Investment and Long-Term. When the user
reviews/rebaselines a plan, Retirement freezes the normalized economic values required by the
simulation into a new immutable plan revision. Future simulation consumes that revision snapshot
rather than re-reading today's source state.

```text
Investment public API ----\\
                            -> normalized economic state
Long-Term public API -----/              |
                                         v
                               reviewed plan revision
                                         |
                                         v
                                  annual simulator
```

Changing a market position, valuation, rental contract, bond, or other source record therefore
changes CURRENT immediately but does not change an already reviewed FUTURE projection. Incorporating
the new source state is an explicit review/rebaseline operation that creates a new revision.

The snapshot is planning provenance, not duplicated domain ownership. It stores only the economic
inputs required by Retirement, never source-domain entities or enough internal structure to
reimplement Investment or Long-Term calculations.

## Annual orchestration

Each year:

1. request planned flows and capital projections;
2. combine module flows with Retirement-owned flows;
3. aggregate income and expenses;
4. calculate signed net flow, gap, and surplus;
5. apply Long-Term reserve transfers;
6. withdraw free reserve;
7. request Long-Term capital explicitly available by strategy;
8. request the remaining amount from Investment;
9. record any unfunded amount;
10. carry returned end values to the next year.

Funding order is fixed: cash-flow income, free reserve, Long-Term capital, Investment, unfunded gap.
Annual surplus never refills reserve or capital automatically.

Example: monthly income 2,000, monthly expense 2,500, annual expense 1,000, annual income 600,
and a 200 one-off expense gives:

```text
netCashFlow = 12*2000 - 12*2500 + 600 - 1000 - 200 = -6600
fundingGap = 6600
```

## Lifecycle and reviews

Working years include employment flow and no retirement spending. Employment is zero after
retirement. Pension starts only at its configured date. One-off flows remain active in both phases.
Spending starts at retirement and grows only afterward. Forward rebasing carries accumulated
retirement spending instead of resetting it. This is deliberate: `annualLivingExpenses` and
`annualDiscretionaryExpenses` represent retirement spending, not a household budget before
retirement.

Long-Term supplies rental and fixed-income flows through its public planning API. Rental values in
the current-year bridge are canonical facts; the next projected year is produced by the Long-Term
contract/period projection and effective growth rules, so it need not equal a naive rounded
`current × (1 + growth)` calculation. Cash income
(rental, bond interest, employment, pension, and events) funds spending; investment return changes
portfolio value and is not cash income. Bond interest, maturity moved to reserve, and maturity used
directly for a funding gap are separate events. Rental growth,
interest treatment, tax, maturity and Long-Term valuation are owned by Long-Term. Capitalized
interest is not a cash-flow income source.

## Canonical timeline

`PlanningTimeline` is the application-facing plan representation:

```text
immutable historical facts
    -> current live state
    -> reviewed plan revision
    -> future deterministic projection
```

Closed historical rows are immutable facts and are never re-simulated. Draft historical rows may
be refreshed or corrected and remain `NEEDS_REVIEW` when facts are unavailable. The current-year
bridge is explicit: actual state plus projected remaining current-year flows and returns produces
the expected year-end state exactly once; that state starts the first future row. The timeline
begins at the configured plan start year and includes missing historical rows explicitly.

Sandbox is not implemented. It will be another prepared input source for the same deterministic
engine. Analysis is scenario/risk evaluation that prepares inputs; Simulation is plan execution.

An open-year result exposes actual income/expenses, projected remaining income/expenses, expected
full-year net result, variance from the original plan, and any explicit reserve adjustment.

## Exclusions

The model excludes economic buckets, market-cash/equity/fixed-income simulation, bond maturity or
rental-contract mechanics, property valuation/growth inside Retirement, and duplicated asset formulas.
Retirement owns only configurable economic withdrawal order, reserve targeting, Investment harvest,
and optional Investment withdrawal. Actual and projected source labels remain separate; a projected
return assumption is never labelled actual.

Retirement also excludes direct dependencies on source-domain entities and concrete asset types.
Adding a new Investment instrument or Long-Term asset type should require a Retirement change only
when it introduces genuinely new economic semantics that cannot be represented by the existing
public planning contracts.
## Economic starting buckets

Retirement distinguishes liquidity from funding ownership. `Liquidity.LIQUID` describes how easily
an asset can be converted; it is not the Retirement reserve bucket. Brokerage cash and explicitly
designated cash reserves form `retirementReserve`, while brokerage ETFs, stocks, funds and other
Investment-owned market value form `investmentCapital`. Long-Term rental and contractual assets remain
Long-Term capital. Future fixed-income cash flow is projected by Long-Term from its planning state;
the current-year bond-income fact is not copied into the future as a scalar.
