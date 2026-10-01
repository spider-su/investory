package com.smartbox.investory.investment.reporting.dashboard.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.api.reporting.InvestmentPerformanceApi.PerformanceBoardQuery;
import com.smartbox.investory.investment.api.reporting.InvestmentPerformanceApi.PerformanceBoardView;
import com.smartbox.investory.investment.api.reporting.PerformanceAggregation;
import com.smartbox.investory.investment.api.reporting.PerformanceMetric;
import com.smartbox.investory.investment.api.reporting.PerformanceStyle;
import com.smartbox.investory.investment.api.reporting.model.Benchmark;
import com.smartbox.investory.investment.reporting.BenchmarkService;
import com.smartbox.investory.shared.time.ClockApplicationTime;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("Investment Performance Application Service")
class InvestmentPerformanceApplicationServiceTest {
  private static final ClockApplicationTime TIME =
      new ClockApplicationTime(
          Clock.fixed(Instant.parse("2026-09-05T08:00:00Z"), ZoneOffset.UTC),
          ZoneId.of("Europe/Warsaw"));

  @Mock private BenchmarkService benchmarkService;

  @DisplayName("empty Account Selection Means All Accounts")
  @Test
  void emptyAccountSelectionMeansAllAccounts() {
    Benchmark benchmark = benchmark();
    when(benchmarkService.calculate(1L, null)).thenReturn(benchmark);
    InvestmentPerformanceApplicationService service = service("2026-01");

    PerformanceBoardView view =
        service.load(
            new PerformanceBoardQuery(
                List.of(),
                PerformanceAggregation.MONTHLY,
                PerformanceMetric.RETURN,
                PerformanceStyle.LINE,
                null,
                1L));

    verify(benchmarkService).calculate(1L, null);
    assertEquals("Portfolio", view.series().getFirst().label());
  }

  @DisplayName("chart And Kpis Use The Configured Kpi Start")
  @Test
  void chartAndKpisUseTheConfiguredKpiStart() {
    Benchmark benchmark = benchmark();
    when(benchmarkService.calculate(1L, null)).thenReturn(benchmark);
    InvestmentPerformanceApplicationService service =
        new InvestmentPerformanceApplicationService(benchmarkService, TIME);
    ReflectionTestUtils.setField(service, "kpiStart", "2026-01");

    PerformanceBoardView view =
        service.load(
            new PerformanceBoardQuery(
                null,
                PerformanceAggregation.MONTHLY,
                PerformanceMetric.RETURN,
                PerformanceStyle.LINE,
                null,
                1L));

    assertEquals(List.of("2026-01", "2026-02"), view.labels());
    assertEquals(List.of(bd(10), bd(21)), view.series().getFirst().values());
    assertEquals(List.of(bd(5), bd(10.25)), view.benchmarkValues());
    assertEquals(bd(21), view.kpis().portfolioReturn());
    assertEquals(bd(10.25), view.kpis().benchmarkReturn());
    assertEquals("2026-01", view.kpis().bestPeriod());
    assertEquals("2026-01", view.kpis().worstPeriod());
  }

  @DisplayName("line Returns Stay Aligned With Quarterly Labels For Other Periods")
  @Test
  void lineReturnsStayAlignedWithQuarterlyLabelsForOtherPeriods() {
    Benchmark benchmark = benchmark();
    benchmark.setPortfolioReturnCurve(List.of(0.0, 10.0, 21.0));
    benchmark.setBenchmarkReturnCurve(List.of(0.0, 5.0, 10.25));
    when(benchmarkService.calculate(1L, null)).thenReturn(benchmark);

    PerformanceBoardView view =
        service("2025-12")
            .load(
                new PerformanceBoardQuery(
                    null,
                    PerformanceAggregation.QUARTERLY,
                    PerformanceMetric.RETURN,
                    PerformanceStyle.LINE,
                    com.smartbox.investory.investment.api.reporting.DashboardPeriod.ONE_YEAR,
                    1L));

    assertEquals(List.of("2025-Q4", "2026-Q1"), view.labels());
    assertEquals(view.labels().size(), view.series().getFirst().values().size());
    assertEquals(view.labels().size(), view.benchmarkValues().size());
    assertEquals(bd(21), view.series().getFirst().values().getLast());
    assertEquals(bd(10.25), view.benchmarkValues().getLast());
  }

