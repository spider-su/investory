# Portfolio accounting contract

This document defines Investory's intended portfolio-level financial semantics. It is a domain
contract, not a description of whichever implementation happens to exist today.

If source code or SQL disagrees with this contract, treat the mismatch as a defect or an explicit
contract-change decision.

## Portfolio value

Current portfolio value is the latest account equity across included accounts, converted to the
portfolio base currency. Accounts marked `cash_only` are excluded from portfolio totals, including
cash, market value, equity, contributions, and profit. Their account-level records remain available
for operational and account-specific views.

Account equity is cash plus the market value of open positions under the position settlement model.
Result-only instruments must not be valued as if full notional were owned.

## Contributions and flows

Net contributions are:

```text
external deposits - external withdrawals
```

Internal transfers and currency conversions move value between accounts/currencies but do not change
portfolio-level contributed capital.

The dashboard's gross deposit and withdrawal amounts start with real external flows. Transfers that
cross the tracked-account boundary are first netted together. A positive boundary net is added only
to deposits; a negative boundary net is added only to withdrawals. This preserves net contributions
without inflating both gross sides. Transfers between two tracked accounts remain excluded.

Reporting keeps three flow concepts separate:

- cash effect: every ledger operation that changes reconstructed cash;
- account funding flow: account-level allocation/funding reporting, including genuine tracked-account
  transfers and other contract-approved bookkeeping allocations;
- performance flow: only external funding and genuine tracked-account transfers that must be removed
  from account-level investment performance. `INTERNAL_BOOKKEEPING`, `FX_CONVERSION`, and ambiguous
  `CORRECTION` rows are not performance flow by default.

At portfolio scope, tracked-account performance flows cancel when both sides are in the portfolio.
Bookkeeping cash effects remain visible to cash reconstruction without becoming investment profit or
loss.

A cash movement must not become investment profit merely because it is not classified as an external
flow.

### Broker transfer identity and comparison matrix

Broker IDs in `Transfer from … to …` comments resolve by portfolio, provider and
`accounts.external_account_id`; `accounts.id` is only the internal join key. Both
materialized contribution consumers and the retained XTB diagnostic funding
calculator use that broker identity. A matching ID in another portfolio or
provider does not make the counterparty tracked.

| Operation | Cash effect | Account funding | Performance flow | Portfolio contribution |
| --- | --- | --- | --- | --- |
| External deposit/withdrawal | Signed amount | Signed amount | Signed amount | Signed amount |
| Bookkeeping transfer with both accounts tracked | Ledger amount | Directional allocation on the identified account | Zero | Zero |
| Bookkeeping transfer crossing tracked boundary | Ledger amount | Directional allocation on the identified account | Zero | Boundary inflow/outflow net, then grossed on only one side |
| Unrecognized bookkeeping note | Ledger amount | No invented allocation | Zero | No invented contribution; unmatched diagnostic retained |
| Missing required FX | Native amount remains visible | Converted amount unavailable | Converted amount unavailable | Required converted contribution unavailable |

The SQL normalization layer deliberately retains `UNMATCHED` diagnostics rather
than claiming Java transfer-pair evidence. The retained Java adjustment requires
a proven pair and remains a diagnostic fallback when canonical base-currency
funding is unavailable. Those pairing contracts are not interchangeable.

**Deferred:** the economic interpretation of `Currency conversion … from TA … to`
remains unchanged. Java classifies this hint as FX conversion while the existing
SQL raw `TRANSFER` classifier uses its existing transfer categories. This repair
does not decide that unresolved financial rule or use it to change historical
contributions. The approved correction covers explicit subaccount-transfer
identity, not a new interpretation of those FX rows.

#### Retained Java/SQL characterization

`NormalizedCashOperationRepositoryContractIT` feeds equivalent IBKR/XTB fixtures
through `CashOperationNormalizer` and the SQL reporting view. It asserts the
literal category/subtype, direction, cash and three flow amounts, and pairing
state. Classification code is unchanged by this identity-only correction; these
are retained baseline/candidate outcomes, not claims that every old difference
was repaired.

| Fixture | Java category / subtype | SQL category / subtype | Retained difference |
| --- | --- | --- | --- |
| Deposit / withdrawal | External deposit / withdrawal, raw subtype | Same | None |
| Dividend and negative reversal | Dividend / dividend reversal | Same | None |
| Positive withholding-tax reversal | Withholding-tax reversal | Same | None |
| Commission charge | Fee / FEE | Same | None |
| Positive commission refund | Fee / FEE_REVERSAL | Fee / FEE | Existing subtype difference |
| Unresolved correction | Correction / UNCLASSIFIED_CORRECTION | Same | None; no invented funding |
| Commission-refund correction | Fee / FEE_REVERSAL | Correction / UNCLASSIFIED_CORRECTION | Existing classification drift outside the authorized funding correction |
| Bond redemption | Bond redemption / BOND_REDEMPTION | Bond redemption / IBKR_BOND_REDEMPTION | Provenance subtype only |
| IBKR cash-transfer hint | Unclassified | External deposit | Existing classification drift outside the funding correction |
| IBKR forex hint | Unclassified | FX conversion | Existing classification drift outside the funding correction |
| Explicit XTB transfer | Internal transfer / XTB_ACCOUNT_TRANSFER | Internal transfer / directional XTB_ACCOUNT_TRANSFER_IN or OUT | Directional subtype only |
| Paired XTB bookkeeping | Bookkeeping, proven pair | Bookkeeping, UNMATCHED | SQL retains diagnostic non-pairing; both funding paths now yield 60 for the paired regression |
| Missing bookkeeping partner | Bookkeeping, no pair | Bookkeeping, UNMATCHED | SQL explicit allocation and paired-only Java fallback have distinct contracts |
| Deferred currency-conversion hint | FX conversion, paired across currencies | Internal transfer, UNMATCHED | Owner-deferred economic interpretation |
| Overlapping-export rows | Both already-ingested rows retained | Both rows retained | Classifiers do not perform importer identity deduplication |

