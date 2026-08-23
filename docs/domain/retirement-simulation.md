# Deterministic retirement planning and simulation

## Aggregate planning buckets (current model)

Retirement simulates four immutable aggregate planning buckets: **Cash, Bonds, Equities, and
Real Estate**. Investment and Long-Term provide the reviewed/frozen starting state; Retirement
does not simulate individual holdings, bond ladders, rental contracts, maturities, or tax lots.

Each projected year executes cash income, aggregate returns, costs, Cash withdrawal, Bonds
withdrawal, Equities withdrawal when enabled, Real Estate withdrawal only after all liquid buckets
reach zero, then positive equity-gain transfer to Bonds up to the frozen initial Bond target.
Cash has zero yield and is never auto-refilled. Bond return stays in Bonds; equity return stays in
Equities unless the harvest policy moves an eligible share to Bonds. Remaining balances carry into
the next year. `UNFUNDED` is recalculated per year and is not sticky.

Current and historical review keeps Plan and Actual separate. Actual bucket values may include
manual activity or external cash and may become the next reviewed baseline; they are not forced to
reconcile to projected mechanics.

Retirement planning consumes normalized economic contracts and freezes them into four aggregate
planning buckets: **Cash, Bonds, Equities, and Real Estate**. Source domains own the current factual
composition of those buckets; Retirement owns how the frozen aggregate balances are projected,
reinvested, transferred, and consumed by the plan.

Retirement does not consume concrete holdings. Investment and Long-Term remain responsible for
current valuation, source-domain tax/return facts, rental contracts, individual bonds, maturities,
and instrument identity. At the Retirement boundary those details are normalized into the economic
starting values and planning assumptions required by the deterministic bucket engine.

The core temporal rule is:

```text
HISTORICAL -> reviewed facts only
CURRENT    -> live/current facts + projected remainder -> expected year-end state
PROJECTED  -> current expected year end + frozen plan assumptions + selected scenario
```

`ForwardSimulationContextFactory` is the sole owner of the temporal boundary. It resolves the
as-of year, current planning age, first projected year, current-year events, and rebased future
assumptions. The deterministic simulator receives that resolved baseline year explicitly; it never
reads the system clock. Therefore the same profile, assumptions, baseline year, and scenario always
produce the same result.

## Ownership and dependency direction

```text
Retirement/Simulation -> Long-Term public API
Retirement/Simulation -> Investment public API
```

Retirement owns lifecycle assumptions, generic flow aggregation, funding gap, bucket transitions,
funding priority, Bond transfers from eligible Equity gains, timeline, and reporting. Long-Term Assets
owns the factual/current composition and economics of bonds, cash reserves, real estate, and rental
contracts. Investment owns the factual/current brokerage/investment state. Neither asset module
depends on Retirement.

Taxes and source-domain costs are reflected when source facts are normalized into the reviewed
baseline. Future Retirement projection does not replay individual bond maturities, rental contracts,
or holdings. It carries forward aggregate bucket balances using the frozen planning assumptions.

Economic return remains distinct from cash income. Rental cash income, employment, pension, events,
and any explicit bond cash payout are spendable flows. Bond bucket return and Equity return normally
change capital value and do not reduce the funding gap directly.

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

### Scenario overlay

Scenario selection is a runtime overlay on the frozen plan revision. Historical and current rows
remain factual; only projected rows use the overlay. Active projected modifiers are inflation,
rental growth, spending growth, Bond return, and Equity return. The BASE Bond yield is derived
from the frozen Bond capital and reviewed Bond income when available, then the selected scenario's
fixed-income delta is added. If source income is unavailable, the selected scenario fixed-income
rate is used as the fallback.

Cash return is retained only for persisted-plan compatibility and is ignored because Cash has a
canonical 0% yield. Real Estate capital appreciation and `otherReturnRate` are also compatibility
fields and are not modeled by the aggregate Retirement bucket engine; Real Estate currently changes
through rental cash income/growth only. Scenario selection never writes a plan revision.

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

## Planning buckets

Future Retirement projection uses four aggregate buckets:

