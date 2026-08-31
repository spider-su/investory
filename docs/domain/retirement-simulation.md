# Deterministic retirement simulation

The simulator is a nominal, deterministic planning model. It reads portfolio data and never writes
accounting data.

## Annual funding

`recurring spending = core living expenses + discretionary spending`.

`requiredPortfolioFunding = max(recurring spending + event expenses - passive income - pension - event income, 0)`.

Passive income includes applicable rental income, PAY_OUT contractual interest, and other recurring manual
asset income. A CAPITALIZE contractual asset increases principal instead of producing same-year passive
income.

Temporary stabilization: forward retirement simulation currently uses a fixed `15,000 PLN/month`
(`180,000 PLN/year`) rental source, grown only by the configured rental-income growth rate. It does
not add Long-term Asset rental cash flows in the bridge or simulator. The PLN value is normalized
once at the forward-input boundary into canonical simulation currency. This is temporary debt:
`TODO(retirement-rental-source)` must replace it with one effective-dated source shared by Long-term
Assets, historical Planning, the bridge, and forward Simulation, with exactly-once year-boundary
handling.

For Reserve + equity harvest, the withdrawal policy funds from a manual liquid cash reserve, then
market cash, then market fixed income, and only then optional emergency equity.
Contractual bonds/deposits remain locked until maturity. Bond principal that matures becomes available
in that year before the other portfolio funding sources; unused bond principal is reinvested as a
projection-only three-year ladder bond. Deposit redemptions retain their existing cash behavior. Real
estate is not sold automatically.

`requiredPortfolioFunding = actualPortfolioWithdrawal + unfundedAmount`.

All three values are non-negative. `actualPortfolioWithdrawal` is only money actually funded under the
withdrawal policy; an equity-to-fixed-income harvest is an internal allocation transfer, not income or a
withdrawal.

After a liquidity failure, projections may continue for diagnosis. Required funding remains a need,
not a withdrawal. Lifetime actual withdrawals sum only actual portfolio withdrawals; lifetime required
funding and total unfunded amount are reported separately.

## Independent annual rates

All rates are decimal internal values and percentage-point UI values (for example, `2.5` means `0.025`).

- **Inflation** is a macro assumption for scenario comparison and interpretation. It does not itself
  grow recurring spending or rental income.
- **Rental income growth** grows `RENT`, `PARKING_RENT`, and `OTHER_INCOME` between explicit
  effective-dated periods. An explicit future period resets the amount; operating expense cash flows
  are not grown by this assumption.
- **Spending need growth** grows core and discretionary planned recurring spending. It does not grow
  actual portfolio withdrawals, which are outputs of the funding calculation.
- **Real-estate return** is retained only for compatibility with saved assumptions. It is not consumed
  by retirement simulation; rental property contributes rental cash flow only.

New plans default to inflation `2.5%`, rental income growth `2.0%`, and spending need growth `2.5%`.
They are stored independently. A saved plan created before spending growth existed initializes that
new field once from its saved inflation rate when read/migrated; it is not linked afterward.

Scenarios adjust each field independently. Conservative is inflation `+1.0pp`, cash `-0.5pp`, fixed
income `-1.0pp`, equity `-2.0pp`, other `-1.0pp`, rental growth `-0.5pp`, and
spending growth `+0.5pp`. Optimistic applies the inverse return/growth adjustments. Every effective
multiplicative scenario rate must be at least `-1`; invalid assumptions fail rather than creating negative
balances.

## Funding strategies

Legacy saved plans use **Simple waterfall**: cash, market fixed income, then equity fund a required
portfolio amount. New plans default to **Reserve + equity harvest** with a five-year reserve target,
7% harvest gate, and 75% eligible-gain fraction. It funds normal gaps from cash and market fixed
income; equity is used only when the explicit emergency-equity setting is enabled.

