# Planning timeline

This document defines the planning lifecycle and source-of-truth boundaries. Retirement funding and
simulation formulas are canonical in [`retirement-simulation.md`](retirement-simulation.md).

## States

| State | Meaning | Source |
|-------|---------|--------|
| **Actual** | A past annual planning snapshot. | Planning persistence: derived and approved values. |
| **Live** | The current calendar year. | Current `InvestmentProfile`, manual planning values, and stable baseline. |
| **Projected** | A future planning year. | Deterministic simulation output only. |

Projected `SimulationYear` values are never persisted as historical or accounting facts.

## Historical lifecycle

A past year begins as a planning **DRAFT**. The application derives reliable market values from the
canonical monthly portfolio-performance reporting data and marks unavailable planning values explicitly.
The user may supply planning-only approved values where the source is unavailable.

Closing is explicit and only permitted after the calendar year ends. A close requires:

- net worth or market assets;
- core spending;
- discretionary spending; and
- real-estate value when the draft has an unavailable real-estate value.

Net worth is optional when trustworthy Market assets are present. Historical Market assets, income,
withdrawals, and return are marked accounting-derived only when the portfolio-performance source has
all twelve unique January–December records. Partial monthly history is unavailable for annual
planning purposes and cannot satisfy the portfolio-value close anchor. Net worth is not reconstructed
from Market assets plus real estate because other assets, liabilities, valuation dates, or historical
FX may be missing. A closed snapshot may therefore remain partial by design.

A closed snapshot is stable: later accounting imports, market valuation changes, simulation changes, and
baseline changes do not rewrite it. Reopen is explicit; it returns the planning record to **DRAFT** and
does not recompute or mutate accounting facts automatically.

## Planned, actual, and unavailable values

`BASELINE` is the planned value captured from the immutable simulation revision. `ACTUAL` is the
reviewed historical value: it may be long-term-derived/accounting-derived or explicitly entered by
the reviewer. Planning values preserve a separate source marker. Important sources include
accounting-derived, portfolio-derived, long-term-derived, simulation-baseline, user override, and
unavailable.

Portfolio facts such as market assets, safe reserve, fixed income, equity, and canonical returns
cannot be manually overridden. Historical `NET_WORTH` is the reviewer-owned year-end planning
snapshot: during a DRAFT it may be entered or corrected as a `USER_OVERRIDE`, remains isolated from
accounting data, and freezes when the year closes. It becomes editable again only after explicit
reopen. Planning flows such as core spending and discretionary spending may also be entered when no
reliable historical source exists. `PASSIVE_INCOME` remains readable
only for legacy snapshots; new derivation uses explicit `RENTAL_INCOME`, `BOND_INCOME`, and other
supported categories instead.

Long-term Asset historical income uses the normalized annual economics exposed by
`LongTermAssetService`, the same calculation used by the Long-term Assets overview. Current asset
balances are not claimed as historical Dec-31 values without a dated valuation source, so real
estate, bond, and cash-reserve balances may remain unavailable. Refresh derived values updates only
open-year actual candidates and never changes the baseline or a user override.

Manual corrections never update `positions`, `cash_operations`, `account_daily`, reporting views, price
history, FX history, or API response data.

## Current year and baseline

The Live year is always the calendar year. It reads the current portfolio and manual assets through
`InvestmentProfile`, then overlays permitted current planning values. A baseline stores selected expected
metrics from a chosen simulation plan together with plan identity and capture time.

The baseline means “expected according to this plan when captured.” Editing the plan, changing the
calendar year, or switching display currency does not alter it. Only an explicit set/refresh/replace
baseline action may do so.

## Future projection and current-year bridge

Future projection starts after the Live year. The current live state is bridged to expected year end first;
the next calendar year starts from that bridged state, not from a mid-year portfolio as though it were
1 January.

The bridge uses the remaining calendar-day fraction for recurring spending, funding, contributions,
passive income, pension, and continuously modelled returns. Working-year contributions go to liquid cash
and use that same fraction; employment income remains informational. Retired years apply spending and
the configured funding strategy. Contractual PAY_OUT interest is netted for tax and becomes bridge cash;
CAPITALIZE interest increases the contractual asset and is not passive income. A remaining maturity uses
the canonical redemption value, or post-interest value when unset, moves cash to `LIQUID_CASH`, and leaves
a zero-valued asset identity. Current-year events and redemptions are applied once in the bridge; future
events and unmatured assets remain in the next full-year assumptions. Contractual effects use the remaining
calendar fraction; exact in-year maturity uses the bridge clock date. This is deterministic annual
bridging, not monthly cash-flow simulation.