  @DisplayName("current Partial Month Is Excluded From Chart And Period Kpis")
  @Test
  void currentPartialMonthIsExcludedFromChartAndPeriodKpis() {
    Benchmark benchmark = benchmark();
    benchmark.setLabels(List.of("2026-09", "2026-10"));
    benchmark.setPortfolioCurve(List.of(100.0, 200.0));
    benchmark.setBenchmarkCurve(List.of(50.0, 75.0));
    benchmark.setPortfolioReturnCurve(List.of(10.0, 25.0));
    benchmark.setBenchmarkReturnCurve(List.of(5.0, 15.0));
    benchmark.setAccountSeries(
        List.of(
            new Benchmark.AccountSeries(
                1L,
                1_000.0,
                300.0,
                150.0,
                List.of(100.0, 200.0),
                List.of(50.0, 75.0),
                List.of(1_000.0, 1_100.0),
                List.of(10.0, 10.0),
                List.of(10.0, 21.0))));
    when(benchmarkService.calculate(1L, null)).thenReturn(benchmark);
    ClockApplicationTime octoberTime =
        new ClockApplicationTime(
            Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC),
            ZoneId.of("Europe/Warsaw"));
    InvestmentPerformanceApplicationService service =
        new InvestmentPerformanceApplicationService(benchmarkService, octoberTime);
    ReflectionTestUtils.setField(service, "kpiStart", "2026-01");

    PerformanceBoardView view =
        service.load(
            new PerformanceBoardQuery(
                null,
                PerformanceAggregation.MONTHLY,
                PerformanceMetric.RETURN,
                PerformanceStyle.LINE,
                com.smartbox.investory.investment.api.reporting.DashboardPeriod.YEAR_TO_DATE,
                1L));

