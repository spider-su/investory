# Retirement Planning & Simulation — Manual QA Plan

## Scope

Deterministic analysis, plan editor, Past/Actual, Current/Live and Future/Projected timeline. UI routes: `/analysis`, `/simulation`, `/simulation/plan/edit`.

## Before testing

- Use a disposable plan and record its ID/revision. Do not delete or rebaseline a real plan.
- Have known source data: profile value, rental/passive income, pension, expenses, cash reserve, bond/deposit maturity and at least one equity holding.
- Test display/input currencies PLN, USD and EUR; storage remains canonical base/USD amounts.

## Test cases

| ID | UI action | Expected result | DB / Kubernetes evidence |
| --- | --- | --- | --- |
| RET-01 | Open Analysis and Simulation for a populated profile. | Required funding, reserve, passive income, portfolio inputs and sustainability result are understandable and agree across surfaces where they represent the same metric. | Trace sampled values to profile and long-term/investment sources; capture plan/revision ID. |
| RET-02 | Create a plan using default settings, then edit spending, passive income, pension, start age/year, inflation, rental/spending growth and return assumptions. | Validation is clear; plan reload is lossless; displayed percentage inputs are percentage points; no silent unit conversion. | `simulation_plans`, events/revisions as applicable; verify full precision/canonical amounts and no unexpected source-domain writes. |
| RET-03 | Switch PLN/USD/EUR and enter values with decimals. | Display converts consistently; changing presentation currency alone does not alter underlying economics or introduce rounding drift. | Compare stored canonical values before/after; inspect FX inputs and logs for unavailable FX. |
| RET-04 | Compare Simple Waterfall with Reserve + equity harvest on controlled assumptions. | Funding order follows selected strategy. Reserve + Harvest spends manual reserve → market cash → spendable fixed income → permitted emergency equity; harvest is limited by gate/eligible gain/target. | Inspect computed timeline values and source inputs; no ledger transactions, positions, prices or FX facts are written. |
| RET-05 | Add/edit/delete plan events, including income and expense, dates inside/outside plan and invalid values. | Only valid events change affected years; delete is scoped to selected event; errors do not leave partial state. | `simulation_plan_events` and revision-event records; check transaction rollback on rejected input. |
| RET-06 | Use plan preview/edit; save a revision, reload, and compare prior/latest revision. | Revision boundary is explicit and historical reviewed economics remain reproducible. | `simulation_plan_revisions`, `_revision_events`; verify expected current revision and timestamps. |
| RET-07 | Mark a past year Actual/approved, attempt edit, reopen, edit, then reapprove. | Approved snapshot is immutable until explicit reopen; reopening is visible and preserves auditability. | `planning_years`, `planning_year_values`, plan revision links; verify state transitions and no duplicate annual snapshot. |
| RET-08 | Inspect current Live year; create/update its baseline and add a manual correction; close and reopen it. | Actual-versus-expected uses a stable baseline. Corrections are planning values, not personal transactions; close/reopen follows confirmation workflow. | Planning tables only; ensure no writes to `cash_operations`, `positions`, `account_daily`, prices or FX. |
| RET-09 | Inspect several Future years around bond/deposit maturity and reserve depletion; change a long-term input then rebaseline. | Future is deterministic and recalculates from allowed inputs; contractual redemption/treatment is correct; rebaseline is explicit rather than silently rewriting a reviewed plan. | Compare plan/revision snapshots before/after; child rows remain consistent. |
| RET-10 | Test unsustainable scenario, zero/negative/invalid assumptions, missing/stale FX and missing required source facts. | Unfunded/unsustainable outcome is honest; validation/error is actionable; no fabricated result or server error. | Application logs and response details; confirm database consistency after rejected commands. |

## Operational checks

- During a rebaseline or multi-year load, inspect pod CPU/memory, restarts and log latency; no job should mutate accounting facts.
- On a representative plan, independently calculate one year’s funding gap: `max(expenses - passive income - pension - event income, 0)` and compare full precision before display rounding.

## Release exit

- Timeline lifecycle Past → Current → Future and approval/reopen controls pass.
- Both strategies pass controlled funding-order scenarios.
- All planning writes stay inside planning tables; reads from portfolio/manual assets never become real trades or ledger rows.
