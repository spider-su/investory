package com.smartbox.investory.investment.reporting.dashboard.application;

import com.smartbox.investory.investment.accounting.PortfolioService;
import com.smartbox.investory.investment.accounting.model.models.AccountBalance;
import com.smartbox.investory.investment.accounting.model.models.Benchmark;
import com.smartbox.investory.investment.accounting.model.models.InstrumentPerformance;
import com.smartbox.investory.investment.accounting.model.models.MonthlyAttribution;
import com.smartbox.investory.investment.accounting.model.models.Performance;
import com.smartbox.investory.investment.accounting.model.models.Portfolio;
import com.smartbox.investory.investment.accounting.model.models.PortfolioDataQuality;
import com.smartbox.investory.investment.accounting.model.models.RiskExposureSummary;
import com.smartbox.investory.investment.reporting.BenchmarkService;
import com.smartbox.investory.investment.reporting.PerformanceResult;
import com.smartbox.investory.investment.reporting.PortfolioPerformanceQuery;
import com.smartbox.investory.investment.reporting.PortfolioReturnCalculator;
import com.smartbox.investory.investment.reporting.ReturnMetric;
import com.smartbox.investory.investment.reporting.dashboard.service.DashboardOperationalContextService;
import com.smartbox.investory.investment.reporting.dashboard.service.DashboardPeriod;
import com.smartbox.investory.investment.reporting.dashboard.service.DashboardPeriodFilterService;
import com.smartbox.investory.investment.reporting.dashboard.service.PortfolioStructureQuery;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DashboardFacade {

  private final PortfolioService portfolioService;
  private final BenchmarkService benchmarkService;
  private final DashboardPeriodFilterService periodFilterService;
  private final PortfolioPeriodMetricsService periodMetricsService;
  private final String performanceKpiStart;
  private final PortfolioPerformanceQuery performanceQuery;
  private final DashboardOperationalContextService operationalContextService;
  private final PortfolioStructureQuery portfolioStructureQuery;

  public DashboardFacade(
      PortfolioService portfolioService,
      BenchmarkService benchmarkService,
      DashboardPeriodFilterService periodFilterService) {
    this(
        portfolioService,
        benchmarkService,
        periodFilterService,
        new PortfolioPeriodMetricsService(),
        "2026-01-01",
        null,
        null,
        new PortfolioStructureQuery(null));
  }

  public DashboardFacade(
      PortfolioService portfolioService,
      BenchmarkService benchmarkService,
      DashboardPeriodFilterService periodFilterService,
      PortfolioPeriodMetricsService periodMetricsService,
      @Value("${app.portfolio.performance-kpi-start:2026-01-01}") String performanceKpiStart) {
    this(
        portfolioService,
        benchmarkService,
        periodFilterService,
        periodMetricsService,
        performanceKpiStart,
        null,
        null,
        new PortfolioStructureQuery(null));
  }

  @Autowired
  public DashboardFacade(
      PortfolioService portfolioService,
      BenchmarkService benchmarkService,
      DashboardPeriodFilterService periodFilterService,
      PortfolioPeriodMetricsService periodMetricsService,
      @Value("${app.portfolio.performance-kpi-start:2026-01-01}") String performanceKpiStart,
      PortfolioPerformanceQuery performanceQuery,
      DashboardOperationalContextService operationalContextService,
      PortfolioStructureQuery portfolioStructureQuery) {
    this.portfolioService = portfolioService;
    this.benchmarkService = benchmarkService;
    this.periodFilterService = periodFilterService;
    this.periodMetricsService = periodMetricsService;
    this.performanceKpiStart = yearMonth(performanceKpiStart);
    this.performanceQuery = performanceQuery;
    this.operationalContextService = operationalContextService;
    this.portfolioStructureQuery = portfolioStructureQuery;
  }

  public DashboardPageView loadDashboard(DashboardQuery query) {
    DashboardPeriod selectedPeriod = DashboardPeriod.fromUrlValue(query.period());
    Portfolio filteredPortfolio =
        DashboardCalculationCopies.portfolio(portfolioService.calculateTotalProfitLoss());
    PerformanceResult kpiPerformance =
        canonicalKpiPerformance(filteredPortfolio.getMonthlyPerformance());
    periodFilterService.apply(filteredPortfolio, selectedPeriod);
    Portfolio portfolio = DashboardCalculationCopies.portfolio(filteredPortfolio);

    Benchmark benchmark =
        query.hasExplicitAccountSelection()
            ? DashboardCalculationCopies.benchmark(benchmarkService.calculate(query.accountIds()))
            : DashboardCalculationCopies.benchmark(benchmarkService.calculate());
    periodFilterService.apply(benchmark, selectedPeriod);
    Map<Long, AccountPeriodMetrics> accountPeriodMetrics =
        accountPeriodMetrics(portfolio.getMonthlyPerformance());
    portfolio.setAccountBalances(
        periodAccountBalances(portfolio.getAccountBalances(), benchmark, accountPeriodMetrics));
    portfolio.setAccountBalancesTotal(
        periodAccountBalancesTotal(
            portfolio.getAccountBalances(),
            portfolio.getAccountBalancesTotal(),
            benchmark,
            accountPeriodMetrics));

    PerformanceResult canonical = canonicalPerformance(portfolio.getMonthlyPerformance());
    PeriodPerformance periodPerformance =
        periodPerformance(benchmark, portfolio.getMonthlyPerformance(), canonical);
    OverviewView overview =
        overview(portfolio, benchmark, periodPerformance, selectedPeriod, query.portfolioId());
    return new DashboardPageView(
        overview,
        new PerformanceView(
            benchmark(benchmark),
            performanceSummary(
                benchmark, portfolio.getMonthlyPerformance(), canonical, kpiPerformance),
            topGainers(portfolio),
            topLosers(portfolio),
            performanceKpiStart),
        overview.positions(),
        overview.cashFlow(),
        overview.riskExposure(),
        overview.dataQuality(),
        selectedPeriod,
        List.of(DashboardPeriod.values()),
        navigation(query, benchmark));
  }

  private static String yearMonth(String value) {
    if (value == null || value.isBlank()) {
      return "2026-01";
    }
    return value.length() >= 7 ? value.substring(0, 7) : value;
  }

  private DashboardNavigationView navigation(DashboardQuery query, Benchmark benchmark) {
    if (!query.hasExplicitAccountSelection()) {
      return new DashboardNavigationView(List.of());
    }
    List<Benchmark.AccountOption> options = benchmark.getAccountOptions();
    if (options == null || options.isEmpty()) {
      return new DashboardNavigationView(query.accountIds());
    }
    List<Long> eligibleIds = options.stream().map(Benchmark.AccountOption::id).toList();
    List<Long> selectedIds =
        options.stream()
            .filter(Benchmark.AccountOption::selected)
            .map(Benchmark.AccountOption::id)
            .toList();
    return new DashboardNavigationView(
        new java.util.HashSet<>(eligibleIds).equals(new java.util.HashSet<>(selectedIds))
            ? List.of()
            : selectedIds);
  }

  private PerformanceSummary performanceSummary(
      Benchmark benchmark,
      Performance performance,
      PerformanceResult canonical,
      PerformanceResult kpiPerformance) {
    String best = "—", worst = "—";
    double bestValue = 0, worstValue = 0;
    if (performance != null
        && performance.getCalculateMonthlyPerformance() != null
        && !performance.getCalculateMonthlyPerformance().isEmpty()) {
      var bestEntry =
          performance.getCalculateMonthlyPerformance().entrySet().stream()
              .max(java.util.Map.Entry.comparingByValue())
              .orElseThrow();
      var worstEntry =
          performance.getCalculateMonthlyPerformance().entrySet().stream()
              .min(java.util.Map.Entry.comparingByValue())
              .orElseThrow();
      best = bestEntry.getKey();
      bestValue = bestEntry.getValue();
      worst = worstEntry.getKey();
      worstValue = worstEntry.getValue();
    }
    return new PerformanceSummary(
        benchmark.getPortfolioReturnPct(),
        benchmark.getBenchmarkReturnPct(),
        benchmark.getAlpha(),
        benchmark.getPortfolioPl(),
        currentDrawdown(benchmark),
        maximumDrawdown(benchmark),
        best,
        bestValue,
        worst,
        worstValue,
        metric(canonical, true),
        metric(canonical, false),
        canonical == null ? null : canonical.attribution(),
        metric(kpiPerformance, true),
        kpiPerformance == null
            ? ReturnMetric.unavailable(ReturnMetric.Status.INSUFFICIENT_DATA, "No KPI history")
            : PortfolioReturnCalculator.annualized(
                metric(kpiPerformance, true),
                kpiPerformance.period().startDate(),
                kpiPerformance.period().endDate()),
        kpiPerformance == null || kpiPerformance.period() == null
            ? null
            : kpiPerformance.period().startDate().toString());
  }

  private PeriodPerformance periodPerformance(
      Benchmark benchmark, Performance performance, PerformanceResult canonical) {
    double deposits = 0.0;
    double withdrawals = 0.0;
    double netExternalFlow = 0.0;
    double realizedProfit = 0.0;
    double dividends = 0.0;
    double taxes = 0.0;
    double interest = 0.0;
    double equityTotal = 0.0;
    int equityObservations = 0;
    if (performance != null && performance.getMonthlyAttributions() != null) {
      for (MonthlyAttribution attribution : performance.getMonthlyAttributions().values()) {
        double openingEquity = attribution.openingEquity();
        double closingEquity = attribution.closingEquity();
        if (Double.isFinite(openingEquity) && Double.isFinite(closingEquity)) {
          double averageEquity = (openingEquity + closingEquity) / 2.0;
          if (averageEquity > 0.0) {
            equityTotal += averageEquity;
            equityObservations++;
          }
        }
        deposits += attribution.deposits();
        withdrawals += attribution.withdrawals();
        netExternalFlow += attribution.netExternalFlow();
        realizedProfit += attribution.realizedTradingResult();
        dividends += attribution.dividends();
        taxes += attribution.taxes();
        interest += attribution.cashInterest();
      }
    }
    if (canonical != null) {
      deposits = canonical.contributions().doubleValue();
      withdrawals = canonical.withdrawals().doubleValue();
      netExternalFlow = canonical.netExternalFlows().doubleValue();
      realizedProfit = canonical.realizedProfit().doubleValue();
      dividends = canonical.dividends().doubleValue();
      taxes = canonical.taxes().doubleValue();
      interest = canonical.interest().doubleValue();
    }
    double profit =
        benchmark.isPortfolioPerformanceAvailable()
            ? benchmark.getPortfolioPl()
            : performanceProfit(performance);
    Double returnPct =
        benchmark.isPortfolioPerformanceAvailable() ? benchmark.getPortfolioReturnPct() : null;
    return new PeriodPerformance(
        profit,
        returnPct,
        deposits,
        withdrawals,
        netExternalFlow,
        realizedProfit,
        dividends,
        periodMetricsService.signedTax(taxes),
        interest,
        equityObservations == 0 ? 0.0 : equityTotal / equityObservations);
  }

  private PerformanceResult canonicalPerformance(Performance performance) {
    if (performanceQuery == null
        || performance == null
        || performance.getCalculateMonthlyPerformance() == null
        || performance.getCalculateMonthlyPerformance().isEmpty()) {
      return null;
    }
    java.util.NavigableSet<java.time.YearMonth> months = new java.util.TreeSet<>();
    performance
        .getCalculateMonthlyPerformance()
        .keySet()
        .forEach(label -> months.add(java.time.YearMonth.parse(label)));
    return performanceQuery.forMonths(months.getFirst(), months.getLast());
  }

  private PerformanceResult canonicalKpiPerformance(Performance performance) {
    if (performanceQuery == null
        || performance == null
        || performance.getCalculateMonthlyPerformance() == null
        || performance.getCalculateMonthlyPerformance().isEmpty()) {
      return null;
    }
    YearMonth configured = YearMonth.parse(performanceKpiStart);
    YearMonth first =
        performance.getCalculateMonthlyPerformance().keySet().stream()
            .map(YearMonth::parse)
            .filter(month -> !month.isBefore(configured))
            .min(YearMonth::compareTo)
            .orElse(null);
    YearMonth last =
        performance.getCalculateMonthlyPerformance().keySet().stream()
            .map(YearMonth::parse)
            .max(YearMonth::compareTo)
            .orElse(null);
    return first == null || last == null || first.isAfter(last)
        ? null
        : performanceQuery.forMonths(first, last);
  }

  private ReturnMetric metric(PerformanceResult result, boolean twr) {
    return result == null
        ? ReturnMetric.unavailable(ReturnMetric.Status.INSUFFICIENT_DATA, "No canonical result")
        : twr ? result.timeWeightedReturn() : result.moneyWeightedReturn();
  }

  private double performanceProfit(Performance performance) {
    return performance == null ? 0.0 : performance.getTotalProfit();
  }

  private OverviewView overview(
      Portfolio portfolio,
      Benchmark benchmark,
      PeriodPerformance periodPerformance,
      DashboardPeriod selectedPeriod,
      Long portfolioId) {
    PortfolioStructureView structure = portfolioStructureQuery.load(portfolioId, portfolio);
    AssetAllocationView allocation = structure.assetAllocation();
    return new OverviewView(
        portfolio.getBalance(),
        periodPerformance.profit(),
        portfolio.getNetDeposits(),
        periodPerformance.returnPct(),
        periodPerformance.incomeTotal(),
        incomeYield(periodPerformance, portfolio),
        periodPerformance.profit(),
        crossRateToPln(portfolio, com.smartbox.investory.shared.currency.CurrencyType.EUR),
        rate(portfolio, com.smartbox.investory.shared.currency.CurrencyType.PLN),
        portfolio.getAccountBalances(),
        portfolio.getAccountBalancesTotal(),
        portfolio.getExchangeRates(),
        portfolio.getBaseCurrency(),
        cashFlow(portfolio, periodPerformance, selectedPeriod),
        positions(portfolio),
        risk(portfolio.getRiskExposure()),
        dataQuality(portfolio.getDataQuality()),
        monthlyPerformance(portfolio.getMonthlyPerformance()),
        operationalContextService == null ? null : operationalContextService.load(portfolio),
        allocation,
        structure);
  }

  private PortfolioStructureView portfolioStructure(
      Portfolio portfolio, AssetAllocationView assetAllocation) {
    return portfolioStructureQuery.load(portfolio, assetAllocation);
  }

  private Double incomeYield(PeriodPerformance periodPerformance, Portfolio portfolio) {
    return periodMetricsService.incomeYield(
        periodPerformance.incomeTotal(),
        periodPerformance.averageEquity(),
        portfolio.getNetDeposits());
  }

  private double currentDrawdown(Benchmark benchmark) {
    List<Double> curve = benchmark.getPortfolioReturnCurve();
    if (curve == null || curve.isEmpty()) return 0.0;
    double peak = 1.0;
    double current = 0.0;
    for (Double value : curve) {
      if (value == null) continue;
      double equity = 1.0 + value / 100.0;
      peak = Math.max(peak, equity);
      current = equity / peak * 100.0 - 100.0;
    }
    return current;
  }

  private double maximumDrawdown(Benchmark benchmark) {
    List<Double> curve = benchmark.getPortfolioReturnCurve();
    if (curve == null || curve.isEmpty()) return 0.0;
    double peak = 1.0;
    double maximum = 0.0;
    for (Double value : curve) {
      if (value == null) continue;
      double equity = 1.0 + value / 100.0;
      peak = Math.max(peak, equity);
      maximum = Math.min(maximum, equity / peak * 100.0 - 100.0);
    }
    return maximum;
  }

  private Double rate(
      Portfolio portfolio, com.smartbox.investory.shared.currency.CurrencyType currency) {
    return portfolio.getExchangeRates() == null ? null : portfolio.getExchangeRates().get(currency);
  }

  private Double crossRateToPln(
      Portfolio portfolio, com.smartbox.investory.shared.currency.CurrencyType currency) {
    Double pln = rate(portfolio, com.smartbox.investory.shared.currency.CurrencyType.PLN);
    Double source = rate(portfolio, currency);
    return pln == null || source == null || source == 0.0 ? null : pln / source;
  }

  private CashFlowView cashFlow(
      Portfolio portfolio, PeriodPerformance periodPerformance, DashboardPeriod selectedPeriod) {
    return new CashFlowView(
        portfolio.getDeposits(),
        portfolio.getWithdrawals(),
        portfolio.getCash(),
        periodPerformance.realizedProfit(),
        Map.of(),
        periodPerformance.dividends(),
        periodPerformance.taxes(),
        periodPerformance.interest(),
        portfolio.getCapitalGainsTax(),
        portfolio.getLossCarryForward(),
        selectedPeriod == DashboardPeriod.MAX ? portfolio.getDividendGainers() : List.of());
  }

  private PositionsView positions(Portfolio portfolio) {
    return new PositionsView(
        portfolio.getUnrealizedProfit(), portfolio.getUnrealizedByCurrency(),
        portfolio.getOpenPositionValues(), portfolio.getOpenPositionValuesTotal());
  }

  private RiskView risk(RiskExposureSummary risk) {
    RiskExposureSummary value = risk == null ? RiskExposureSummary.unavailable(0.0) : risk;
    return new RiskView(
        value.largestAssetWeightPct(),
        value.topFiveAssetConcentrationPct(),
        value.baseCurrencyAccountExposurePct(),
        value.foreignCurrencyAccountExposurePct(),
        value.availableCash(),
        value.incomeSinceInception(),
        concentrationWarning(value.topFiveAssetConcentrationPct()),
        value.periodLabel(),
        value.warnings());
  }

  private String concentrationWarning(Double topFivePct) {
    return topFivePct != null && topFivePct >= 60.0 ? "High" : null;
  }

  private DataQualityView dataQuality(PortfolioDataQuality quality) {
    PortfolioDataQuality value = quality == null ? PortfolioDataQuality.unknown() : quality;
    return new DataQualityView(
        value.state(),
        value.reconciledAccounts(),
        value.totalAccounts(),
        value.pricedOpenPositions(),
        value.totalOpenPositions(),
        value.missingPriceCount(),
        value.stalePriceCount(),
        value.proxyPriceCount(),
        value.estimatedPriceCount(),
        value.missingFxCount(),
        value.ambiguousCostBasisCurrencyCount(),
        value.unclassifiedCashOperationCount(),
        value.latestBrokerReconciliationAt(),
        value.latestImportAt(),
        value.latestPriceDate(),
        value.latestFxMonth(),
        value.latestReportingRefreshAt(),
        value.issues());
  }

  private MonthlyPerformanceView monthlyPerformance(Performance performance) {
    return performance == null
        ? new MonthlyPerformanceView(null, null, null, null)
        : new MonthlyPerformanceView(
            performance.getCalculateMonthlyPerformance(), performance.getMonthlyOperationsCount(),
            performance.getMonthlyCashflow(), performance.getMonthlyAttributions());
  }

  private BenchmarkView benchmark(Benchmark benchmark) {
    return new BenchmarkView(
        benchmark.isPortfolioPerformanceAvailable(),
        benchmark.isPortfolioPerformanceAvailable(),
        benchmark.isBenchmarkAvailable(),
        benchmark.getSymbol(),
        benchmark.getLabels(),
        benchmark.getPortfolioCurve(),
        benchmark.getBenchmarkCurve(),
        benchmark.getPortfolioReturnCurve(),
        benchmark.getBenchmarkReturnCurve(),
        benchmark.getInvestedCapital(),
        benchmark.getPortfolioPl(),
        benchmark.getBenchmarkPl(),
        benchmark.getPortfolioReturnPct(),
        benchmark.getBenchmarkReturnPct(),
        benchmark.getAlpha(),
        benchmark.getAccountOptions(),
        benchmark.getAccountSeries(),
        benchmark.isAccountValuesAvailable(),
        benchmark.getSelectedAccountValueYear(),
        benchmark.getAccountValueYears());
  }

  private List<InstrumentPerformance> topGainers(Portfolio portfolio) {
    return top(portfolio, true);
  }

  private List<AccountBalance> periodAccountBalances(
      List<AccountBalance> accounts,
      Benchmark benchmark,
      Map<Long, AccountPeriodMetrics> accountPeriodMetrics) {
    if (accounts == null) {
      return List.of();
    }
    Map<Long, Benchmark.AccountSeries> seriesByAccount =
        benchmark.getAccountSeries().stream()
            .collect(java.util.stream.Collectors.toMap(Benchmark.AccountSeries::id, s -> s));
    return accounts.stream()
        .map(
            account -> {
              Benchmark.AccountSeries series = seriesByAccount.get(account.getAccountId());
              AccountPeriodMetrics attributionMetrics =
                  accountPeriodMetrics.get(account.getAccountId());
              Double periodProfit =
                  attributionMetrics != null && attributionMetrics.hasHistory()
                      ? attributionMetrics.profit
                      : series != null && !series.portfolioCurve().isEmpty()
                          ? series.portfolioPl()
                          : null;
              Double periodReturn = series == null ? null : returnPercent(series);
              Double localProfit = account.getLocalProfit();
              return new AccountBalance(
                  account.getAccountId(),
                  account.getAccountName(),
                  account.getNetDeposit(),
                  account.getBaseNetDeposit(),
                  periodProfit,
                  localProfit,
                  periodReturn,
                  account.getBalance(),
                  account.getCash(),
                  account.getLocalCurrency(),
                  account.getLocalBalance(),
                  account.getLocalCash());
            })
        .toList();
  }

  private AccountBalance periodAccountBalancesTotal(
      List<AccountBalance> accounts,
      AccountBalance total,
      Benchmark benchmark,
      Map<Long, AccountPeriodMetrics> accountPeriodMetrics) {
    if (total == null) {
      return null;
    }
    double periodProfit =
        benchmark.isPortfolioPerformanceAvailable()
            ? benchmark.getPortfolioPl()
            : accounts.stream()
                .mapToDouble(account -> account.getProfit() == null ? 0.0 : account.getProfit())
                .sum();
    Double periodReturn = returnPercent(benchmark);
    Double localProfit = null;
    return new AccountBalance(
        total.getAccountId(),
        total.getAccountName(),
        total.getNetDeposit(),
        total.getBaseNetDeposit(),
        periodProfit,
        localProfit,
        periodReturn,
        total.getBalance(),
        total.getCash(),
        total.getLocalCurrency(),
        total.getLocalBalance(),
        total.getLocalCash());
  }

  private Double returnPercent(Benchmark.AccountSeries series) {
    return series == null
        ? null
        : periodMetricsService.compoundAccountReturn(series.returnPctCurve());
  }

  private Double returnPercent(Benchmark benchmark) {
    return benchmark.getPortfolioReturnCurve() == null
            || benchmark.getPortfolioReturnCurve().isEmpty()
        ? null
        : benchmark.getPortfolioReturnPct();
  }

  private Map<Long, AccountPeriodMetrics> accountPeriodMetrics(Performance performance) {
    Map<Long, AccountPeriodMetrics> metrics = new LinkedHashMap<>();
    if (performance == null || performance.getMonthlyAttributions() == null) {
      return metrics;
    }
    performance
        .getMonthlyAttributions()
        .forEach(
            (period, attribution) -> {
              if (attribution.accounts() == null) {
                return;
              }
              attribution
                  .accounts()
                  .forEach(
                      contribution -> {
                        try {
                          Long accountId = Long.valueOf(contribution.accountId());
                          AccountPeriodMetrics metric =
                              metrics.computeIfAbsent(
                                  accountId, ignored -> new AccountPeriodMetrics());
                          boolean validObservation =
                              Math.abs(contribution.openingValue()) > 0.005
                                  || Math.abs(contribution.closingValue()) > 0.005;
                          if (!metric.started && !validObservation) {
                            return;
                          }
                          metric.started = true;
                          metric.profit += contribution.monthlyProfit();
                        } catch (NumberFormatException ignored) {
                          // Non-account attribution labels cannot identify a popup row.
                        }
                      });
            });
    return metrics;
  }

  private static final class AccountPeriodMetrics {
    private double profit;
    private boolean started;

    private boolean hasHistory() {
      return started;
    }
  }

  private double round(double value) {
    return Math.round(value * 100.0) / 100.0;
  }

  private List<InstrumentPerformance> topLosers(Portfolio portfolio) {
    return top(portfolio, false);
  }

  private List<InstrumentPerformance> top(Portfolio portfolio, boolean gainers) {
    if (portfolio.getPerformancePerSymbol() == null) {
      return List.of();
    }
    Comparator<InstrumentPerformance> order =
        Comparator.comparingDouble(InstrumentPerformance::getTotal);
    return portfolio.getPerformancePerSymbol().stream()
        .filter(row -> gainers ? row.getTotal() >= 0 : row.getTotal() < 0)
        .sorted(gainers ? order.reversed() : order)
        .limit(10)
        .toList();
  }

  private record PeriodPerformance(
      double profit,
      Double returnPct,
      double deposits,
      double withdrawals,
      double netExternalFlow,
      double realizedProfit,
      double dividends,
      double taxes,
      double interest,
      double averageEquity) {

    double incomeTotal() {
      return dividends + taxes + interest;
    }
  }
}