    assertEquals(List.of("2026-09"), view.labels());
    assertEquals(List.of(bd(5)), view.benchmarkValues());
    assertEquals(bd(10), view.kpis().portfolioReturn());
    assertEquals(bd(5), view.kpis().benchmarkReturn());
  }

  @DisplayName("missing Profit Boundary Returns Unavailable")
  @Test
  void missingProfitBoundaryReturnsUnavailable() {
    Benchmark benchmark = benchmark();
    Benchmark.AccountSeries row = benchmark.getAccountSeries().getFirst();
    benchmark.setAccountSeries(
        List.of(
            new Benchmark.AccountSeries(
                row.id(),
                row.investedCapital(),
                row.portfolioPl(),
                row.benchmarkPl(),
                java.util.Arrays.asList(100.0, null, 300.0),
                row.benchmarkCurve(),
                row.returnCapitalCurve(),
                row.returnContributionCurve(),
                row.returnPctCurve())));
    when(benchmarkService.calculate(1L, null)).thenReturn(benchmark);
    InvestmentPerformanceApplicationService service = service("2026-02");

    PerformanceBoardView view =
        service.load(
            new PerformanceBoardQuery(
                null,
                PerformanceAggregation.MONTHLY,
                PerformanceMetric.RETURN,
                PerformanceStyle.LINE,
                null,
                1L));

    assertNull(view.kpis().portfolioProfitLoss());
  }

  @DisplayName("unavailable Quarterly Buckets Remain Unavailable")
  @Test
  void unavailableQuarterlyBucketsRemainUnavailable() {
    Benchmark benchmark = benchmark();
    benchmark.setPortfolioReturnCurve(java.util.Arrays.asList(10.0, 21.0, null));
    benchmark.setBenchmarkReturnCurve(java.util.Arrays.asList(5.0, 10.25, null));
    benchmark.setPortfolioCurve(java.util.Arrays.asList(100.0, 120.0, null));
    when(benchmarkService.calculate(1L, null)).thenReturn(benchmark);
    InvestmentPerformanceApplicationService service = service("2026-01");

    PerformanceBoardView returns =
        service.load(
            new PerformanceBoardQuery(
                null,
                PerformanceAggregation.QUARTERLY,
                PerformanceMetric.RETURN,
                PerformanceStyle.BARS,
                null,
                1L));
    PerformanceBoardView profit =
        service.load(
            new PerformanceBoardQuery(
                null,
                PerformanceAggregation.QUARTERLY,
                PerformanceMetric.PROFIT,
                PerformanceStyle.BARS,
                null,
                1L));

    assertNull(returns.series().getFirst().values().getFirst());
    assertNull(returns.benchmarkValues().getFirst());
    assertNull(profit.series().getFirst().values().getFirst());
  }

  @DisplayName("missing Scope Boundary Does Not Crash Aggregated Bars")
  @Test
  void missingScopeBoundaryDoesNotCrashAggregatedBars() {
    Benchmark benchmark = benchmark();
    benchmark.setPortfolioReturnCurve(java.util.Arrays.asList(null, 21.0, 33.1));
    benchmark.setBenchmarkReturnCurve(java.util.Arrays.asList(null, 10.25, 15.7625));
    when(benchmarkService.calculate(1L, null)).thenReturn(benchmark);
    InvestmentPerformanceApplicationService service = service("2026-01");

    PerformanceBoardView view =
        service.load(
            new PerformanceBoardQuery(
                null,
                PerformanceAggregation.QUARTERLY,
                PerformanceMetric.RETURN,
                PerformanceStyle.BARS,
                null,
                1L));

    assertEquals(List.of("2026-Q1"), view.labels());
    assertNull(view.series().getFirst().values().getFirst());
    assertNull(view.benchmarkValues().getFirst());
  }

  @DisplayName("selected Account Return Stays Unavailable After Missing Month")
  @Test
  void selectedAccountReturnStaysUnavailableAfterMissingMonth() {
    Benchmark benchmark = benchmark();
    Benchmark.AccountSeries row = benchmark.getAccountSeries().getFirst();
    benchmark.setAccountOptions(
        List.of(
            new Benchmark.AccountOption(1L, "Broker", true),
            new Benchmark.AccountOption(2L, "Other", false)));
    benchmark.setAccountSeries(
        List.of(
            new Benchmark.AccountSeries(
                row.id(),
                row.investedCapital(),
                row.portfolioPl(),
                row.benchmarkPl(),
                row.portfolioCurve(),
                row.benchmarkCurve(),
                List.of(1_000.0, 1_100.0, 1_200.0),
                row.returnContributionCurve(),
                java.util.Arrays.asList(10.0, null, 10.0))));
    when(benchmarkService.calculate(1L, List.of(1L))).thenReturn(benchmark);
    InvestmentPerformanceApplicationService service = service("2025-12");

    PerformanceBoardView view =
        service.load(
            new PerformanceBoardQuery(
                List.of(1L),
                PerformanceAggregation.MONTHLY,
                PerformanceMetric.RETURN,
                PerformanceStyle.LINE,
                null,
                1L));

    assertEquals(java.util.Arrays.asList(bd(10), null, null), view.series().getFirst().values());
  }

  @DisplayName("selected Account Return Can Start After Pre Inception Months")
  @Test
  void selectedAccountReturnCanStartAfterPreInceptionMonths() {
    Benchmark benchmark = benchmark();
    Benchmark.AccountSeries row = benchmark.getAccountSeries().getFirst();
    benchmark.setAccountOptions(
        List.of(
            new Benchmark.AccountOption(1L, "Broker", true),
            new Benchmark.AccountOption(2L, "Other", false)));
    benchmark.setAccountSeries(
        List.of(
            new Benchmark.AccountSeries(
                row.id(),
                row.investedCapital(),
                row.portfolioPl(),
                row.benchmarkPl(),
                row.portfolioCurve(),
                row.benchmarkCurve(),
                java.util.Arrays.asList(null, 1_000.0, 1_100.0),
                row.returnContributionCurve(),
                java.util.Arrays.asList(null, 10.0, 10.0))));
    when(benchmarkService.calculate(1L, List.of(1L))).thenReturn(benchmark);
    InvestmentPerformanceApplicationService service = service("2025-12");

    PerformanceBoardView view =
        service.load(
            new PerformanceBoardQuery(
                List.of(1L),
                PerformanceAggregation.MONTHLY,
                PerformanceMetric.RETURN,
                PerformanceStyle.LINE,
                null,
                1L));

    assertEquals(java.util.Arrays.asList(null, bd(10), bd(21)), view.series().getFirst().values());
  }

  @DisplayName("configured Start After History Returns Unavailable Kpis")
  @Test
  void configuredStartAfterHistoryReturnsUnavailableKpis() {
    Benchmark benchmark = benchmark();
    when(benchmarkService.calculate(1L, null)).thenReturn(benchmark);
    InvestmentPerformanceApplicationService service = service("2099-01");

    PerformanceBoardView view =
        service.load(
            new PerformanceBoardQuery(
                null,
                PerformanceAggregation.MONTHLY,
                PerformanceMetric.RETURN,
                PerformanceStyle.LINE,
                null,
                1L));

    assertEquals(List.of(), view.labels());
    assertNull(view.kpis().portfolioReturn());
    assertNull(view.kpis().benchmarkReturn());
    assertNull(view.kpis().portfolioProfitLoss());
  }

  @DisplayName("selected Dashboard Period Overrides Configured Kpi Start")
  @Test
  void selectedDashboardPeriodOverridesConfiguredKpiStart() {
    Benchmark benchmark = benchmark();
    when(benchmarkService.calculate(1L, null)).thenReturn(benchmark);
    InvestmentPerformanceApplicationService service = service("2099-01");

    PerformanceBoardView view =
        service.load(
            new PerformanceBoardQuery(
                null,
                PerformanceAggregation.MONTHLY,
                PerformanceMetric.RETURN,
                PerformanceStyle.LINE,
                com.smartbox.investory.investment.api.reporting.DashboardPeriod.MAX,
                1L));

    assertEquals(benchmark.getLabels(), view.labels());
    assertEquals(
        benchmark.getPortfolioReturnCurve().stream()
            .map(InvestmentPerformanceApplicationServiceTest::bd)
            .toList(),
        view.series().getFirst().values());
  }

  @DisplayName("subset Kpis Use The Aggregate Selected Portfolio")
  @Test
  void subsetKpisUseTheAggregateSelectedPortfolio() {
    Benchmark benchmark = benchmark();
    Benchmark.AccountSeries first = benchmark.getAccountSeries().getFirst();
    benchmark.setAccountOptions(
        List.of(
            new Benchmark.AccountOption(1L, "First", true),
            new Benchmark.AccountOption(2L, "Second", true),
            new Benchmark.AccountOption(3L, "Other", false)));
    benchmark.setAccountSeries(
        List.of(
            first,
            new Benchmark.AccountSeries(
                2L,
                first.investedCapital(),
                first.portfolioPl(),
                first.benchmarkPl(),
                first.portfolioCurve(),
                first.benchmarkCurve(),
                first.returnCapitalCurve(),
                first.returnContributionCurve(),
                List.of(5.0, 5.0, 5.0))));
    benchmark.setPortfolioReturnCurve(List.of(100.0, 130.0, 160.0));
    when(benchmarkService.calculate(1L, List.of(1L, 2L))).thenReturn(benchmark);
    InvestmentPerformanceApplicationService service = service("2026-01");

    PerformanceBoardView view =
        service.load(
            new PerformanceBoardQuery(
                List.of(1L, 2L),
                PerformanceAggregation.MONTHLY,
                PerformanceMetric.RETURN,
                PerformanceStyle.LINE,
                null,
                1L));

    assertEquals(bd(30), view.kpis().portfolioReturn());
    assertEquals(bd(19.75), view.excessValues().getLast());
  }

  private static BigDecimal bd(double value) {
    return BigDecimal.valueOf(value);
  }

  private InvestmentPerformanceApplicationService service(String start) {
    InvestmentPerformanceApplicationService service =
        new InvestmentPerformanceApplicationService(benchmarkService, TIME);
    ReflectionTestUtils.setField(service, "kpiStart", start);
    return service;
  }

  private Benchmark benchmark() {
    Benchmark benchmark = new Benchmark();
    benchmark.setAvailable(true);
    benchmark.setPortfolioPerformanceAvailable(true);
    benchmark.setBenchmarkAvailable(true);
    benchmark.setLabels(List.of("2025-12", "2026-01", "2026-02"));
    benchmark.setPortfolioCurve(List.of(100.0, 200.0, 300.0));
    benchmark.setBenchmarkCurve(List.of(50.0, 100.0, 150.0));
    benchmark.setPortfolioReturnCurve(List.of(100.0, 120.0, 142.0));
    benchmark.setBenchmarkReturnCurve(List.of(50.0, 57.5, 65.375));
    benchmark.setAccountOptions(List.of(new Benchmark.AccountOption(1L, "Broker", true)));
    benchmark.setAccountSeries(
        List.of(
            new Benchmark.AccountSeries(
                1L,
                1_000.0,
                300.0,
                150.0,
                List.of(100.0, 200.0, 300.0),
                List.of(50.0, 100.0, 150.0),
                List.of(1_000.0, 1_100.0, 1_210.0),
                List.of(10.0, 10.0, 10.0),
                List.of(10.0, 21.0, 33.1))));
    return benchmark;
  }
}
