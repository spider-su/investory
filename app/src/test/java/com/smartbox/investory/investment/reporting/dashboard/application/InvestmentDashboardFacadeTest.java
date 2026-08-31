package com.smartbox.investory.investment.reporting.dashboard.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi.DashboardPageView;
import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi.DashboardQuery;
import com.smartbox.investory.investment.api.reporting.model.AccountBalance;
import com.smartbox.investory.investment.api.reporting.model.AssetAllocationView;
import com.smartbox.investory.investment.api.reporting.model.Benchmark;
import com.smartbox.investory.investment.api.reporting.model.DividendGainer;
import com.smartbox.investory.investment.api.reporting.model.InstrumentPerformance;
import com.smartbox.investory.investment.api.reporting.model.MonthlyAttribution;
import com.smartbox.investory.investment.api.reporting.model.OpenPositionValue;
import com.smartbox.investory.investment.api.reporting.model.PerformanceAttribution;
import com.smartbox.investory.investment.api.reporting.model.PortfolioStructureView;
import com.smartbox.investory.investment.api.reporting.model.ReturnMetric;
import com.smartbox.investory.investment.performance.PortfolioMetricsService;
import com.smartbox.investory.investment.performance.model.Performance;
import com.smartbox.investory.investment.performance.model.Portfolio;
import com.smartbox.investory.investment.performance.model.RiskExposureSummary;
import com.smartbox.investory.investment.reporting.BenchmarkService;
import com.smartbox.investory.investment.reporting.PerformancePeriod;
import com.smartbox.investory.investment.reporting.PerformanceResult;
import com.smartbox.investory.investment.reporting.PortfolioPerformanceQuery;
import com.smartbox.investory.investment.reporting.dashboard.service.DashboardPeriodFilterService;
import com.smartbox.investory.investment.reporting.dashboard.service.PortfolioStructureQuery;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Dashboard Facade")
class InvestmentDashboardFacadeTest {

  @Mock private PortfolioMetricsService portfolioMetricsService;
  @Mock private BenchmarkService benchmarkService;
  @Mock private DashboardPeriodFilterService periodFilterService;

