# Generic retirement planning and simulation

Retirement planning consumes only three things: planned cash flows, one liquid reserve, and capital
projections. It does not calculate rental yield, bond interest, tax, inflation, investment return,
maturity, or asset valuation.

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

Reserve combines cash and immediately available deposits. Simulation may withdraw it but never
automatically adds annual surplus to it. Future simulation always uses:

```text
reviewAdjustment = 0
reserveWithdrawal = min(startValue, fundingGap)
endValue = startValue - reserveWithdrawal
```

Review may apply an explicit, separately reported reserve adjustment. External contributions do not
exist inside future simulation. Unused surplus stays informational unless a review allocates it.

## Capital projections

The conceptual capital result contains:

```text
year, startValue, annualIncome, annualReturn,
availableForWithdrawal, requestedWithdrawal, actualWithdrawal, endValue, source
```

`annualIncome` is a cash flow and enters aggregation. `annualReturn` changes capital value only and
does not reduce the funding gap. The owning module decides availability, withdrawal, and end value;
actual withdrawal cannot exceed availability or value.

Long-Term maturity strategies are `REINVEST`, `MOVE_TO_RESERVE`, and `FUND_GAP`, defaulting to
`REINVEST`. Reinvested principal remains unavailable to Simulation. Move-to-reserve proceeds are a
reserve transfer. Fund-gap proceeds cover the current request and unused proceeds transfer to
reserve. Bond dates and maturity processing stay inside Long-Term.

Investment is a black box. Simulation sees no equity, fixed-income, market-cash, allocation, or
portfolio internals.

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
retirement spending instead of resetting it.

An open-year result exposes actual income/expenses, projected remaining income/expenses, expected
full-year net result, variance from the original plan, and any explicit reserve adjustment.

## Exclusions

The model excludes economic buckets, market-cash/equity/fixed-income simulation, configurable
withdrawal order, equity harvesting, emergency equity withdrawals, synthetic bonds, reserve targets
or refill rules, property valuation/growth inside Retirement, and duplicated asset formulas.
Legacy UI adapters may remain temporarily where external screens still require them; they are not
part of the generic simulation contract.
