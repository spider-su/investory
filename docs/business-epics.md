# Business Epics

This document is a short map of Investory business logic. It is an overview only; detailed financial semantics remain in the domain documents.

## 1. Brokerage Data Import

Bring broker data into the Investory accounting model.

Main features:
- Import IBKR CSV statements.
- Import XTB XLSX and ZIP statements.
- Detect exact duplicate files and support safe reprocessing.
- Normalize broker operations into common accounting records.
- Resolve imported symbols to canonical assets.
- Preserve import provenance and source identifiers.

## 2. Portfolio Accounting and Performance

Calculate what the brokerage portfolio owns, receives, spends, and earns.

Main features:
- Track account cash and open positions.
- Separate external contributions from internal transfers and FX conversions.
- Calculate realized and unrealized profit/loss.
- Track dividends, interest, and imported taxes.
- Calculate portfolio value, investment earnings, and ROI.
- Build daily and monthly performance history.
- Estimate current-year Polish capital-gains tax.

## 3. Market Prices and FX

Provide prices and currency conversion required by accounting and reporting.

Main features:
- Refresh market prices automatically.
- Store historical asset prices.
- Support manual price overrides and fallback prices.
- Store daily FX rates for supported currencies.
- Derive required currency cross-rates.
- Reject calculations when a safe FX rate is unavailable.

## 4. Investment Reporting

Present the current portfolio and its historical performance.

Main features:
- Show portfolio value, contributions, profit, and ROI.
- Show monthly and historical account performance.
- Compare selected accounts with SPY.
- Show performance attribution.
- Show currency exposure.
- Show current positions and account breakdowns.
- Show daily performance details.

## 5. Long-Term Assets

Manage important assets that are not represented by broker statements.

Main features:
- Track real estate values, income, expenses, tax base, and growth.
- Track bonds with interest and maturity redemption.
- Track contractual deposits with maturity.
- Track planning-only cash reserves.
- Track generic other assets.
- Support PAY_OUT and CAPITALIZE interest treatment where applicable.

## 6. Unified Investment Profile

Create one read-only financial view for planning.

Main features:
- Combine brokerage investments and long-term assets.
- Keep brokerage accounting and manual assets separate at source.
- Normalize values for planning and simulation.
- Provide the current asset base used by retirement planning.

## 7. Retirement Planning and Simulation

Test whether current and future assets can fund retirement spending.

Main features:
- Run deterministic annual retirement projections.
- Model working and retired lifecycle years.
- Model spending, inflation, income, pension, contributions, and asset returns.
- Model contractual maturity and passive income.
- Support Simple Waterfall and Reserve + Harvest funding strategies.
- Track safe reserve, portfolio withdrawals, and unfunded amounts.
- Run scenario and sensitivity analysis for major assumptions.

## 8. Rolling Planning / Plan vs Reality

Connect historical results, the current year, and future projections into one planning timeline.

Main features:
- Keep Past years as approved Actual snapshots.
- Track the Current year as Live actual versus expected.
- Keep Future years as Projected simulation results.
- Freeze approved historical planning years until reopened.
- Preserve a stable current-year expected baseline.
- Keep planning corrections separate from brokerage accounting.
- Support planning display/input currency without changing canonical storage.

## 9. Reconciliation and Data Quality

Check that imported data and derived financial results remain internally consistent.

Main features:
- Validate major stages of the accounting/reporting pipeline.
- Compare source data with normalized ledger results.
- Check derived account and portfolio values.
- Surface available portfolio data-quality indicators.
- Provide developer reconciliation and regression tooling.