The changed funding fixture has zero net cash and performance flow, account
allocation 60 instead of the prior incorrect 0, and boundary portfolio net 240
PLN at USD/PLN 4. Tracked counterparties yield zero portfolio contribution;
other providers/portfolios remain outside that boundary. The cross-currency
fixture (+100 USD / -400 PLN) conserves base cash and net flows at zero while
pinning the deferred classification/pairing difference. Source IDs remain
explicit; no normalization-layer deduplication or new financial policy is added.
The newly visible legacy classification differences above require separate
assessment; this PR does not claim to resolve them.

## Investment earnings

Headline investment earnings are composed from:

```text
realized P/L
+ unrealized P/L
+ net dividend result
+ net interest
```

Recorded dividend withholding and interest tax follow the imported ledger semantics used by the
current calculation. The capital-gains tax estimate is displayed separately and is not deducted from
headline earnings or ROI.

The Profile page consumes Investment-owned return values. Its expected annual investment result is
the investment income base multiplied by expected annual total return, including market appreciation;
it is not distributable income. Dividend, interest, and other distribution forecasts remain income
measures and are not implemented by this total-return projection.

Reporting names these concepts separately: historical TWR is realized cash-flow-neutral
performance; historical annualized TWR annualizes that observed TWR; expected annual return is the
forward estimate derived from historical annualized TWR and the benchmark assumption; expected
annual investment result is capital base multiplied by expected annual total return. Cash income is
dividends plus interest (or another explicitly distributable-income measure), and must not be used
as a name for a total-return projection.

The Profile market value is current brokerage equity, including signed cash. The Profile page's
investment-result YTD value is current-calendar-year investment profit from the canonical monthly
performance rows. It is neither income received nor a substitute for current or year-start value.
Negative brokerage cash reduces liquid assets and net worth. It is not shown as a positive asset
allocation, so the allocation reconciliation reports that liability as an unclassified delta.

Profile annual income projections combine annual Long-Term income with annualized Brokerage YTD
income when the canonical Investment income summary is unavailable. The fallback annualizes the
observed current-calendar-year period; YTD values remain separately identified. The canonical
Investment summary provides annual expected investment result and YTD investment result as separate
fields.

## Headline ROI

Headline ROI is:

```text
investment earnings / net contributions * 100
```

It is since inception, is shown as zero when net contributions are not positive, and is not TWR,
MWR, or XIRR.

## Period performance

The dashboard period selector changes period-based performance/benchmark views. It does not redefine
the current portfolio value or since-inception contribution/earnings totals.

The dashboard header's monetary investment result and adjacent percentage use the same selected
period and account scope. The amount is canonical period investment profit. The percentage is the
linked cash-flow-neutral return; it is not the amount divided by since-inception net contributions.
The fixed KPI-start return uses the same normalized daily valuation and performance-flow boundaries.
Its annualized value compounds the cumulative return over the actual observation dates.

Monthly profit must represent investment movement rather than external funding. Economic-truth
reconciliation should be able to explain equity change as flows plus investment result.

## Benchmark

The current benchmark comparison uses SPY and selected active accounts over the selected dashboard
period. It is a comparison model, not a simulation of every later cash-flow-timed SPY purchase.

## Currency

Every monetary value is converted from its declared source currency. Account currency must not be
used as an implicit substitute for an instrument, proceeds, profit, fee, or cash-operation currency.

See `docs/domain/asset-identity-and-money.md`.

FX conversion direction, stale/missing-rate handling, and completeness rules are defined in
`docs/domain/fx-normalization.md`. Authoritative totals must not silently treat an unconverted amount
as if it were already in the target currency.

## Taxes

The current capital-gains estimate follows the project's documented Polish assumptions and is an
estimate, not a tax filing.

Tax behavior that affects accounting output must be represented explicitly in tests and documentation
rather than hidden in presentation logic.

## Reconciliation invariant

For any reporting period, Investory should be able to distinguish:

```text
change in equity
= external flows
+ internal movements that net out at portfolio level
+ investment result
```

Any unexplained residual is a data-quality/reconciliation problem. See
`docs/quality/reconciliation.md`.