## Forward simulation boundary

The saved plan retains its original `startYear`, `currentAge`, retirement assumptions, and event
definitions. A `ForwardSimulationContext` derives the current actual boundary from the application
calendar and carries the live `InvestmentProfile` as the economic starting state; it never rebuilds
today's portfolio by replaying old simulation years.

The effective age is:

```text
saved current age + (as-of calendar year - saved start year)
```

The context distinguishes the current partial year from the first complete projected year. Past events
are excluded from the full forward-year event list, current-year events are carried separately for the
bridge, and future events remain in the rebased assumptions. Future assumptions preserve retirement,
pension, contribution, return, spending, reserve, and funding settings. A plan beyond its configured end
age is rejected; an as-of year at the horizon has no complete projected-year assumptions.

This context is the foundation for later rebasing of scenarios and decision analyses. It does not perform
annual rollover. The bridge keeps the live profile immutable and returns a separate projected year-end
profile for the first complete projected year, preventing current-year contributions and events from
being applied twice.

## Forward-consumer architecture

Future-facing Simulation consumers use one `ForwardSimulationInput` per request:

```text
frozen history
    -> current actual InvestmentProfile
    -> current-year bridge
    -> first full projected year
    -> canonical annual simulator
    -> scenarios, charts, and decision analyses
```

Base, Conservative, and Optimistic scenarios share the same bridged profile and remaining event set;
only scenario settings diverge in projected years. Sustainable Spending, Sensitivity, and Retirement
Timing use the same rebased assumptions. None replays past contributions, events, returns, or funding.

## Annual rollover

`AnnualPlanningRolloverService` advances reporting state from the application calendar. For each
existing year before the calendar-current year it creates missing historical accounting-derived
values and exposes the year for review. It never closes a year automatically, even when the normal
historical completeness rules are satisfied. The calendar-current planning year is created when
absent; this does not depend on whether the previous year has been reconciled.

Calendar progression changes the forward simulation boundary. Closing a planning year changes
historical reporting state. Neither operation replays prior simulation years. Closed actuals are
immutable reporting data and are never used to reconstruct the live `InvestmentProfile` or the
forward simulation input. Reopening is an explicit historical correction operation and does not move
the simulation boundary backward.

The temporal model is therefore:

```text
historical years  -> frozen/reporting
current year      -> live actual + current-year bridge
future years      -> canonical annual simulation
```

The Simulation Plan vs Reality section presents an unclosed past year as **Needs review**, with a
link to its planning-year detail. A closed year is shown as **Actual**. Review and close remain
explicit user actions; normal calendar advancement and creation of the new current-year row are
quiet when no historical work is waiting. Multiple existing past drafts are listed for review.

## Accounting comparison

Historical review compares only the market metrics for which Investory has a trustworthy source:
year-end market assets, market income, net portfolio withdrawal, and compounded market return. For a
calendar year `Y`, the start boundary is the exact December closing value for `Y-1` when available,
and the end boundary is the December closing value for `Y`. Gross deposits and withdrawals remain
accounting facts; Planning derives `netContribution = max(deposits - withdrawals, 0)` and
`netWithdrawal = max(withdrawals - deposits, 0)`. Thus internal movements are not presented as
retirement withdrawals. Values are compared in canonical planning currency before display conversion.
Monetary differences use a `0.01` tolerance; return differences use `0.00000001`.

Core spending, discretionary spending, safe reserve, equity, total net worth, and aggregate passive
income remain manual/planning-only or unavailable for historical accounting comparison because the
current code does not provide equivalent historical sources for them. A missing source is shown as
unavailable, not estimated from simulation projections or today's portfolio. Differences inform
review but do not block close and never overwrite either side. Closed-year comparisons are current
read-only comparisons against the accounting view; the frozen planning snapshot remains unchanged.

Past events are excluded by the forward boundary, current-year events are applied only by the
current-year bridge, and future events remain in the annual simulator. At the next calendar year,
the previous bridge effects are outside the forward event set and cannot execute again.

## Calendar and age anchor