For Reserve + equity harvest, the safe reserve is a manual **Cash reserve** asset plus market cash
plus spendable market fixed income. A Cash reserve is a planning-only, immediately spendable manual
asset; its explicit annual return takes precedence over the scenario cash return. It is distinct from
a contractual **Deposit**, which remains locked until maturity. Locked contractual bonds/deposits are
excluded until their existing maturity redemption becomes cash.

`recurring funding gap = max(core + discretionary - passive income - pension, 0)`.

`safe reserve target = recurring funding gap × safe-reserve years`.

One-off events are excluded from this target. After same-year spending and market returns, a transfer
from equity to fixed income is allowed only when the scenario equity return meets the harvest threshold.
The reserve transfer's maximum is the lesser of the reserve shortfall, configured fraction of the
positive market-equity gain, and current equity balance. Any eligible gain left after that transfer may
refill the three-year bond ladder up to three years of recurring funding gap. Both transfers change
allocation only; neither changes net worth.

`safe reserve` is the normal reserve only. `spendable assets` are safe reserve plus equity only when
the configured strategy permits an emergency equity withdrawal. `financial assets` additionally show
equity and other financial holdings regardless of withdrawal policy. Locked contractual assets and real
estate are illiquid. Presentation must not call inaccessible equity "liquid".

For real estate, rental tax is `taxBase × taxRate` only when a tax base is explicitly configured.
A missing tax base produces no simulated rental tax; annual rent is never used as an implicit tax base.

### Bond ladder

The projection uses persisted bond maturity dates as the ladder's actual schedule. Principal is capital,
not passive income. If bond maturities in year `N` exceed the required withdrawal, the unused principal
is aggregated into one projection-only capitalizing bond maturing on December 31 of `N + 3`; its
rate is the scenario-adjusted fixed-income return. Existing bond interest and tax treatment applies
through maturity. Non-matured bonds remain locked.

```
Costs - income
      ↓
Portfolio withdrawal
      ↓
Bond maturities in N
      ↓
Use required amount
      ↓
Unused principal → projection-only bond maturing in N+3
      ↓
Other portfolio funding
      ↓
End portfolio
```

## Retirement lifecycle

`retirementAge` is an explicit transition, independent of `pensionStartAge`. For ages below
`retirementAge`, a year is `WORKING`: employment income is reported, the explicit annual
pre-retirement contribution is added to `LIQUID_CASH`, and recurring retirement spending is zero.
For ages at or above `retirementAge`, a year is `RETIRED`: employment income and contributions stop,
the configured living plus discretionary spending starts, and the normal retirement funding policy
applies. `retirementAge == currentAge` means immediate retirement.

The annual contribution is applied before that year's market returns and withdrawals. It is an
explicit portfolio contribution; employment income is not also deposited, and contributions are not
automatically allocated across buckets. The V1 destination is `LIQUID_CASH`. Spending is interpreted
as the amount needed at retirement start and grows only in later retired years using
`spendingGrowthRate`; it does not compound during working years.

Pension remains independent and starts only when `age >= pensionStartAge`, so a retirement-to-pension
bridge is funded by modeled passive income and the portfolio. One-off income and expense events still
apply by calendar year in both phases. An unfundable event can therefore fail a working-year
projection, while normal retirement funding failure begins only in the retired phase. Working years
have zero recurring funding gap and safe-reserve target; retirement years retain the existing reserve
semantics. Decision-summary reserve coverage is calculated only from retired years with a positive recurring funding
gap. If no retired year needs recurring portfolio funding, reserve coverage is not applicable rather than zero.

Saved plans created before these fields existed default to immediate retirement (`retirementAge` equal
to `currentAge`) and zero employment income and contribution, preserving their prior behavior. V1
does not model employment taxes, multiple jobs, bonuses, phased or partial-year retirement, automatic
salary-minus-expenses saving, or dynamic contribution allocation/rebalancing. Retirement timing analysis
evaluates explicit annual retirement-age candidates; it does not change the saved plan.

## Retirement timing analysis

