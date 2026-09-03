# Retirement module: simulation --- quick KT

> **Audience:** developer already familiar with Investory's
> **Investment** and **Long-Term Assets** modules.\
> **Goal:** understand enough of Simulation to navigate the code and
> make a safe first change.

## 1. What Simulation does

Simulation answers:

> **Given today's assets, a retirement plan, and a scenario, can future
> spending be funded through the planning horizon?**

It does **not** own Investment or Long-Term Assets. It consumes an
`InvestmentProfile` and a **frozen planning baseline** assembled through
their public APIs.

``` text
SOURCE MODULES                         SIMULATION

Investment --------\
                    +--> Frozen baseline ----\
Long-Term Assets --/                         |
                                              +--> RetirementProjectionApi
                                                   |
                                                   v
                                      RetirementSimulationService
Plan revision ------------------------------>|
Scenario ----------------------------------->|
                                              v
                                      Year-by-year result
                                              |
                                  +-----------+----------+
                                  |                      |
                                 UI                   Analysis
                                             (reruns same engine)
```

**Key boundary:** source modules provide current facts. The baseline
freezes those facts for the plan. Simulation then works
deterministically from that baseline.

## 2. Planning buckets

The detailed portfolio is reduced to four retirement buckets:

``` text
Funding order

LIQUID_CASH  ->  FIXED_INCOME  ->  EQUITY  ->  REAL_ESTATE
 ^          ^                         ^
 first      may be refilled           last resort
            from Equity gains
```

-   **Cash** --- liquidity, modeled at 0% return, spent first.
-   **Bonds** --- defensive capital; return follows its configured
    payout/capitalization policy.
-   **Equities** --- growth capital; eligible gains may refill Bonds.
-   **Real Estate** --- provides rental income; capital is sold only
    after liquid buckets are exhausted.

Internal transfers conserve value:

``` text
Equities  -30K
Bonds     +30K
----------------
Transfer total = 0
```

Do not confuse **investment return** with **spendable income**. A return
may stay capitalized in its bucket instead of becoming cash available
for spending.

## 3. Planning timeline and continuity

``` text
ACTUAL -> NEEDS_REVIEW -> LIVE -> PROJECTED
 facts      review       current  simulated
```

`PlanningTimelineState` is the API vocabulary. Historical years are
`ACTUAL`; a current year can be `NEEDS_REVIEW` or `LIVE`; future years are
`PROJECTED`. Scenarios do not rewrite actual facts.

**Current:** available current-year facts plus the canonical planned
remainder.

**Projected:** future years generated from the frozen baseline, plan and
selected scenario.

Important continuity invariant:

``` text
CURRENT expected year-end
        |
        v
first PROJECTED year start

projected[n].end(bucket)
        ==
projected[n+1].start(bucket)
```

Therefore, a current Cash value is **not necessarily** next year's Cash
start: the remaining part of the current year must be applied first.

## 4. One projected year

Use this as a mental model, not exact implementation ordering:

``` text
for each year:

    resolve income + spending
    apply bucket-specific return policy
    determine spendable cash

    if spending is not covered:
        fund gap using
        Cash -> Bonds -> Equities -> Real Estate

    if permitted:
        refill Bonds from eligible Equity gains

    produce immutable year-end bucket state
```

Typical cash income can include rent, bond payouts, employment, pension
and events. Capitalized returns remain in their bucket rather than
becoming living-cost income.

### Failure

Conceptually:

``` text
SUSTAINABLE
    every year can fund required spending

FAILURE
    permitted income + withdrawals cannot fund spending

unfunded > 0
    => failure in that year
```

Check the simulator/tests for the exact authoritative failure rules
before modifying them.

## 5. Scenarios and funding

Preset scenarios include:

``` text
Conservative | Base | Optimistic
```

A scenario changes **PROJECTED years only**. Historical and current
facts remain unchanged.

Main scenario-effective assumptions include:

``` text
Inflation
Rental growth
Bond return
Equity return
Spending growth
```

Mental model:

``` text
Frozen baseline
      +
Plan revision
      +
Scenario overlay
      |
      v
RetirementSimulation
      |
      v
SimulationResult
```

## 6. Where to start in code

Read these in order:

``` text
1. RetirementSimulation
   Core deterministic year-by-year engine.

2. RetirementProjectionFacade
   Prepares/orchestrates planning input and simulation.

3. SimulationEvaluationService
   Canonical entry point when Analysis reruns Simulation.

4. RetirementAnalysisService
   Example consumer/interpreter of simulation output.

5. Simulation tests
   Executable documentation for bucket transitions and invariants.
```

`RetirementAnalysisService` and the sensitivity/sustainable-spending
services should **reuse the canonical evaluation path**, not reproduce
financial formulas. Funding behavior is selected with
`SimulationFundingStrategy`: `SIMPLE_WATERFALL` or
`RESERVE_AND_HARVEST`.

## 7. Rules for safe changes

``` text
DO NOT

- read mutable Investment/Long-Term state during future projection
- modify historical/current facts through a scenario
- treat capitalized return as spendable cash
- duplicate scenario or funding calculations in Analysis/UI
- calculate financial rules in Thymeleaf or JavaScript
- use system date inside deterministic simulation
- break year-to-year bucket continuity
```

Remember the ownership chain:

``` text
Investment / Long-Term
        |
        v
  frozen baseline
        |
        v
     Simulation
        |
        v
 deterministic result
        |
        +--> UI
        |
        +--> Analysis
```

If you understand **baseline ownership**, the **four-bucket funding
waterfall**, **CURRENT → PROJECTED continuity**, and that **Analysis
reruns the same deterministic engine**, you have the essential model
needed to start working in Simulation.