Saved plans retain their temporal anchor:

```text
currentYear = application clock year
currentPlanningAge = ageAtPlanStart + currentYear - planStartYear
retirementYear = planStartYear + retirementAge - ageAtPlanStart
age(year) = ageAtPlanStart + year - planStartYear
```

New plans may default their `planStartYear` to the current calendar year. Opening or editing a saved plan
never moves its anchor.

The Plan Editor exposes plan start year and age at plan start as the editable temporal anchor. Current
planning year and current planning age are read-only values derived from the application `Clock`.
Retirement age remains an editable absolute age boundary and its calendar year is shown as
`startYear + retirementAge - ageAtPlanStart`. Save As copies the source plan's temporal anchor rather
than silently creating a new current-year anchor.

Historical years from the plan start through the year before the current year are shown as
`NEEDS_REVIEW` until they have a planning record. The explicit **Prefill historical years from
Investory** action creates those records and derives only data available from canonical portfolio and
Long-Term readers. It never overwrites closed or manually entered values. Missing facts remain
manual/unavailable; the current year remains live and future years remain projected from the current-year
bridge.

When a historical draft is populated, the same complete-calendar-year rule applies: missing or
duplicated months leave annual market metrics unavailable. With twelve calendar months, Market return
is still unavailable when any monthly return is missing.

## Currency and presentation

Planning monetary values are stored in canonical USD/base representation. The planning UI converts only
at the presentation boundary using the application FX service:

```text
canonical amount -> selected display currency -> UI
UI amount -> canonical amount -> planning command
```

PLN is the default display/input currency; USD and EUR are also supported. Display currency changes do
not change canonical stored values. Ratios remain ratios and are not currency-converted. Normal money is
shown with natural grouping and up to two decimals; normal percentages are shown as percentage points to
one decimal place.

## Accounting isolation

```text
Accounting facts -> InvestmentProfile -> Planning / Simulation
```

Planning is downstream. It reads accounting and long-term assets but does not create transactions,
rebalance portfolios, or persist simulated transfers as real activity.

## Retirement boards and projection boundary

Simulation owns plan configuration, starting position, the selected scenario outcome, and the raw
yearly planning timeline. Analysis owns interpretation: cross-scenario comparison, charts,
sensitivity, spending flexibility, and retirement-age flexibility.

Both boards consume one `RetirementProjectionContext` prepared by `RetirementProjectionFacade`.
That boundary preserves the forward-input and current-year bridge semantics. Analysis derives its
baseline data from this completed context; it must not create a separate base projection pipeline.

## Plan provenance and revisions

A saved retirement plan has a stable logical identity and an immutable linear revision history. A
revision contains every Base simulation assumption and its life-event snapshots. Editing assumptions
creates a new revision and makes it current; it never edits a revision already used by a baseline.
Rollover does not create revisions, and scenario transforms remain runtime-only.

```text
Logical plan
   ├─ Revision 1 ──> 2026 baseline
   ├─ Revision 2
   └─ Revision 3 ──> 2027 baseline
```

Baselines store the exact revision ID used to calculate their expected values. Historical planning
detail can therefore identify the assumptions behind a baseline independently of later plan edits.
Save As creates a new logical plan with revision 1 and copies the current event set, not the source
revision history. Renaming is logical-plan metadata; archiving preserves revisions referenced by
history. Legacy plans are migrated to an initial revision representing the plan state known at
migration time; the database cannot reconstruct revisions that did not previously exist.

Plan revision provenance is separate from accounting reconciliation and from annual rollover. Stored
assumptions use canonical currency values; display currency never creates a revision or changes one.

## Plan progress and year in review

For a closed year, Plan Progress is an independent boundary comparison:

`reviewed Actual NET_WORTH at 31 December Y - frozen baseline NET_WORTH for Y`.

Each point keeps its own baseline plan and immutable revision provenance. Points are independent
boundary measurements, not yearly deltas to sum: the latest comparable point is the historical Plan
Progress headline. Later plan revisions establish new forward benchmarks and do not rewrite earlier
comparisons. Missing Actual or baseline net worth makes that point unavailable; `MARKET_ASSETS` is
not a fallback. Investment internals remain outside Simulation. Investment performance and other
unattributed effects remain inside an explicit **Other changes** residual in Year in review; the
planning layer does not reconstruct or attribute historical investment activity.
