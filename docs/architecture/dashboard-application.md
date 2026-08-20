# Dashboard application boundary

The server-rendered dashboard enters through `investment.reporting.dashboard.application.DashboardFacade`. `HomeController` maps request
parameters to `DashboardQuery` and adds the resulting `DashboardPageView` plus period navigation
metadata to the Thymeleaf model.

`DashboardFacade` coordinates the existing `PortfolioService`, `BenchmarkService`, and
`DashboardPeriodFilterService`. It does not recalculate reporting values. The facade maps those
results into immutable section view models: overview, performance, positions, cash flow, risk, and
data quality. Financial truth remains in the existing portfolio services and the `account_daily`
reporting pipeline.

The dashboard facade and view models live in `investment.reporting.dashboard.application`; supporting
queries and mappers live in `investment.reporting.dashboard.service`. Controllers retain their existing
routes while package relocation is completed mechanically.

Initial rendering remains server-side Thymeleaf. A small presentation alias in `dashboard.html`
keeps existing expressions stable while the page is incrementally split into section-heading
fragments. Chart data is still injected by Thymeleaf because Chart.js needs the prepared server
result. Chart rendering remains in the Thymeleaf-inlined Chart.js block; actions, core page state,
formatting support, and accessibility behavior are split into static dashboard modules.
The current static modules are `dashboard.js`, `dashboard-actions.js`, `dashboard-core.js`, and
`dashboard-accessibility.js`; the page does not introduce a frontend framework or a broad dashboard
API.

The current section records are `OverviewView`, `PerformanceView`, `PositionsView`, `CashFlowView`,
`RiskView`, and `DataQualityView`, composed by `DashboardPageView`. The records contain prepared
financial values needed by the template and charts, while calculations that define financial meaning
remain in the existing portfolio and reporting services.

The internal dashboard application models are presentation models, separate from persistence and
reporting contracts. Existing command routes and the daily attribution interaction remain unchanged.

## Portfolio benchmark return contract

The dashboard benchmark uses two distinct measures. Absolute account curves contain cumulative
monthly `account_monthly_mv.total_profit` in the portfolio base currency and are used for P/L
amount reconciliation. Percentage curves are cumulative flow-adjusted returns: each monthly
`compounded_monthly_return` is weighted by that account/month opening equity, then linked as
`(1 + r1) * (1 + r2) - 1`. Deposits, withdrawals, internal transfers, and FX conversions are not
performance. Selected-account filtering is applied before numerator and opening-equity weighting.

The benchmark baseline is the first available SPY monthly close immediately before the first
portfolio comparison label. SPY is therefore a price-return series unless the stored provider
series is explicitly documented otherwise. Both portfolio monetary inputs and the benchmark
amount curves use the portfolio base currency; account-native PLN values are converted by the
reporting MV before ratios are formed.

The account summary percentage remains simple return on its accounting net-deposit denominator.
It is intentionally not required to equal the flow-adjusted benchmark return when external flows
occur during the selected period.