  @BeforeEach
  void returnFilteredCopiesAsExpected() {
    lenient()
        .when(periodFilterService.filter(any(Portfolio.class), any(DashboardPeriod.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    lenient()
        .when(periodFilterService.filter(any(Benchmark.class), any(DashboardPeriod.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @DisplayName("composes Filtered Portfolio And Benchmark Into Dashboard Sections")
  @Test
  void composesFilteredPortfolioAndBenchmarkIntoDashboardSections() {
    Portfolio portfolio = new Portfolio();
    portfolio.setBalance(123.0);
    portfolio.setNetDeposits(100.0);
    portfolio.setPerformancePerSymbol(
        List.of(
            new InstrumentPerformance("GAIN", 4.0, 0.0, 4.0),
            new InstrumentPerformance("LOSS", -2.0, 0.0, -2.0)));
    portfolio.setDividendGainers(List.of(new DividendGainer("AAPL.US", 42.0)));
    Benchmark benchmark = new Benchmark();
    when(portfolioMetricsService.calculateTotalProfitLoss()).thenReturn(portfolio);
    when(benchmarkService.calculate()).thenReturn(benchmark);

    DashboardPageView result =
        new InvestmentDashboardFacade(
                portfolioMetricsService, benchmarkService, periodFilterService)
            .loadDashboard(new DashboardQuery(List.of(), false, DashboardPeriod.ONE_YEAR, 1L));

    assertEquals(123.0, result.overview().balance());
    assertEquals(100.0, result.overview().netDeposits());
    assertEquals("GAIN", result.performance().topGainers().getFirst().getSymbol());
    assertEquals("LOSS", result.performance().topLosers().getFirst().getSymbol());
    assertSame(result.overview().positions(), result.positions());
    assertEquals(DashboardPeriod.ONE_YEAR, result.selectedPeriod());
    assertEquals(List.of(new DividendGainer("AAPL.US", 42.0)), result.overview().dividendGainers());
    assertEquals("2026-01", result.performance().kpiStart());
    verify(periodFilterService).filter(portfolio, DashboardPeriod.ONE_YEAR);
    verify(periodFilterService).filter(benchmark, DashboardPeriod.ONE_YEAR);
  }

  @DisplayName("attribution Keeps Fourteen Named Rows And Aggregates The Rest Per Result Side")
  @Test
  void attributionKeepsFourteenNamedRowsAndAggregatesTheRestPerResultSide() {
    Portfolio portfolio = new Portfolio();
    List<InstrumentPerformance> rows = new ArrayList<>();
    for (int value = 1; value <= 16; value++) {
      rows.add(new InstrumentPerformance("GAIN-" + value, value, 0.0, value));
      rows.add(new InstrumentPerformance("LOSS-" + value, -value, 0.0, -value));
    }
    portfolio.setPerformancePerSymbol(rows);
    when(portfolioMetricsService.calculateTotalProfitLoss()).thenReturn(portfolio);
    when(benchmarkService.calculate()).thenReturn(new Benchmark());

    DashboardPageView result =
        new InvestmentDashboardFacade(
                portfolioMetricsService, benchmarkService, periodFilterService)
            .loadDashboard(new DashboardQuery(List.of(), false, DashboardPeriod.MAX, 1L));

    assertEquals(15, result.performance().topGainers().size());
    assertEquals("GAIN-16", result.performance().topGainers().getFirst().getSymbol());
    assertEquals("Other", result.performance().topGainers().getLast().getSymbol());
    assertEquals(3.0, result.performance().topGainers().getLast().getTotal(), 0.001);
    assertEquals(15, result.performance().topLosers().size());
    assertEquals("LOSS-16", result.performance().topLosers().getFirst().getSymbol());
    assertEquals("Other", result.performance().topLosers().getLast().getSymbol());
    assertEquals(-3.0, result.performance().topLosers().getLast().getTotal(), 0.001);
  }

  @DisplayName("portfolio Structure Aggregates Duplicate Symbols And Account Currencies")
  @Test
  void portfolioStructureAggregatesDuplicateSymbolsAndAccountCurrencies() throws Exception {
    Portfolio portfolio = new Portfolio();
    portfolio.setBalance(700.0);
    portfolio.setCash(100.0);
    portfolio.setOpenPositionValues(
        List.of(
            new OpenPositionValue(
                "VWRA",
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                CurrencyType.USD,
                BigDecimal.valueOf(300),
                BigDecimal.valueOf(30),
                null,
                CurrencyType.USD,
                BigDecimal.ZERO,
                null),
            new OpenPositionValue(
                "VWRA",
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                CurrencyType.USD,
                BigDecimal.valueOf(200),
                BigDecimal.valueOf(20),
                null,
                CurrencyType.USD,
                BigDecimal.ZERO,
                null),
            new OpenPositionValue(
                "SMH",
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                CurrencyType.USD,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(10),
                null,
                CurrencyType.USD,
                BigDecimal.ZERO,
                null)));
    portfolio.setAccountBalances(
        List.of(
            new AccountBalance(
                1L, "USD one", 0d, 0d, 0d, 0d, 0d, 350d, 50d, CurrencyType.USD, 350d, 50d),
            new AccountBalance(
                2L, "USD two", 0d, 0d, 0d, 0d, 0d, 210d, 50d, CurrencyType.USD, 210d, 50d),
            new AccountBalance(
                3L, "PLN", 0d, 0d, 0d, 0d, 0d, 140d, 0d, CurrencyType.PLN, 140d, 0d)));
    portfolio.setAccountBalancesTotal(new AccountBalance());
    var facade =
        new InvestmentDashboardFacade(
            portfolioMetricsService, benchmarkService, periodFilterService);
    var method =
        InvestmentDashboardFacade.class.getDeclaredMethod(
            "portfolioStructure", Portfolio.class, AssetAllocationView.class);
    method.setAccessible(true);
    var structure =
        (PortfolioStructureView)
            method.invoke(facade, portfolio, new AssetAllocationView(700.0, List.of()));
    assertEquals("VWRA", structure.largestHolding().symbol());
    assertEquals(500.0, structure.largestHolding().value());
    assertEquals(85.714285, structure.topFiveWeightPct(), 0.000001);
    assertEquals(
        100.0,
        structure.cashWeightPct()
            + structure.topHoldings().stream()
                .mapToDouble(PortfolioStructureView.Holding::weightPct)
                .sum());
    assertEquals(2, structure.accountCurrencies().size());
    assertEquals(CurrencyType.USD, structure.accountCurrencies().getFirst().currency());
    assertEquals(80.0, structure.accountCurrencies().getFirst().weightPct());
  }

  @DisplayName("exposes Configured Performance Board Kpi Start")
  @Test
  void exposesConfiguredPerformanceBoardKpiStart() {
    Portfolio portfolio = new Portfolio();
    Benchmark benchmark = new Benchmark();
    when(portfolioMetricsService.calculateTotalProfitLoss()).thenReturn(portfolio);
    when(benchmarkService.calculate()).thenReturn(benchmark);

    DashboardPageView result =
        new InvestmentDashboardFacade(
                portfolioMetricsService,
                benchmarkService,
                periodFilterService,
                new PortfolioPeriodMetricsService(),
                "2027-03-01")
            .loadDashboard(new DashboardQuery(List.of(), false, DashboardPeriod.MAX, 1L));

    assertEquals("2027-03", result.performance().kpiStart());
  }

  @DisplayName("maps Submitted Benchmark Account Selection To Benchmark Service")
  @Test
  void mapsSubmittedBenchmarkAccountSelectionToBenchmarkService() {
    Portfolio portfolio = new Portfolio();
    Benchmark benchmark = new Benchmark();
    List<Long> accountIds = List.of(11L, 12L);
    benchmark.setAccountOptions(
        List.of(
            new Benchmark.AccountOption(11L, "One", true),
            new Benchmark.AccountOption(12L, "Two", true),
            new Benchmark.AccountOption(13L, "Three", false)));
    when(portfolioMetricsService.calculateTotalProfitLoss()).thenReturn(portfolio);
    when(benchmarkService.calculate(accountIds)).thenReturn(benchmark);

    DashboardPageView result =
        new InvestmentDashboardFacade(
                portfolioMetricsService, benchmarkService, periodFilterService)
            .loadDashboard(new DashboardQuery(accountIds, true, DashboardPeriod.YEAR_TO_DATE, 1L));

    verify(benchmarkService).calculate(accountIds);
    assertEquals(
        "/dashboard?period=YTD&benchmarkAccountsSubmitted=true&accountIds=11&accountIds=12",
        result.navigation().periodUrl(DashboardPeriod.YEAR_TO_DATE));
  }

  @DisplayName("treats An Empty Submitted Account Selection As All Eligible Accounts")
  @Test
  void treatsAnEmptySubmittedAccountSelectionAsAllEligibleAccounts() {
    Portfolio portfolio = new Portfolio();
    Benchmark benchmark = new Benchmark();
    benchmark.setPortfolioPerformanceAvailable(true);
    benchmark.setAccountOptions(
        List.of(
            new Benchmark.AccountOption(1L, "One", true),
            new Benchmark.AccountOption(3L, "Three", true)));
    when(portfolioMetricsService.calculateTotalProfitLoss()).thenReturn(portfolio);
    when(benchmarkService.calculate()).thenReturn(benchmark);

    DashboardPageView result =
        new InvestmentDashboardFacade(
                portfolioMetricsService, benchmarkService, periodFilterService)
            .loadDashboard(new DashboardQuery(List.of(), true, DashboardPeriod.YEAR_TO_DATE, 1L));

    verify(benchmarkService).calculate();
    assertTrue(result.performance().benchmark().available());
    assertTrue(
        result.performance().benchmark().accountOptions().stream()
            .allMatch(Benchmark.AccountOption::selected));
    assertEquals(
        "/dashboard?period=YTD", result.navigation().periodUrl(DashboardPeriod.YEAR_TO_DATE));
  }

  @DisplayName("does Not Keep Redundant Account Ids In Period Links When All Accounts Are Selected")
  @Test
  void doesNotKeepRedundantAccountIdsInPeriodLinksWhenAllAccountsAreSelected() {
    Portfolio portfolio = new Portfolio();
    Benchmark benchmark = new Benchmark();
    List<Long> accountIds = List.of(1L, 3L);
    benchmark.setAccountOptions(
        List.of(
            new Benchmark.AccountOption(1L, "One", true),
            new Benchmark.AccountOption(3L, "Three", true)));
    when(portfolioMetricsService.calculateTotalProfitLoss()).thenReturn(portfolio);
    when(benchmarkService.calculate(accountIds)).thenReturn(benchmark);

    DashboardPageView result =
        new InvestmentDashboardFacade(
                portfolioMetricsService, benchmarkService, periodFilterService)
            .loadDashboard(new DashboardQuery(accountIds, true, DashboardPeriod.YEAR_TO_DATE, 1L));

    assertEquals(
        "/dashboard?period=YTD", result.navigation().periodUrl(DashboardPeriod.YEAR_TO_DATE));
  }

  @DisplayName("uses Scoped Performance Values Instead Of All Time Portfolio Totals")
  @Test
  void usesScopedPerformanceValuesInsteadOfAllTimePortfolioTotals() {
    YearMonth current = YearMonth.now();
    List<String> labels =
        List.of(
            current.minusMonths(3).toString(),
            current.minusMonths(2).toString(),
            current.minusMonths(1).toString(),
            current.toString());
    Portfolio portfolio = new Portfolio();
    portfolio.setBalance(900.0);
    portfolio.setTotalProfit(9_999.0);
    portfolio.setNetDeposits(8_888.0);
    Performance performance = new Performance();
    LinkedHashMap<String, Double> monthlyProfit = new LinkedHashMap<>();
    LinkedHashMap<String, MonthlyAttribution> attributions = new LinkedHashMap<>();
    labels.forEach(
        label -> {
          monthlyProfit.put(label, 100.0);
          attributions.put(label, attribution(label));
        });
    performance.setCalculateMonthlyPerformance(monthlyProfit);
    performance.setMonthlyAttributions(attributions);
    portfolio.setMonthlyPerformance(performance);
    Benchmark benchmark = new Benchmark();
    benchmark.setAvailable(true);
    benchmark.setLabels(labels);
    benchmark.setPortfolioCurve(List.of(100.0, 200.0, 300.0, 400.0));
    benchmark.setBenchmarkCurve(List.of(50.0, 100.0, 150.0, 200.0));
    benchmark.setPortfolioReturnCurve(List.of(2.0, 4.04, 6.12, 8.24));
    benchmark.setBenchmarkReturnCurve(List.of(1.0, 2.01, 3.03, 4.06));
    benchmark.setInvestedCapital(1_000.0);
    when(portfolioMetricsService.calculateTotalProfitLoss()).thenReturn(portfolio);
    when(benchmarkService.calculate()).thenReturn(benchmark);

    DashboardPageView result =
        new InvestmentDashboardFacade(
                portfolioMetricsService, benchmarkService, new DashboardPeriodFilterService())
            .loadDashboard(new DashboardQuery(List.of(), false, DashboardPeriod.ONE_MONTH, 1L));

    assertEquals(900.0, result.overview().balance());
    assertEquals(200.0, result.overview().totalProfit());
    assertEquals(4.04, result.overview().gainPct());
    assertEquals(8_888.0, result.overview().netDeposits());
    assertEquals(0.0, result.overview().deposits());
    assertEquals(0.0, result.overview().withdrawals());
    assertEquals(200.0, result.performance().summary().portfolioPl());
    assertEquals(2, result.performance().benchmark().labels().size());
  }

  @DisplayName("uses Canonical Selected Period Profit And Return For The Headline")
  @Test
  void usesCanonicalSelectedPeriodProfitAndReturnForTheHeadline() {
    Portfolio portfolio = new Portfolio();
    Performance performance = new Performance();
    performance.setCalculateMonthlyPerformance(
        new LinkedHashMap<>(java.util.Map.of("2026-01", 10.0, "2026-02", 20.0)));
    portfolio.setMonthlyPerformance(performance);

    Benchmark benchmark = new Benchmark();
    benchmark.setPortfolioPerformanceAvailable(true);
    benchmark.setPortfolioPl(999.0);
    benchmark.setPortfolioReturnPct(88.0);
    when(portfolioMetricsService.calculateTotalProfitLoss()).thenReturn(portfolio);
    when(benchmarkService.calculate()).thenReturn(benchmark);

    PortfolioPerformanceQuery performanceQuery = mock(PortfolioPerformanceQuery.class);
    PerformanceResult canonical =
        new PerformanceResult(
            new PerformancePeriod(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-02-28")),
            CurrencyType.USD,
            new BigDecimal("1000"),
            new BigDecimal("1198.45"),
            new BigDecimal("100"),
            BigDecimal.ZERO,
            new BigDecimal("100"),
            new BigDecimal("98.45"),
            BigDecimal.ZERO,
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("0.0725"),
            ReturnMetric.available(new BigDecimal("0.0725")),
            ReturnMetric.available(new BigDecimal("0.08")),
            new PerformanceAttribution(
                BigDecimal.ZERO,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                true,
                false));
    when(performanceQuery.forPortfolioMonths(
            1L, YearMonth.parse("2026-01"), YearMonth.parse("2026-02")))
        .thenReturn(canonical);

    InvestmentDashboardFacade facade =
        new InvestmentDashboardFacade(
            portfolioMetricsService,
            benchmarkService,
            new DashboardPeriodFilterService(),
            new PortfolioPeriodMetricsService(),
            "2026-01-01",
            performanceQuery,
            null,
            new PortfolioStructureQuery(null));
    DashboardPageView result =
        facade.loadDashboard(new DashboardQuery(List.of(), false, DashboardPeriod.MAX, 1L));
    InvestmentDashboardFacade.PerformanceKpi profileKpi = facade.loadPerformanceKpi(1L);

    assertEquals(98.45, result.overview().totalProfit(), 0.001);
    assertEquals(7.25, result.overview().gainPct(), 0.001);
    assertEquals(new BigDecimal("0.0725"), result.performance().summary().kpiReturn().value());
    assertEquals(result.performance().summary().annualizedReturn(), profileKpi.annualizedReturn());
    assertEquals(result.performance().summary().kpiStartDate(), profileKpi.startDate());
  }

  @DisplayName("calculates Drawdown Income Yield And Concentration Warning")
  @Test
  void calculatesDrawdownIncomeYieldAndConcentrationWarning() {
    Portfolio portfolio = new Portfolio();
    portfolio.setNetDeposits(1_000.0);
    Performance performance = new Performance();
    performance.setMonthlyAttributions(
        new LinkedHashMap<>(java.util.Map.of("2026-01", attributionWithIncome())));
    portfolio.setMonthlyPerformance(performance);
    portfolio.setRiskExposure(
        new RiskExposureSummary(18.4, 72.0, 27.0, 73.0, 80.0, 23.0, "Current snapshot", List.of()));
    Benchmark benchmark = new Benchmark();
    benchmark.setAvailable(true);
    benchmark.setInvestedCapital(1_000.0);
    benchmark.setPortfolioReturnCurve(List.of(10.0, 5.0, 12.0));
    benchmark.setPortfolioPl(120.0);
    when(portfolioMetricsService.calculateTotalProfitLoss()).thenReturn(portfolio);
    when(benchmarkService.calculate()).thenReturn(benchmark);

    DashboardPageView result =
        new InvestmentDashboardFacade(
                portfolioMetricsService, benchmarkService, new DashboardPeriodFilterService())
            .loadDashboard(new DashboardQuery(List.of(), false, DashboardPeriod.MAX, 1L));

    assertEquals(0.0, result.performance().summary().currentDrawdownPct());
    assertEquals(-4.545, result.performance().summary().maxDrawdownPct(), 0.001);
    assertEquals(2.3, result.overview().incomeYieldPct(), 0.03);
    assertEquals("High", result.overview().riskExposure().concentrationWarning());
  }

  @DisplayName(
      "keeps Snapshot Metrics All Time While Scoping Profit Realized And Income To The Period")
  @Test
  void keepsSnapshotMetricsAllTimeWhileScopingProfitRealizedAndIncomeToThePeriod() {
    YearMonth now = YearMonth.now();
    List<String> labels =
        List.of(
            now.minusMonths(4).toString(),
            now.minusMonths(3).toString(),
            now.minusMonths(1).toString(),
            now.toString());
    Portfolio portfolio = new Portfolio();
    portfolio.setBalance(1_500.0);
    portfolio.setNetDeposits(1_000.0);
    portfolio.setDeposits(1_200.0);
    portfolio.setWithdrawals(200.0);
    portfolio.setUnrealizedProfit(250.0);
    portfolio.setRealizedProfit(9_999.0);
    portfolio.setDividends(8_888.0);
    portfolio.setDividendTax(-777.0);
    portfolio.setInterest(666.0);
    Performance performance = new Performance();
    LinkedHashMap<String, Double> monthlyProfit = new LinkedHashMap<>();
    LinkedHashMap<String, MonthlyAttribution> attributions = new LinkedHashMap<>();
    addPeriod(labels, monthlyProfit, attributions, 0, 10.0, 10.0, 1.0, -1.0, 1.0, 1_000.0);
    addPeriod(labels, monthlyProfit, attributions, 1, 20.0, 20.0, 2.0, -2.0, 2.0, 1_100.0);
    addPeriod(labels, monthlyProfit, attributions, 2, 30.0, 30.0, 3.0, -3.0, 3.0, 1_300.0);
    addPeriod(labels, monthlyProfit, attributions, 3, 40.0, 40.0, 4.0, -4.0, 4.0, 1_600.0);
    performance.setCalculateMonthlyPerformance(monthlyProfit);
    performance.setMonthlyAttributions(attributions);
    portfolio.setMonthlyPerformance(performance);
    Benchmark benchmark = benchmark(labels);
    when(portfolioMetricsService.calculateTotalProfitLoss()).thenReturn(portfolio);
    when(benchmarkService.calculate()).thenReturn(benchmark);

    InvestmentDashboardFacade facade =
        new InvestmentDashboardFacade(
            portfolioMetricsService, benchmarkService, new DashboardPeriodFilterService());
    DashboardPageView oneMonth =
        facade.loadDashboard(new DashboardQuery(List.of(), false, DashboardPeriod.ONE_MONTH, 1L));
    DashboardPageView threeMonths =
        facade.loadDashboard(
            new DashboardQuery(List.of(), false, DashboardPeriod.THREE_MONTHS, 1L));
    DashboardPageView max =
        facade.loadDashboard(new DashboardQuery(List.of(), false, DashboardPeriod.MAX, 1L));

    assertSnapshotMetrics(oneMonth);
    assertSnapshotMetrics(threeMonths);
    assertSnapshotMetrics(max);
    assertNotEquals(oneMonth.overview().totalProfit(), threeMonths.overview().totalProfit());
    assertNotEquals(oneMonth.overview().realizedProfit(), threeMonths.overview().realizedProfit());
    assertNotEquals(oneMonth.overview().incomeTotal(), threeMonths.overview().incomeTotal());
    assertEquals(oneMonth.overview().totalProfit(), oneMonth.performance().summary().portfolioPl());
    assertEquals(
        threeMonths.overview().totalProfit(), threeMonths.performance().summary().portfolioPl());
    assertEquals(100.0, max.overview().realizedProfit());
    assertEquals(10.0, max.overview().incomeTotal());
  }

  @DisplayName("dashboard Cards Declare Current And Selected Period Semantics")
  @Test
  void dashboardCardsDeclareCurrentAndSelectedPeriodSemantics() throws Exception {
    try (var template = getClass().getResourceAsStream("/templates/dashboard.html")) {
      assertTrue(template != null, "dashboard template must be present");
      String html = new String(template.readAllBytes(), StandardCharsets.UTF_8);

      assertTrue(html.contains("Open positions · before tax"));
      assertFalse(html.contains("Dividends &amp; cash interest"));
      assertTrue(html.contains("Dividends"));
      assertTrue(html.contains("stats.formatBase(stats.unrealizedProfit)"));
      assertTrue(html.contains("stats.formatBase(stats.realizedProfit)"));
      assertTrue(html.contains("stats.formatBase(stats.incomeTotal)"));
      assertTrue(html.contains("selectedPeriod.label() + ' · before tax'"));
      assertFalse(html.contains("By currency"));
      assertFalse(html.contains("realizedByCurrency"));
      assertFalse(html.contains("unrealizedByCurrency"));
      assertFalse(html.contains("selectedPeriod.label() + ' · before capital-gains tax'"));
      assertTrue(html.contains("after withholding tax · yield"));
      assertTrue(
          html.contains(
              "selectedPeriod == T(com.smartbox.investory.investment.api.reporting.DashboardPeriod).MAX"));
      assertTrue(
          html.contains("fragments/app-header :: appNavigation('investment', ${portfolioId})"));
      assertFalse(html.contains("iv-risk-summary__links"));
      assertTrue(html.contains("iv-realized-details"));
      assertTrue(html.contains("iv-realized-attribution-popover"));
      assertFalse(html.contains("dashboard/fragments/positions :: heading"));
      assertFalse(html.contains("iv-attribution-popover"));
    }
  }

  @DisplayName("account Popup Uses Period Profit But Keeps Snapshot Values")
  @Test
  void accountPopupUsesPeriodProfitButKeepsSnapshotValues() {
    int year = java.time.Year.now().getValue();
    List<String> labels = List.of((year - 1) + "-12", year + "-01", year + "-02");
    Performance performance = new Performance();
    LinkedHashMap<String, Double> monthlyProfit = new LinkedHashMap<>();
    monthlyProfit.put(labels.get(0), 100.0);
    monthlyProfit.put(labels.get(1), 100.0);
    monthlyProfit.put(labels.get(2), 100.0);
    performance.setCalculateMonthlyPerformance(monthlyProfit);
    LinkedHashMap<String, MonthlyAttribution> monthlyAttributions = new LinkedHashMap<>();
    for (String label : labels) {
      monthlyAttributions.put(
          label,
          new MonthlyAttribution(
              label,
              1_000.0,
              1_100.0,
              0,
              0,
              0,
              100.0,
              0,
              0,
              0,
              0,
              0,
              0,
              0,
              0,
              List.of(
                  new MonthlyAttribution.AccountContribution(
                      "1", 1_000.0, 1_100.0, 0, 100.0, 0.1))));
    }
    performance.setMonthlyAttributions(monthlyAttributions);

    Portfolio portfolio = new Portfolio();
    portfolio.setMonthlyPerformance(performance);
    AccountBalance account =
        new AccountBalance(
            1L,
            "Main",
            900.0,
            1_000.0,
            300.0,
            300.0,
            30.0,
            1_300.0,
            100.0,
            com.smartbox.investory.shared.currency.CurrencyType.USD,
            1_250.0,
            100.0);
    portfolio.setAccountBalances(List.of(account));
    portfolio.setAccountBalancesTotal(account);

    Benchmark benchmark = new Benchmark();
    benchmark.setAvailable(true);
    benchmark.setLabels(labels);
    benchmark.setPortfolioCurve(List.of(100.0, 200.0, 300.0));
    benchmark.setBenchmarkCurve(List.of(50.0, 75.0, 125.0));
    benchmark.setPortfolioReturnCurve(List.of(5.0, 10.25, 15.76));
    benchmark.setBenchmarkReturnCurve(List.of(5.0, 7.5, 13.0));
    benchmark.setPortfolioPl(300.0);
    benchmark.setPortfolioReturnPct(15.76);
    benchmark.setAccountSeries(
        List.of(
            new Benchmark.AccountSeries(
                1L,
                1_000.0,
                300.0,
                125.0,
                List.of(100.0, 200.0, 300.0),
                List.of(50.0, 75.0, 125.0),
                List.of(1_000.0, 1_000.0, 1_000.0),
                List.of(100.0, 100.0, 100.0),
                List.of(5.0, 5.0, 5.0))));

    when(portfolioMetricsService.calculateTotalProfitLoss()).thenReturn(portfolio);
    when(benchmarkService.calculate()).thenReturn(benchmark);

    DashboardPageView result =
        new InvestmentDashboardFacade(
                portfolioMetricsService, benchmarkService, new DashboardPeriodFilterService())
            .loadDashboard(new DashboardQuery(List.of(), false, DashboardPeriod.YEAR_TO_DATE, 1L));
    DashboardPageView maxResult =
        new InvestmentDashboardFacade(
                portfolioMetricsService, benchmarkService, new DashboardPeriodFilterService())
            .loadDashboard(new DashboardQuery(List.of(), false, DashboardPeriod.MAX, 1L));

    AccountBalance periodAccount = result.overview().accountBalances().getFirst();
    assertEquals(900.0, periodAccount.getNetDeposit());
    assertEquals(1_300.0, periodAccount.getBalance());
    assertEquals(100.0, periodAccount.getCash());
    assertEquals(200.0, periodAccount.getProfit());
    assertEquals(300.0, periodAccount.getLocalProfit());
    assertEquals(10.25, periodAccount.getProfitLossPercent(), 0.001);
    assertEquals(200.0, result.overview().accountBalancesTotal().getProfit());
    assertEquals(10.25, result.overview().accountBalancesTotal().getProfitLossPercent(), 0.001);
    assertEquals(300.0, maxResult.overview().accountBalances().getFirst().getProfit());
    assertEquals(
        15.76, maxResult.overview().accountBalances().getFirst().getProfitLossPercent(), 0.001);
    assertEquals(
        result.overview().accountBalances().getFirst().getBalance(),
        maxResult.overview().accountBalances().getFirst().getBalance());
    assertEquals(
        result.overview().accountBalances().getFirst().getNetDeposit(),
        maxResult.overview().accountBalances().getFirst().getNetDeposit());
    assertEquals(
        result.overview().accountBalances().getFirst().getCash(),
        maxResult.overview().accountBalances().getFirst().getCash());
  }

  private void assertSnapshotMetrics(DashboardPageView page) {
    assertEquals(1_000.0, page.overview().netDeposits());
    assertEquals(1_500.0, page.overview().balance());
    assertEquals(250.0, page.overview().unrealizedProfit());
  }

  private Benchmark benchmark(List<String> labels) {
    Benchmark benchmark = new Benchmark();
    benchmark.setAvailable(true);
    benchmark.setLabels(labels);
    benchmark.setInvestedCapital(1_000.0);
    benchmark.setPortfolioCurve(List.of(100.0, 300.0, 600.0, 1_000.0));
    benchmark.setBenchmarkCurve(List.of(50.0, 100.0, 150.0, 200.0));
    return benchmark;
  }

  private void addPeriod(
      List<String> labels,
      LinkedHashMap<String, Double> monthlyProfit,
      LinkedHashMap<String, MonthlyAttribution> attributions,
      int index,
      double profit,
      double realized,
      double dividends,
      double taxes,
      double interest,
      double openingEquity) {
    String label = labels.get(index);
    monthlyProfit.put(label, profit);
    attributions.put(
        label,
        new MonthlyAttribution(
            label,
            openingEquity,
            openingEquity + profit,
            0,
            0,
            0,
            profit,
            0,
            realized,
            dividends,
            interest,
            0,
            taxes,
            0,
            0,
            List.of()));
  }

  private MonthlyAttribution attributionWithIncome() {
    return new MonthlyAttribution(
        "2026-01", 1_000, 1_023, 0, 0, 0, 23, 0, 0, 20, 5, 0, -2, 0, 0, List.of());
  }

  private MonthlyAttribution attribution(String label) {
    return new MonthlyAttribution(label, 0, 0, 5, 1, 4, 100, 0, 0, 0, 0, 0, 0, 0, 0, List.of());
  }

  private static void assertEquals(Object expected, Object actual, Object... extras) {
    if (extras.length == 0) {
      if (expected instanceof Number expectedNumber && actual instanceof Number actualNumber) {
        org.junit.jupiter.api.Assertions.assertEquals(
            expectedNumber.doubleValue(), actualNumber.doubleValue());
        return;
      }
      org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
      return;
    }
    org.junit.jupiter.api.Assertions.assertEquals(
        ((Number) expected).doubleValue(),
        ((Number) actual).doubleValue(),
        ((Number) extras[0]).doubleValue());
  }
}