Retirement timing analysis tests the configured Base and Conservative scenarios by changing only
`retirementAge`. Current age, end age, spending, spending growth, employment income, contribution,
pension timing and amount, return assumptions, strategy settings, and life-event calendar years stay
unchanged. The existing canonical simulator and sustainability assessment are used for every annual
candidate age.

Candidate ages are evaluated exhaustively as integer annual boundaries from `currentAge` through `endAge` for
each scenario; no candidate is skipped after an earlier sustainable age is found. Contributions and employment
income naturally stop earlier for earlier candidates, and a pension bridge naturally becomes longer; events keep
their calendar years and may therefore create non-smooth candidate results. If an earlier candidate is sustainable
while the planned age is not, the result is explicitly non-monotonic rather than being reported as a required delay.

The result distinguishes immediate retirement, earlier retirement availability, a planned-age
boundary, required delay, and no sustainable age in the horizon. Headroom is planned age minus the
earliest sustainable age; a delay is shown separately when the planned age fails. This is a
deterministic boundary under configured assumptions and is not a probability-of-success estimate or
a guarantee. It does not optimize retirement age against spending or change the saved plan.

## Planning timeline

Planning lifecycle, source provenance, temporal anchor, display-currency boundary, and the current-year
bridge are canonical in [`planning-timeline.md`](planning-timeline.md). Planning consumes simulation
results but never turns projected rows into historical facts.

Manual cash reserves are withdrawn deterministically by lowest effective return, then asset id.

## Forward consumer boundary

Scenario comparison, charts, Sustainable Spending, Sensitivity, and Retirement Timing consume the
same forward-only boundary. The live `InvestmentProfile` is bridged once for the current partial year;
rebased assumptions then begin at the first complete projected year. The canonical annual simulator
does not replay completed years, past contributions, past events, or current-year bridge effects.

## Sustainable spending analysis

Sustainable recurring spending is the highest annual value of `annualLivingExpenses` plus
`annualDiscretionaryExpenses` that remains sustainable under the existing deterministic simulator for
the configured `endAge` horizon. Candidate totals preserve the current living-to-discretionary
proportion; when the current total is zero, the candidate is assigned to living expenses and
discretionary expenses remain zero. Future one-off events are preserved unchanged and are not included
in the reported recurring-spending value.

The analysis evaluates Base and Conservative scenarios through the canonical
`RetirementSimulationService` and reuses its binary sustainability predicate: no actual funding
failure (`simulationFailed == false`). It brackets the boundary by exponential expansion and then
uses binary search to a deterministic `100` planning-unit tolerance. A zero current-spending plan has
no percentage headroom because percentage growth is undefined. This is deterministic scenario
analysis, not a statistical safe-withdrawal-rate or probability-of-success model.


## Sensitivity analysis

Sensitivity analysis is deterministic, one-variable-at-a-time analysis. It evaluates the current Base
plan once, then changes one active assumption while keeping all other assumptions and life events
unchanged. It is separate from the Base/Conservative/Optimistic scenarios, which change several
assumptions together, and separate from sustainable-spending boundary search.

Version 1 tests recurring spending at `±10%`, spending growth at `±0.5pp`, equity and fixed-income
returns at `±1pp`, rental-income growth at `±0.5pp`, pension at `±10%` when configured inside the
horizon, and safe-reserve years at
`±1 year` for Reserve + equity harvest. Drivers are omitted when their economic input is inactive;
inflation is omitted because it does not directly grow spending or rental income in the current model.

Real-estate market return is not a retirement-simulation driver. Rental-income growth remains active
when applicable; property market value remains an accounting/Long-term Assets concern.

Results rank sustainability failure and earlier failure before reserve and spendable-asset margin,
then ending wealth. A wealth-only impact is reported separately when ending wealth changes materially
but plan sustainability is unchanged. Safe-reserve policy changes are selected by the worse observed
side rather than assuming that a lower or higher target is always adverse.

This analysis does not measure probabilities, confidence intervals, or interactions between multiple
simultaneously adverse assumptions. It does not recommend spending or investment actions.
