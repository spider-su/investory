# Retirement simulation

This document defines the simplified annual simulation contract.

## Module ownership

The dependency direction is:

```text
Retirement -> Long-Term public API
Retirement -> Investment public API
```

Retirement owns assumptions, expenses, pension, employment income, one-off events, the signed
income-gap calculation, funding orchestration, the timeline, and result presentation. It does not
read persistence entities or infrastructure packages from another module.

Long-Term owns rental income, free reserve (cash and immediately available deposits), bonds, bond
maturity, reinvestment, and yearly long-term asset projections. Rental and bond income are net of
tax and costs. Long-Term exposes immutable annual values and does not expose persistence models.

Investment is one black box. Its annual public projection contains only year, start value, annual
return amount, withdrawal, end value, and source (`ACTUAL` or `PROJECTED`). Investment owns return
and end-value calculation.

## Annual calculation

Use signed values internally:

```text
annualIncomeGap =
    annualExpenses
    + eventExpenses
    - sum(monthlyNetRentalIncome) * 12
    - sum(netBondIncome)
    - monthlyPension
    - netEmploymentIncome
    - eventIncome

requiredFunding = max(annualIncomeGap, 0)
annualSurplus   = max(-annualIncomeGap, 0)
```

Investment return is not income. It never reduces the gap; it changes the investment end value
after the requested withdrawal.

Funding is always performed in this order:

1. annual income offsets expenses;
2. free reserve;
3. matured bond principal allowed by its maturity strategy;
4. investment withdrawal;
5. unfunded shortfall.

This order is fixed and is not configurable.

## Bond maturity strategies

Each bond has a maturity date, redemption value, net annual income, source, and strategy:

- `REINVEST`: renew the matured principal using the configured renewal term and net rate;
- `MOVE_TO_RESERVE`: move redemption proceeds to free reserve;
- `FUND_GAP`: use proceeds for the current annual gap and move unused proceeds to free reserve.

Missing strategy means `REINVEST`. Reinvested principal is unavailable for current funding.

## Lifecycle

Working years include configured employment income and have no retirement spending. Employment
income is zero from retirement onward. Pension applies only from its configured start age/date.
One-off events remain active in working and retired years.

Retirement spending starts at retirement. Spending growth compounds only after retirement; it does
not grow during working years. A forward rebase carries the accumulated retirement spending into
the new forward boundary and must not reset it.

Values are marked actual or projected at the public module boundaries. The simulation does not
invent actual values from projections.

## Exclusions

The simplified model has no economic buckets, separate market-cash/equity/fixed-income simulation,
configurable withdrawal order, reserve-target calculation, equity harvesting, emergency equity
withdrawal, synthetic bonds, rental-property valuation or growth, or duplicated maturity/return
calculation in Retirement.
