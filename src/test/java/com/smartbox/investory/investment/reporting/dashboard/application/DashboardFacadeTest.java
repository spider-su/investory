package com.smartbox.investory.investment.reporting.dashboard.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.accounting.PortfolioService;
import com.smartbox.investory.investment.accounting.model.models.AccountBalance;
import com.smartbox.investory.investment.accounting.model.models.Benchmark;
import com.smartbox.investory.investment.accounting.model.models.InstrumentPerformance;
import com.smartbox.investory.investment.accounting.model.models.MonthlyAttribution;
import com.smartbox.investory.investment.accounting.model.models.OpenPositionValue;
import com.smartbox.investory.investment.accounting.model.models.Performance;
import com.smartbox.investory.investment.accounting.model.models.Portfolio;
import com.smartbox.investory.investment.accounting.model.models.RiskExposureSummary;
import com.smartbox.investory.investment.reporting.BenchmarkService;
import com.smartbox.investory.investment.reporting.dashboard.service.DashboardPeriod;
import com.smartbox.investory.investment.reporting.dashboard.service.DashboardPeriodFilterService;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardFacadeTest {

  @Mock private PortfolioService portfolioService;
  @Mock private BenchmarkService benchmarkService;
  @Mock private DashboardPeriodFilterService periodFilterService;

  @Test
  void composesFilteredPortfolioAndBenchmarkIntoDashboardSections() {
    Portfolio portfolio = new Portfolio();
    portfolio.setBalance(123.0);
    portfolio.setNetDeposits(100.0);
    portfolio.setPerformancePerSymbol(
        List.of(
            new InstrumentPerformance("GAIN", 4.0, 0.0, 4.0),
            new InstrumentPerformance("LOSS", -2.0, 0.0, -2.0)));
    Benchmark benchmark = new Benchmark();
    when(portfolioService.calculateTotalProfitLoss()).thenReturn(portfolio);
    when(benchmarkService.calculate()).thenReturn(benchmark);

    DashboardPageView result =
        new DashboardFacade(portfolioService, benchmarkService, periodFilterService)
            .loadDashboard(new DashboardQuery(List.of(), false, "1Y"));

    assertEquals(123.0, result.overview().balance());
    assertEquals(100.0, result.overview().netDeposits());
    assertEquals("GAIN", result.performance().topGainers().getFirst().getSymbol());
    assertEquals("LOSS", result.performance().topLosers().getFirst().getSymbol());
    assertSame(result.overview().positions(), result.positions());
    assertEquals(DashboardPeriod.ONE_YEAR, result.selectedPeriod());
    assertEquals("2026-01", result.performance().kpiStart());
    verify(periodFilterService).apply(portfolio, DashboardPeriod.ONE_YEAR);
    verify(periodFilterService).apply(benchmark, DashboardPeriod.ONE_YEAR);
  }

  @Test
  void portfolioStructureAggregatesDuplicateSymbolsAndAccountCurrencies() throws Exception {
    Portfolio portfolio = new Portfolio();
    portfolio.setBalance(700.0);
    portfolio.setCash(100.0);
    portfolio.setOpenPositionValues(
        List.of(
            new OpenPositionValue(
                "VWRA", 1, 0, 0, 0, CurrencyType.USD, 300, 30, null, CurrencyType.USD, 0, null),
            new OpenPositionValue(
                "VWRA", 1, 0, 0, 0, CurrencyType.USD, 200, 20, null, CurrencyType.USD, 0, null),
            new OpenPositionValue(
                "SMH", 1, 0, 0, 0, CurrencyType.USD, 100, 10, null, CurrencyType.USD, 0, null)));
    portfolio.setAccountBalances(
        List.of(
            new AccountBalance(
                1L, "USD one", 0d, 0d, 0d, 0d, 0d, 350d, 50d, CurrencyType.USD, 350d, 50d),
            new AccountBalance(
                2L, "USD two", 0d, 0d, 0d, 0d, 0d, 210d, 50d, CurrencyType.USD, 210d, 50d),
            new AccountBalance(
                3L, "PLN", 0d, 0d, 0d, 0d, 0d, 140d, 0d, CurrencyType.PLN, 140d, 0d)));
    portfolio.setAccountBalancesTotal(new AccountBalance());
    var facade = new DashboardFacade(portfolioService, benchmarkService, periodFilterService);
    var method =
        DashboardFacade.class.getDeclaredMethod(
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

  @Test
  void exposesConfiguredPerformanceBoardKpiStart() {
    Portfolio portfolio = new Portfolio();
    Benchmark benchmark = new Benchmark();
    when(portfolioService.calculateTotalProfitLoss()).thenReturn(portfolio);
    when(benchmarkService.calculate()).thenReturn(benchmark);

    DashboardPageView result =
        new DashboardFacade(
                portfolioService,
                benchmarkService,
                periodFilterService,
                new PortfolioPeriodMetricsService(),
                "2027-03-01")
            .loadDashboard(new DashboardQuery(List.of(), false, "MAX"));

    assertEquals("2027-03", result.performance().kpiStart());
  }

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
    when(portfolioService.calculateTotalProfitLoss()).thenReturn(portfolio);
    when(benchmarkService.calculate(accountIds)).thenReturn(benchmark);

    DashboardPageView result =
        new DashboardFacade(portfolioService, benchmarkService, periodFilterService)
            .loadDashboard(new DashboardQuery(accountIds, true, null));

    verify(benchmarkService).calculate(accountIds);
    assertEquals(
        "/dashboard?period=YTD&benchmarkAccountsSubmitted=true&accountIds=11&accountIds=12",
        result.navigation().periodUrl(DashboardPeriod.YEAR_TO_DATE));
  }

  @Test
  void treatsAnEmptySubmittedAccountSelectionAsAllEligibleAccounts() {
    Portfolio portfolio = new Portfolio();
    Benchmark benchmark = new Benchmark();
    benchmark.setPortfolioPerformanceAvailable(true);
    benchmark.setAccountOptions(
        List.of(
            new Benchmark.AccountOption(1L, "One", true),
            new Benchmark.AccountOption(3L, "Three", true)));
    when(portfolioService.calculateTotalProfitLoss()).thenReturn(portfolio);
    when(benchmarkService.calculate()).thenReturn(benchmark);

    DashboardPageView result =
        new DashboardFacade(portfolioService, benchmarkService, periodFilterService)
            .loadDashboard(new DashboardQuery(List.of(), true, "YTD"));

    verify(benchmarkService).calculate();
    assertTrue(result.performance().benchmark().available());
    assertTrue(
        result.performance().benchmark().accountOptions().stream()
            .allMatch(Benchmark.AccountOption::selected));
    assertEquals(
        "/dashboard?period=YTD", result.navigation().periodUrl(DashboardPeriod.YEAR_TO_DATE));
  }

  @Test
  void doesNotKeepRedundantAccountIdsInPeriodLinksWhenAllAccountsAreSelected() {
    Portfolio portfolio = new Portfolio();
    Benchmark benchmark = new Benchmark();
    List<Long> accountIds = List.of(1L, 3L);
    benchmark.setAccountOptions(
        List.of(
            new Benchmark.AccountOption(1L, "One", true),
            new Benchmark.AccountOption(3L, "Three", true)));
    when(portfolioService.calculateTotalProfitLoss()).thenReturn(portfolio);
    when(benchmarkService.calculate(accountIds)).thenReturn(benchmark);

    DashboardPageView result =
        new DashboardFacade(portfolioService, benchmarkService, periodFilterService)
            .loadDashboard(new DashboardQuery(accountIds, true, "YTD"));

    assertEquals(
        "/dashboard?period=YTD", result.navigation().periodUrl(DashboardPeriod.YEAR_TO_DATE));
  }

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
    when(portfolioService.calculateTotalProfitLoss()).thenReturn(portfolio);
    when(benchmarkService.calculate()).thenReturn(benchmark);

    DashboardPageView result =
        new DashboardFacade(portfolioService, benchmarkService, new DashboardPeriodFilterService())
            .loadDashboard(new DashboardQuery(List.of(), false, "1M"));

    assertEquals(900.0, result.overview().balance());
    assertEquals(200.0, result.overview().totalProfit());
    assertEquals(8_888.0, result.overview().netDeposits());
    assertEquals(0.0, result.overview().deposits());
    assertEquals(0.0, result.overview().withdrawals());
    assertEquals(200.0, result.performance().summary().portfolioPl());
    assertEquals(2, result.performance().benchmark().labels().size());
  }

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
    when(portfolioService.calculateTotalProfitLoss()).thenReturn(portfolio);
    when(benchmarkService.calculate()).thenReturn(benchmark);

    DashboardPageView result =
        new DashboardFacade(portfolioService, benchmarkService, new DashboardPeriodFilterService())
            .loadDashboard(new DashboardQuery(List.of(), false, "MAX"));

    assertEquals(0.0, result.performance().summary().currentDrawdownPct());
    assertEquals(-4.545, result.performance().summary().maxDrawdownPct(), 0.001);
    assertEquals(2.3, result.overview().incomeYieldPct(), 0.03);
    assertEquals("High", result.overview().riskExposure().concentrationWarning());
  }

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
    when(portfolioService.calculateTotalProfitLoss()).thenReturn(portfolio);
    when(benchmarkService.calculate()).thenReturn(benchmark);

    DashboardFacade facade =
        new DashboardFacade(portfolioService, benchmarkService, new DashboardPeriodFilterService());
    DashboardPageView oneMonth = facade.loadDashboard(new DashboardQuery(List.of(), false, "1M"));
    DashboardPageView threeMonths =
        facade.loadDashboard(new DashboardQuery(List.of(), false, "3M"));
    DashboardPageView max = facade.loadDashboard(new DashboardQuery(List.of(), false, "MAX"));

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
      assertTrue(html.contains("By currency"));
      assertFalse(html.contains("selectedPeriod.label() + ' · before capital-gains tax'"));
      assertTrue(html.contains("after withholding tax · yield"));
      assertTrue(
          html.contains(
              "selectedPeriod == T(com.smartbox.investory.investment.reporting.dashboard.service.DashboardPeriod).MAX"));
      assertTrue(
          html.contains("fragments/app-header :: appNavigation('investment', ${portfolioId})"));
      assertFalse(html.contains("iv-risk-summary__links"));
      assertTrue(html.contains("iv-realized-details"));
      assertTrue(html.contains("iv-realized-attribution-popover"));
      assertFalse(html.contains("dashboard/fragments/positions :: heading"));
      assertFalse(html.contains("iv-attribution-popover"));
    }
  }

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

    when(portfolioService.calculateTotalProfitLoss()).thenReturn(portfolio);
    when(benchmarkService.calculate()).thenReturn(benchmark);

    DashboardPageView result =
        new DashboardFacade(portfolioService, benchmarkService, new DashboardPeriodFilterService())
            .loadDashboard(new DashboardQuery(List.of(), false, "YTD"));
    DashboardPageView maxResult =
        new DashboardFacade(portfolioService, benchmarkService, new DashboardPeriodFilterService())
            .loadDashboard(new DashboardQuery(List.of(), false, "MAX"));

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
}
