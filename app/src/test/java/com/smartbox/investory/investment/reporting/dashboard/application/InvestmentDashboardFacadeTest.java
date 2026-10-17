package com.smartbox.investory.investment.reporting.dashboard.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
import com.smartbox.investory.investment.reporting.dashboard.service.DashboardPeriodViewService;
import com.smartbox.investory.investment.reporting.dashboard.service.PortfolioStructureQuery;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
  @Mock private DashboardPeriodViewService periodFilterService;
  @Mock private PortfolioPerformanceQuery performanceQuery;

  @BeforeEach
  void returnFilteredCopiesAsExpected() {
    lenient()
        .when(periodFilterService.filter(any(Portfolio.class), any(DashboardPeriod.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    lenient()
        .when(periodFilterService.filter(any(Benchmark.class), any(DashboardPeriod.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void incompleteValuationDoesNotPublishPlausiblePeriodOrProfileMetrics() {
    Portfolio portfolio = new Portfolio();
    addCanonicalMonth(portfolio);
    portfolio.setValuationComplete(false);
    portfolio.setNetDeposits(100.0);
    portfolio.getMonthlyPerformance().setMonthlyOperationsCount(Map.of("2026-01", 7L));
    portfolio.getMonthlyPerformance().setMonthlyCashflow(Map.of("2026-01", 100.0));
    portfolio.getMonthlyPerformance().getCalculateMonthlyPerformance().put("2026-01", 250.0);
    when(portfolioMetricsService.calculateTotalProfitLoss(1L)).thenReturn(portfolio);
    Benchmark benchmark = new Benchmark();
    benchmark.setBenchmarkReturnPct(12.0);
    when(benchmarkService.calculate(1L, null)).thenReturn(benchmark);
    when(performanceQuery.forPortfolioMonths(
            1L, YearMonth.parse("2026-01"), YearMonth.parse("2026-01")))
        .thenReturn(
            performanceResult(
                YearMonth.parse("2026-01"),
                YearMonth.parse("2026-01"),
                250,
                100,
                20,
                5,
                3,
                1000,
                1250));
    InvestmentDashboardFacade facade =
        newFacade(portfolioMetricsService, benchmarkService, new DashboardPeriodViewService());
    DashboardPageView result =
        facade.loadDashboard(new DashboardQuery(List.of(), false, DashboardPeriod.MAX, 1L));
    org.junit.jupiter.api.Assertions.assertNull(result.overview().balance());
    org.junit.jupiter.api.Assertions.assertNull(result.overview().totalProfit());
    org.junit.jupiter.api.Assertions.assertNull(result.overview().gainPct());
    org.junit.jupiter.api.Assertions.assertNull(result.overview().netGain());
    org.junit.jupiter.api.Assertions.assertNull(result.overview().incomeYieldPct());
    org.junit.jupiter.api.Assertions.assertNull(result.performance().summary().portfolioPl());
    org.junit.jupiter.api.Assertions.assertNull(
        result.performance().summary().portfolioReturnPct());
    assertEquals(
        ReturnMetric.Status.INSUFFICIENT_DATA,
        result.performance().summary().totalReturn().status());
    assertEquals(
        ReturnMetric.Status.INSUFFICIENT_DATA,
        result.performance().summary().timeWeightedReturn().status());
    assertEquals("—", result.performance().summary().bestPeriod());
    assertEquals(12.0, result.performance().summary().benchmarkReturnPct());
    assertEquals(BigDecimal.valueOf(100.0), result.overview().netDeposits());
    assertTrue(result.overview().monthlyPerformance().calculateMonthlyPerformance().isEmpty());
    assertEquals(
        Map.of("2026-01", 7L), result.overview().monthlyPerformance().monthlyOperationsCount());
    assertEquals(
        Map.of("2026-01", 100.0), result.overview().monthlyPerformance().monthlyCashflow());
    assertEquals(
        ReturnMetric.Status.INSUFFICIENT_DATA,
        facade.loadPerformanceKpi(1L).totalReturn().status());
    assertEquals("Benchmark estimate", facade.loadPerformanceKpi(1L).historyContext());
  }

  @DisplayName("composes Filtered Portfolio And Benchmark Into Dashboard Sections")
  @Test
  void composesFilteredPortfolioAndBenchmarkIntoDashboardSections() {
    Portfolio portfolio = new Portfolio();
    addCanonicalMonth(portfolio);
    portfolio.setBalance(123.0);
    portfolio.setNetDeposits(100.0);
    portfolio.setPerformancePerSymbol(
        List.of(
            new InstrumentPerformance("GAIN", 4.0, 0.0, 4.0),
            new InstrumentPerformance("LOSS", -2.0, 0.0, -2.0)));
    portfolio.setDividendGainers(List.of(new DividendGainer("AAPL.US", 42.0)));
    Benchmark benchmark = new Benchmark();
    when(portfolioMetricsService.calculateTotalProfitLoss(1L)).thenReturn(portfolio);
    when(benchmarkService.calculate(1L, null)).thenReturn(benchmark);
    DashboardPageView result =
        newFacade(portfolioMetricsService, benchmarkService, periodFilterService)
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
    addCanonicalMonth(portfolio);
    List<InstrumentPerformance> rows = new ArrayList<>();
    for (int value = 1; value <= 16; value++) {
      rows.add(new InstrumentPerformance("GAIN-" + value, value, 0.0, value));
      rows.add(new InstrumentPerformance("LOSS-" + value, -value, 0.0, -value));
    }
    portfolio.setPerformancePerSymbol(rows);
    when(portfolioMetricsService.calculateTotalProfitLoss(1L)).thenReturn(portfolio);
    when(benchmarkService.calculate(1L, null)).thenReturn(new Benchmark());

    DashboardPageView result =
        newFacade(portfolioMetricsService, benchmarkService, periodFilterService)
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
            AccountBalance.builder()
                .accountId(1L)
                .accountName("USD one")
                .balanceBase(BigDecimal.valueOf(350))
                .cashBase(BigDecimal.valueOf(50))
                .localCurrency(CurrencyType.USD)
                .balanceLocal(BigDecimal.valueOf(350))
                .cashLocal(BigDecimal.valueOf(50))
                .build(),
            AccountBalance.builder()
                .accountId(2L)
                .accountName("USD two")
                .balanceBase(BigDecimal.valueOf(210))
                .cashBase(BigDecimal.valueOf(50))
                .localCurrency(CurrencyType.USD)
                .balanceLocal(BigDecimal.valueOf(210))
                .cashLocal(BigDecimal.valueOf(50))
                .build(),
            AccountBalance.builder()
                .accountId(3L)
                .accountName("PLN")
                .balanceBase(BigDecimal.valueOf(140))
                .localCurrency(CurrencyType.PLN)
                .balanceLocal(BigDecimal.valueOf(140))
                .build()));
    portfolio.setAccountBalancesTotal(new AccountBalance());
    var facade = newFacade(portfolioMetricsService, benchmarkService, periodFilterService);
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
    addCanonicalMonth(portfolio);
    Benchmark benchmark = new Benchmark();
    when(portfolioMetricsService.calculateTotalProfitLoss(1L)).thenReturn(portfolio);
    when(benchmarkService.calculate(1L, null)).thenReturn(benchmark);

    DashboardPageView result =
        new InvestmentDashboardFacade(
                portfolioMetricsService,
                benchmarkService,
                periodFilterService,
                new PortfolioPeriodMetricsService(),
                "2027-03-01",
                performanceQuery,
                null,
                new PortfolioStructureQuery(null),
                com.smartbox.investory.investment.reporting.ReturnEstimateCalculator
                    .DEFAULT_BENCHMARK_EXPECTATION)
            .loadDashboard(new DashboardQuery(List.of(), false, DashboardPeriod.MAX, 1L));

    assertEquals("2027-03", result.performance().kpiStart());
  }

  @DisplayName("maps Submitted Benchmark Account Selection To Benchmark Service")
  @Test
  void mapsSubmittedBenchmarkAccountSelectionToBenchmarkService() {
    Portfolio portfolio = new Portfolio();
    addCanonicalMonth(portfolio);
    Benchmark benchmark = new Benchmark();
    List<Long> accountIds = List.of(11L, 12L);
    benchmark.setAccountOptions(
        List.of(
            new Benchmark.AccountOption(11L, "One", true),
            new Benchmark.AccountOption(12L, "Two", true),
            new Benchmark.AccountOption(13L, "Three", false)));
    when(portfolioMetricsService.calculateTotalProfitLoss(1L)).thenReturn(portfolio);
    when(benchmarkService.calculate(1L, accountIds)).thenReturn(benchmark);

    DashboardPageView result =
        newFacade(portfolioMetricsService, benchmarkService, periodFilterService)
            .loadDashboard(new DashboardQuery(accountIds, true, DashboardPeriod.YEAR_TO_DATE, 1L));

    verify(benchmarkService).calculate(1L, accountIds);
    assertEquals(
        "/portfolios/1/dashboard?period=YTD&benchmarkAccountsSubmitted=true&accountIds=11&accountIds=12",
        result.navigation().periodUrl(DashboardPeriod.YEAR_TO_DATE));
  }

  @DisplayName("treats An Empty Submitted Account Selection As All Eligible Accounts")
  @Test
  void treatsAnEmptySubmittedAccountSelectionAsAllEligibleAccounts() {
    Portfolio portfolio = new Portfolio();
    addCanonicalMonth(portfolio);
    Benchmark benchmark = new Benchmark();
    benchmark.setPortfolioPerformanceAvailable(true);
    benchmark.setAccountOptions(
        List.of(
            new Benchmark.AccountOption(1L, "One", true),
            new Benchmark.AccountOption(3L, "Three", true)));
    when(portfolioMetricsService.calculateTotalProfitLoss(1L)).thenReturn(portfolio);
    when(benchmarkService.calculate(1L, null)).thenReturn(benchmark);

    DashboardPageView result =
        newFacade(portfolioMetricsService, benchmarkService, periodFilterService)
            .loadDashboard(new DashboardQuery(List.of(), true, DashboardPeriod.YEAR_TO_DATE, 1L));

    verify(benchmarkService).calculate(1L, null);
    assertTrue(result.performance().benchmark().available());
    assertTrue(
        result.performance().benchmark().accountOptions().stream()
            .allMatch(Benchmark.AccountOption::selected));
    assertEquals(
        "/portfolios/1/dashboard?period=YTD",
        result.navigation().periodUrl(DashboardPeriod.YEAR_TO_DATE));
  }

  @DisplayName("does Not Keep Redundant Account Ids In Period Links When All Accounts Are Selected")
  @Test
  void doesNotKeepRedundantAccountIdsInPeriodLinksWhenAllAccountsAreSelected() {
    Portfolio portfolio = new Portfolio();
    addCanonicalMonth(portfolio);
    Benchmark benchmark = new Benchmark();
    List<Long> accountIds = List.of(1L, 3L);
    benchmark.setAccountOptions(
        List.of(
            new Benchmark.AccountOption(1L, "One", true),
            new Benchmark.AccountOption(3L, "Three", true)));
    when(portfolioMetricsService.calculateTotalProfitLoss(1L)).thenReturn(portfolio);
    when(benchmarkService.calculate(1L, accountIds)).thenReturn(benchmark);

    DashboardPageView result =
        newFacade(portfolioMetricsService, benchmarkService, periodFilterService)
            .loadDashboard(new DashboardQuery(accountIds, true, DashboardPeriod.YEAR_TO_DATE, 1L));

    assertEquals(
        "/portfolios/1/dashboard?period=YTD",
        result.navigation().periodUrl(DashboardPeriod.YEAR_TO_DATE));
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
    when(portfolioMetricsService.calculateTotalProfitLoss(1L)).thenReturn(portfolio);
    when(benchmarkService.calculate(1L, null)).thenReturn(benchmark);
    when(performanceQuery.forPortfolioMonths(any(), any(), any()))
        .thenAnswer(
            invocation ->
                performanceResult(
                    invocation.getArgument(1),
                    invocation.getArgument(2),
                    200,
                    0,
                    0,
                    0,
                    0,
                    1_000,
                    1_200));

    DashboardPageView result =
        newFacade(portfolioMetricsService, benchmarkService, new DashboardPeriodViewService())
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
    when(portfolioMetricsService.calculateTotalProfitLoss(1L)).thenReturn(portfolio);
    when(benchmarkService.calculate(1L, null)).thenReturn(benchmark);

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
            periodFilterService,
            new PortfolioPeriodMetricsService(),
            "2026-01-01",
            performanceQuery,
            null,
            new PortfolioStructureQuery(null),
            com.smartbox.investory.investment.reporting.ReturnEstimateCalculator
                .DEFAULT_BENCHMARK_EXPECTATION);
    DashboardPageView result =
        facade.loadDashboard(new DashboardQuery(List.of(), false, DashboardPeriod.MAX, 1L));
    verify(performanceQuery, times(1))
        .forPortfolioMonths(1L, YearMonth.parse("2026-01"), YearMonth.parse("2026-02"));
    InvestmentDashboardFacade.PerformanceKpi profileKpi = facade.loadPerformanceKpi(1L);
    // A separate load reads again instead of retaining potentially stale financial values.
    verify(performanceQuery, times(2))
        .forPortfolioMonths(1L, YearMonth.parse("2026-01"), YearMonth.parse("2026-02"));

    assertEquals(98.45, result.overview().totalProfit(), 0.001);
    assertEquals(7.25, result.overview().gainPct(), 0.001);
    assertEquals(new BigDecimal("0.0725"), result.performance().summary().totalReturn().value());
    assertEquals(
        result.performance().summary().historicalAnnualizedReturn(),
        profileKpi.historicalAnnualizedReturn());
    assertEquals(
        result.performance().summary().expectedAnnualReturn().value(),
        profileKpi.expectedAnnualReturn());
    // The KPI starts in 2026, so all prior-year slots use fixed SPY returns.
    assertEquals(new BigDecimal("0.1884"), profileKpi.expectedAnnualReturn());
    assertEquals(result.performance().summary().totalReturnStartDate(), profileKpi.startDate());

    Portfolio filtered = new Portfolio();
    Performance filteredPerformance = new Performance();
    filteredPerformance.setCalculateMonthlyPerformance(
        new LinkedHashMap<>(java.util.Map.of("2026-02", 20.0)));
    filtered.setMonthlyPerformance(filteredPerformance);
    when(periodFilterService.filter(portfolio, DashboardPeriod.ONE_MONTH)).thenReturn(filtered);
    when(performanceQuery.forPortfolioMonths(
            1L, YearMonth.parse("2026-02"), YearMonth.parse("2026-02")))
        .thenReturn(canonical);
    facade.loadDashboard(new DashboardQuery(List.of(), false, DashboardPeriod.ONE_MONTH, 1L));
    verify(performanceQuery, times(3))
        .forPortfolioMonths(1L, YearMonth.parse("2026-01"), YearMonth.parse("2026-02"));
    verify(performanceQuery)
        .forPortfolioMonths(1L, YearMonth.parse("2026-02"), YearMonth.parse("2026-02"));
  }

  @DisplayName("does Not Substitute Legacy Attribution When Canonical Reporting Has No Result")
  @Test
  void doesNotSubstituteLegacyAttributionWhenCanonicalReportingHasNoResult() {
    Performance performance = new Performance();
    performance.setCalculateMonthlyPerformance(
        new LinkedHashMap<>(java.util.Map.of("2026-01", 999.0)));
    performance.setMonthlyAttributions(
        new LinkedHashMap<>(java.util.Map.of("2026-01", attributionWithIncome())));
    Portfolio portfolio = new Portfolio();
    portfolio.setMonthlyPerformance(performance);
    when(portfolioMetricsService.calculateTotalProfitLoss(1L)).thenReturn(portfolio);
    when(performanceQuery.forPortfolioMonths(
            1L, YearMonth.parse("2026-01"), YearMonth.parse("2026-01")))
        .thenReturn(null);

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                newFacade(portfolioMetricsService, benchmarkService, periodFilterService)
                    .loadDashboard(new DashboardQuery(List.of(), false, DashboardPeriod.MAX, 1L)));

    assertTrue(failure.getMessage().contains("PortfolioPerformanceQuery returned no result"));
    verify(performanceQuery)
        .forPortfolioMonths(1L, YearMonth.parse("2026-01"), YearMonth.parse("2026-01"));
  }

  @DisplayName("reads Canonical Reporting Even Without Monthly Display History")
  @Test
  void readsCanonicalReportingEvenWithoutMonthlyDisplayHistory() {
    Performance empty = new Performance();
    empty.setCalculateMonthlyPerformance(new LinkedHashMap<>());
    Performance absentMap = new Performance();
    absentMap.setCalculateMonthlyPerformance(null);
    for (Performance history : java.util.Arrays.asList(empty, absentMap, null)) {
      org.mockito.Mockito.clearInvocations(performanceQuery);
      Portfolio portfolio = new Portfolio();
      portfolio.setMonthlyPerformance(history);
      Benchmark benchmark = new Benchmark();
      benchmark.setPortfolioPl(999.0);
      when(portfolioMetricsService.calculateTotalProfitLoss(1L)).thenReturn(portfolio);
      when(benchmarkService.calculate(1L, null)).thenReturn(benchmark);
      when(performanceQuery.forPortfolioMonths(1L, null, null))
          .thenReturn(
              performanceResult(
                  YearMonth.of(2026, 1), YearMonth.of(2026, 2), 42, 7, 3, 2, 1, 100, 142));

      DashboardPageView page =
          newFacade(portfolioMetricsService, benchmarkService, periodFilterService)
              .loadDashboard(new DashboardQuery(List.of(), false, DashboardPeriod.MAX, 1L));

      assertEquals(42.0, page.overview().totalProfit());
      assertEquals(42.0, page.performance().summary().portfolioPl());
      assertEquals(7.0, page.overview().realizedProfit());
      verify(performanceQuery).forPortfolioMonths(1L, null, null);
    }
  }

  @DisplayName(
      "empty Display History Keeps Configured And Selected Period Bounds And Portfolio Scope")
  @Test
  void emptyDisplayHistoryKeepsConfiguredAndSelectedPeriodBoundsAndPortfolioScope() {
    Portfolio portfolio = new Portfolio();
    portfolio.setMonthlyPerformance(new Performance());
    Benchmark benchmark = new Benchmark();
    when(portfolioMetricsService.calculateTotalProfitLoss(7L)).thenReturn(portfolio);
    when(benchmarkService.calculate(7L, List.of(17L))).thenReturn(benchmark);
    DashboardPeriodViewService periods =
        new DashboardPeriodViewService(
            LocalDate.of(2025, 2, 1),
            java.time.Clock.fixed(
                java.time.Instant.parse("2026-08-15T12:00:00Z"), java.time.ZoneOffset.UTC));
    for (DashboardPeriod period :
        List.of(DashboardPeriod.MAX, DashboardPeriod.YEAR_TO_DATE, DashboardPeriod.ONE_MONTH)) {
      YearMonth from =
          switch (period) {
            case MAX -> YearMonth.of(2025, 2);
            case YEAR_TO_DATE -> YearMonth.of(2026, 1);
            default -> YearMonth.of(2026, 7);
          };
      when(performanceQuery.forPortfolioMonths(7L, from, null))
          .thenReturn(performanceResult(from, YearMonth.of(2026, 8), 42, 0, 0, 0, 0, 100, 142));
      DashboardPageView page =
          newFacade(portfolioMetricsService, benchmarkService, periods)
              .loadDashboard(new DashboardQuery(List.of(17L), true, period, 7L));
      assertEquals(42.0, page.overview().totalProfit());
      verify(performanceQuery).forPortfolioMonths(7L, from, null);
    }
    verify(benchmarkService, times(3)).calculate(7L, List.of(17L));
  }

  @DisplayName("no Canonical Reporting Rows Keep Returns Explicitly Unavailable")
  @Test
  void noCanonicalReportingRowsKeepReturnsExplicitlyUnavailable() {
    Portfolio portfolio = new Portfolio();
    portfolio.setMonthlyPerformance(new Performance());
    Benchmark benchmark = new Benchmark();
    benchmark.setPortfolioPl(500.0);
    PortfolioPerformanceQuery emptyQuery =
        new PortfolioPerformanceQuery(
            mock(
                com.smartbox.investory.investment.infrastructure.persistence.portfolio
                    .PortfolioMonthlyPerformanceRepository.class),
            mock(
                com.smartbox.investory.investment.infrastructure.persistence.account
                    .AccountDailyRepository.class));
    PerformanceResult emptyResult = emptyQuery.forPortfolioMonths(1L, null, null);
    when(performanceQuery.forPortfolioMonths(1L, null, null)).thenReturn(emptyResult);
    when(portfolioMetricsService.calculateTotalProfitLoss(1L)).thenReturn(portfolio);
    when(benchmarkService.calculate(1L, null)).thenReturn(benchmark);
    DashboardPageView page =
        newFacade(portfolioMetricsService, benchmarkService, periodFilterService)
            .loadDashboard(new DashboardQuery(List.of(), false, DashboardPeriod.MAX, 1L));
    assertEquals(
        ReturnMetric.Status.INSUFFICIENT_DATA,
        page.performance().summary().timeWeightedReturn().status());
    assertEquals(0.0, page.overview().totalProfit());
    assertEquals(0.0, page.performance().summary().portfolioPl());
    verify(performanceQuery).forPortfolioMonths(1L, null, null);
  }

  @DisplayName("canonical Query Errors Remain Visible Without Display History")
  @Test
  void canonicalQueryErrorsRemainVisibleWithoutDisplayHistory() {
    Portfolio portfolio = new Portfolio();
    when(portfolioMetricsService.calculateTotalProfitLoss(1L)).thenReturn(portfolio);
    when(benchmarkService.calculate(1L, null)).thenReturn(new Benchmark());
    IllegalStateException expected = new IllegalStateException("canonical storage failure");
    when(performanceQuery.forPortfolioMonths(1L, null, null)).thenThrow(expected);
    assertSame(
        expected,
        assertThrows(
            IllegalStateException.class,
            () ->
                newFacade(portfolioMetricsService, benchmarkService, periodFilterService)
                    .loadDashboard(new DashboardQuery(List.of(), false, DashboardPeriod.MAX, 1L))));
  }

  @DisplayName("does Not Invent Dashboard Performance When Monthly History Is Empty")
  @Test
  void doesNotInventDashboardPerformanceWhenMonthlyHistoryIsEmpty() {
    Performance performance = new Performance();
    performance.setCalculateMonthlyPerformance(new LinkedHashMap<>());
    performance.setMonthlyAttributions(
        new LinkedHashMap<>(java.util.Map.of("2026-01", attributionWithIncome())));
    Portfolio portfolio = new Portfolio();
    portfolio.setMonthlyPerformance(performance);
    Benchmark benchmark = new Benchmark();
    benchmark.setAvailable(true);
    benchmark.setPortfolioPl(500.0);
    benchmark.setPortfolioReturnPct(25.0);
    when(portfolioMetricsService.calculateTotalProfitLoss(1L)).thenReturn(portfolio);
    when(benchmarkService.calculate(1L, null)).thenReturn(benchmark);

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                newFacade(portfolioMetricsService, benchmarkService, periodFilterService)
                    .loadDashboard(new DashboardQuery(List.of(), false, DashboardPeriod.MAX, 1L)));

    assertTrue(failure.getMessage().contains("PortfolioPerformanceQuery returned no result"));
    verify(performanceQuery).forPortfolioMonths(1L, null, null);
  }

  @DisplayName("calculates Drawdown Income Yield And Concentration Warning")
  @Test
  void calculatesDrawdownIncomeYieldAndConcentrationWarning() {
    Portfolio portfolio = new Portfolio();
    portfolio.setNetDeposits(1_000.0);
    Performance performance = new Performance();
    performance.setCalculateMonthlyPerformance(
        new LinkedHashMap<>(java.util.Map.of("2026-01", 23.0)));
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
    when(portfolioMetricsService.calculateTotalProfitLoss(1L)).thenReturn(portfolio);
    when(benchmarkService.calculate(1L, null)).thenReturn(benchmark);
    when(performanceQuery.forPortfolioMonths(any(), any(), any()))
        .thenAnswer(
            invocation ->
                performanceResult(
                    invocation.getArgument(1),
                    invocation.getArgument(2),
                    23,
                    0,
                    20,
                    -2,
                    5,
                    1_000,
                    1_023));

    DashboardPageView result =
        newFacade(portfolioMetricsService, benchmarkService, new DashboardPeriodViewService())
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
    when(portfolioMetricsService.calculateTotalProfitLoss(1L)).thenReturn(portfolio);
    when(benchmarkService.calculate(1L, null)).thenReturn(benchmark);
    when(performanceQuery.forPortfolioMonths(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              YearMonth from = invocation.getArgument(1);
              YearMonth to = invocation.getArgument(2);
              long months = java.time.temporal.ChronoUnit.MONTHS.between(from, to) + 1;
              return performanceResult(
                  from,
                  to,
                  months * 25,
                  months * 25,
                  months * 2.5,
                  months * -2.5,
                  months * 2.5,
                  1_000,
                  1_000 + months * 25);
            });

    InvestmentDashboardFacade facade =
        newFacade(portfolioMetricsService, benchmarkService, new DashboardPeriodViewService());
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
    assertEquals(125.0, max.overview().realizedProfit());
    assertEquals(12.5, max.overview().incomeTotal());
  }

  @DisplayName("dashboard Cards Declare Current And Selected Period Semantics")
  @Test
  void dashboardCardsDeclareCurrentAndSelectedPeriodSemantics() throws Exception {
    try (var template = getClass().getResourceAsStream("/templates/dashboard.html")) {
      assertTrue(template != null, "dashboard template must be present");
      String html = new String(template.readAllBytes(), StandardCharsets.UTF_8);

      assertTrue(html.contains("dashboard/fragments/dashboard-header :: dashboardHeader"));
      assertTrue(html.contains("dashboard/fragments/overview :: overview"));
      assertTrue(html.contains("dashboard/fragments/performance :: performance"));
      assertTrue(html.contains("dashboard-page-data"));
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
        AccountBalance.builder()
            .accountId(1L)
            .accountName("Main")
            .netDepositLocal(BigDecimal.valueOf(900))
            .netDepositBase(BigDecimal.valueOf(1_000))
            .profitBase(BigDecimal.valueOf(300))
            .profitLocal(BigDecimal.valueOf(300))
            .profitLossPercent(BigDecimal.valueOf(30))
            .balanceBase(BigDecimal.valueOf(1_300))
            .cashBase(BigDecimal.valueOf(100))
            .localCurrency(CurrencyType.USD)
            .balanceLocal(BigDecimal.valueOf(1_250))
            .cashLocal(BigDecimal.valueOf(100))
            .build();
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

    when(portfolioMetricsService.calculateTotalProfitLoss(1L)).thenReturn(portfolio);
    when(benchmarkService.calculate(1L, null)).thenReturn(benchmark);
    when(performanceQuery.forPortfolioMonths(any(), any(), any()))
        .thenAnswer(
            invocation ->
                performanceResult(
                    invocation.getArgument(1),
                    invocation.getArgument(2),
                    1,
                    1,
                    1,
                    -1,
                    1,
                    1_000,
                    1_001));

    DashboardPageView result =
        newFacade(portfolioMetricsService, benchmarkService, new DashboardPeriodViewService())
            .loadDashboard(new DashboardQuery(List.of(), false, DashboardPeriod.YEAR_TO_DATE, 1L));
    DashboardPageView maxResult =
        newFacade(portfolioMetricsService, benchmarkService, new DashboardPeriodViewService())
            .loadDashboard(new DashboardQuery(List.of(), false, DashboardPeriod.MAX, 1L));

    AccountBalance periodAccount = result.overview().accountBalances().getFirst();
    assertEquals(900.0, periodAccount.getNetDepositLocal());
    assertEquals(1_300.0, periodAccount.getBalanceBase());
    assertEquals(100.0, periodAccount.getCashBase());
    assertEquals(200.0, periodAccount.getProfitBase());
    assertEquals(300.0, periodAccount.getProfitLocal());
    assertEquals(10.25, periodAccount.getProfitLossPercent(), 0.001);
    assertEquals(200.0, result.overview().accountBalancesTotal().getProfitBase());
    assertEquals(10.25, result.overview().accountBalancesTotal().getProfitLossPercent(), 0.001);
    assertEquals(300.0, maxResult.overview().accountBalances().getFirst().getProfitBase());
    assertEquals(
        15.76, maxResult.overview().accountBalances().getFirst().getProfitLossPercent(), 0.001);
    assertEquals(
        result.overview().accountBalances().getFirst().getBalanceBase(),
        maxResult.overview().accountBalances().getFirst().getBalanceBase());
    assertEquals(
        result.overview().accountBalances().getFirst().getNetDepositLocal(),
        maxResult.overview().accountBalances().getFirst().getNetDepositLocal());
    assertEquals(
        result.overview().accountBalances().getFirst().getCashBase(),
        maxResult.overview().accountBalances().getFirst().getCashBase());
  }

  private void assertSnapshotMetrics(DashboardPageView page) {
    assertEquals(1_000.0, page.overview().netDeposits());
    assertEquals(1_500.0, page.overview().balance());
    assertEquals(250.0, page.overview().unrealizedProfit());
  }

  private InvestmentDashboardFacade newFacade(
      PortfolioMetricsService portfolioMetricsService,
      BenchmarkService benchmarkService,
      DashboardPeriodViewService periodViewService) {
    return new InvestmentDashboardFacade(
        portfolioMetricsService,
        benchmarkService,
        periodViewService,
        new PortfolioPeriodMetricsService(),
        "2026-01-01",
        performanceQuery,
        null,
        new PortfolioStructureQuery(null),
        com.smartbox.investory.investment.reporting.ReturnEstimateCalculator
            .DEFAULT_BENCHMARK_EXPECTATION);
  }

  private void addCanonicalMonth(Portfolio portfolio) {
    Performance performance = new Performance();
    performance.setCalculateMonthlyPerformance(
        new LinkedHashMap<>(java.util.Map.of("2026-01", 0.0)));
    portfolio.setMonthlyPerformance(performance);
    lenient()
        .when(
            performanceQuery.forPortfolioMonths(
                1L, YearMonth.parse("2026-01"), YearMonth.parse("2026-01")))
        .thenReturn(
            performanceResult(
                YearMonth.parse("2026-01"), YearMonth.parse("2026-01"), 0, 0, 0, 0, 0, 0, 0));
  }

  private PerformanceResult performanceResult(
      YearMonth from,
      YearMonth to,
      double profit,
      double realized,
      double dividends,
      double taxes,
      double interest,
      double startValue,
      double endValue) {
    return new PerformanceResult(
        new PerformancePeriod(from.atDay(1), to.atEndOfMonth()),
        CurrencyType.USD,
        BigDecimal.valueOf(startValue),
        BigDecimal.valueOf(endValue),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.valueOf(profit),
        BigDecimal.valueOf(realized),
        null,
        BigDecimal.valueOf(dividends),
        BigDecimal.valueOf(interest),
        BigDecimal.ZERO,
        BigDecimal.valueOf(taxes),
        null,
        ReturnMetric.available(new BigDecimal("0.0404")),
        ReturnMetric.available(new BigDecimal("0.0404")),
        null);
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
