package com.smartbox.investory.investment.reporting.dashboard.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.accounting.model.Benchmark;
import com.smartbox.investory.investment.api.InvestmentPerformanceApi.PerformanceBoardQuery;
import com.smartbox.investory.investment.api.InvestmentPerformanceApi.PerformanceBoardView;
import com.smartbox.investory.investment.reporting.BenchmarkService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InvestmentPerformanceApplicationServiceTest {
  @Mock private BenchmarkService benchmarkService;

  @Test
  void chartAndKpisUseTheConfiguredKpiStart() {
    Benchmark benchmark = benchmark();
    when(benchmarkService.calculate()).thenReturn(benchmark);
    InvestmentPerformanceApplicationService service =
        new InvestmentPerformanceApplicationService(benchmarkService);
    ReflectionTestUtils.setField(service, "kpiStart", "2026-01");

    PerformanceBoardView view =
        service.load(new PerformanceBoardQuery(null, "monthly", "return", "line"));

    assertEquals(List.of("2026-01", "2026-02"), view.labels());
    assertEquals(List.of(10.0, 21.0), view.series().getFirst().values());
    assertEquals(List.of(5.0, 10.25), view.benchmarkValues());
    assertEquals(21.0, view.kpis().portfolioReturn());
    assertEquals(10.25, view.kpis().benchmarkReturn());
    assertEquals("2026-01", view.kpis().bestPeriod());
    assertEquals("2026-01", view.kpis().worstPeriod());
  }

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
    when(benchmarkService.calculate()).thenReturn(benchmark);
    InvestmentPerformanceApplicationService service = service("2026-02");

    PerformanceBoardView view =
        service.load(new PerformanceBoardQuery(null, "monthly", "return", "line"));

    assertNull(view.kpis().portfolioProfitLoss());
  }

  @Test
  void unavailableQuarterlyBucketsRemainUnavailable() {
    Benchmark benchmark = benchmark();
    benchmark.setPortfolioReturnCurve(java.util.Arrays.asList(10.0, 21.0, null));
    benchmark.setBenchmarkReturnCurve(java.util.Arrays.asList(5.0, 10.25, null));
    benchmark.setPortfolioCurve(java.util.Arrays.asList(100.0, 120.0, null));
    when(benchmarkService.calculate()).thenReturn(benchmark);
    InvestmentPerformanceApplicationService service = service("2026-01");

    PerformanceBoardView returns =
        service.load(new PerformanceBoardQuery(null, "quarterly", "return", "bars"));
    PerformanceBoardView profit =
        service.load(new PerformanceBoardQuery(null, "quarterly", "profit", "bars"));

    assertNull(returns.series().getFirst().values().getFirst());
    assertNull(returns.benchmarkValues().getFirst());
    assertNull(profit.series().getFirst().values().getFirst());
  }

  @Test
  void configuredStartAfterHistoryReturnsUnavailableKpis() {
    Benchmark benchmark = benchmark();
    when(benchmarkService.calculate()).thenReturn(benchmark);
    InvestmentPerformanceApplicationService service = service("2099-01");

    PerformanceBoardView view =
        service.load(new PerformanceBoardQuery(null, "monthly", "return", "line"));

    assertEquals(List.of(), view.labels());
    assertNull(view.kpis().portfolioReturn());
    assertNull(view.kpis().benchmarkReturn());
    assertNull(view.kpis().portfolioProfitLoss());
  }

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
    when(benchmarkService.calculate(List.of(1L, 2L))).thenReturn(benchmark);
    InvestmentPerformanceApplicationService service = service("2026-01");

    PerformanceBoardView view =
        service.load(new PerformanceBoardQuery(List.of(1L, 2L), "monthly", "return", "line"));

    assertEquals(30.0, view.kpis().portfolioReturn());
    assertEquals(19.75, view.excessValues().getLast());
  }

  private InvestmentPerformanceApplicationService service(String start) {
    InvestmentPerformanceApplicationService service =
        new InvestmentPerformanceApplicationService(benchmarkService);
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