| Bucket | Role | Planned yield | Spending priority | Automatic transfer |
| --- | --- | ---: | ---: | --- |
| Cash | immediate spending liquidity | `0%` | 1 | none |
| Bonds | defensive capital | frozen bond planning yield | 2 | eligible Equity gains |
| Equities | growth capital | plan Equity return assumption | 3 | retained own return |
| Real Estate | last-resort capital | optional value-growth assumption | 4 | none |

Rental income is a cash flow produced by the Real Estate economic state; property value is capital.
The two must not be conflated.

Every yearly bucket result exposes, conceptually:

```text
startValue
returnOrGrowth
netTransfer
withdrawal
expectedEndValue
```

Derived end values are simulation output and are not persisted as plan inputs.

### Cash

Cash is deliberately simple:

```text
cashReturn = 0
cashEnd = max(0, cashStart - cashWithdrawal)
```

Cash is consumed first and is **not automatically refilled** by Bonds or Equities. Any remaining Cash
carries into the next year unchanged. During a year review the user may record an Actual cash balance
different from Plan. That variance is accepted as reality rather than reverse-engineered by the
simulator, and an explicitly reviewed/rebaselined value may become the next frozen starting state.

### Bonds

Bonds are an aggregate defensive bucket. Retirement does not distinguish coupon, interest, and
principal when funding a deficit: available Bond capital is spendable after Cash.

Bond cash payout and Bond capital return are separate normalized facts. The frozen profile planning
snapshot may contain source-derived bond periods, maturity dates, tax rates, and interest treatment.
`FrozenBondCashFlowProjection` evaluates those immutable details for projected cash income. A
`PAY_OUT` period contributes spendable cash and does not add that payout to Bond principal. A
`CAPITALIZE` period contributes no cash income and its net return is represented by Bond capital
return. Mutable Long-Term services are never consulted during forward simulation.

```text
bondsBeforeSpending = bondsStart + bondReturn
bondsAfterSpending = max(0, bondsBeforeSpending - bondWithdrawal)
bondsExpectedEnd = bondsAfterSpending + bondTransfer
```

Unused Bond capital is considered reinvested automatically and becomes the next year's Bond start.
Future Retirement does not maintain an individual bond ladder. Long-Term detail is used only to
derive the reviewed starting capital and planning yield.

### Equities

Equities are the growth bucket:

```text
equityReturn = equitiesStart * equityReturnRate
equitiesBeforeRefill = equitiesStart + equityReturn
```

Positive eligible Equity gain may transfer value to Bonds toward the configured Bond target. The
transfer is internal capital movement, not cash income:

```text
eligibleGain = max(0, equityReturn)
harvest = eligibleGain * equityGainHarvestRate
bondTransfer = min(harvest, max(0, bondTarget - bondsAfterSpending))
equityTransfer = -bondTransfer
```

The existing minimum-return threshold gates whether harvesting is permitted. Any unharvested gain
remains invested in Equities. Equity principal is consumed for spending only after Cash and Bonds,
subject to the configured emergency/withdrawal policy.

### Real Estate

Real Estate is the final capital source. Rental income participates in ordinary cash income, while
property capital is preserved until the liquid buckets are exhausted. The initial critical threshold
is zero:

```text
sell Real Estate only when Cash == 0 and Bonds == 0 and Equities == 0
```

Retirement reduces aggregate Real Estate capital only; it does not choose or sell individual
properties.

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

Each projected year is evaluated from scratch from the previous year's end state:

1. start with Cash, Bonds, Equities, and Real Estate balances;
2. aggregate ordinary cash income and annual costs;
3. apply Bond return and Equity return to their own buckets;
4. calculate the remaining funding gap;
5. withdraw Cash;
6. withdraw Bonds;
7. withdraw Equities when policy allows and a gap remains;
8. withdraw Real Estate only after Cash, Bonds, and Equities are exhausted;
9. if eligible positive Equity gain remains, transfer value to Bonds toward the Bond target;
10. calculate end values for all four buckets;
11. record any amount still unfunded;
12. carry the end values into the next year.

The canonical spending priority is therefore:

