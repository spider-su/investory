# Portfolio accounting contract

This document defines Investory's intended portfolio-level financial semantics. It is a domain
contract, not a description of whichever implementation happens to exist today.

If source code or SQL disagrees with this contract, treat the mismatch as a defect or an explicit
contract-change decision.

## Portfolio value

Current portfolio value is the latest account equity across included accounts, converted to the
portfolio base currency.

Account equity is cash plus the market value of open positions under the position settlement model.
Result-only instruments must not be valued as if full notional were owned.

## Contributions and flows

Net contributions are:

```text
external deposits - external withdrawals
```

Internal transfers and currency conversions move value between accounts/currencies but do not change
portfolio-level contributed capital.

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