```text
cash income -> Cash -> Bonds -> Equities -> Real Estate -> unfunded
```

Bond transfer is the reverse capital-maintenance path:

```text
eligible Equity gain -> Bonds (up to target)
```

The developer table shows this as a signed `Transfer`: Bonds receive `+X` and Equities show
`-X`. Internal transfers conserve value:

```text
sum(all bucket net transfers) = 0
```

For every applicable consecutive pair of projected rows, the authoritative bucket identity is:

```text
Expected end = Start + Return + Net internal transfer - Withdrawal
ExpectedEnd(year N) = Start(year N+1)
```

### Current/live and projected boundaries

`CURRENT`/`LIVE` has two distinct boundaries. The opening value is the current factual bucket
balance. The bridge projects only the remaining part of the current calendar year using the same
authoritative simulator, and exposes the resulting expected year-end bucket value. This is why
`Cash now` can be higher than `Expected year end`; the difference is the remaining current-year
cash use after income.

The first `PROJECTED` year opens from those expected current-year end values. Each projected year
applies the scenario return assumptions, signed internal transfers, and external withdrawals;
its expected end is the next year's start. Current factual balances are never presented as if they
were final current-year balances.

The continuity identities are:

```text
CurrentExpectedEnd(bucket) = FirstProjectedStart(bucket)
ProjectedEnd(year N, bucket) = ProjectedStart(year N+1, bucket)
```

Cash is not automatically refilled. Remaining Bonds and Equities are automatically reinvested under
the same frozen assumptions.

`UNFUNDED` is a **per-year result**, not a sticky failure state. Every subsequent year reevaluates
income, returns, events, and all available bucket balances. A later year may recover if new resources
become available.

Example: monthly income 2,000, monthly expense 2,500, annual expense 1,000, annual income 600,
and a 200 one-off expense gives:

```text
netCashFlow = 12*2000 - 12*2500 + 600 - 1000 - 200 = -6600
fundingGap = 6600
```

That `6600` gap is then funded in bucket priority order.

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

Sandbox uses the same deterministic engine with an alternate prepared input. It may override
planning-bucket starts and yields without mutating Investment or Long-Term source data. At minimum,
Sandbox may override Cash start, Bond start/yield/target, Equity start/yield, Real Estate start, and
rental cash income. When Sandbox is disabled, the immutable reviewed baseline is authoritative.

Sandbox and Custom have different intended ownership. Sandbox changes the what-if starting state or
baseline. A future Custom scenario, if introduced, should change projected effective rates only; it
must not rewrite CURRENT or HISTORICAL facts. Custom persistence is not part of the current model.

Analysis is scenario/risk evaluation that prepares inputs; Simulation is plan execution. Analysis
must not implement a separate funding engine.

An open-year result exposes actual income/expenses, projected remaining income/expenses, expected
full-year net result, variance from the original plan, and any explicit reserve adjustment.

## Exclusions

Retirement deliberately excludes:

- individual bond ladder and maturity simulation;
- individual Equity holdings and tax lots;
- rental-contract mechanics;
- choosing which physical property to sell;
- duplicated source-domain valuation formulas;
- direct dependencies on source-domain persistence entities.

The source domains may use those details to derive the reviewed starting state. Once frozen, Future
Retirement operates only on the aggregate planning economics required by the four buckets and cash
flows.

Adding a new Investment instrument or Long-Term asset type should require a Retirement change only
when it introduces genuinely new economic semantics that cannot be normalized into the existing
bucket/flow contracts.

## Economic starting buckets

Retirement distinguishes source composition from planning ownership:

```text
brokerage/free cash + designated cash reserve -> Cash
bond/fixed-income capital                    -> Bonds
ETF/stock/fund investment capital            -> Equities
property capital                             -> Real Estate
```

The reviewed baseline also carries the planning assumptions needed to project those balances, such as
Bond yield, Equity return, rental income/growth, Bond target, harvest threshold/share, and
whether Equity principal may be used for spending.

Current/live source changes affect CURRENT immediately. Future changes only after an explicit
review/rebaseline creates a new immutable revision.
