package com.smartbox.investory.investment.reporting.dashboard.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
  }

  private Benchmark benchmark() {
    Benchmark benchmark = new Benchmark();
    benchmark.setAvailable(true);
    benchmark.setPortfolioPerformanceAvailable(true);
    benchmark.setBenchmarkAvailable(true);
    benchmark.setLabels(List.of("2025-12", "2026-01", "2026-02"));
    benchmark.setPortfolioCurve(List.of(100.0, 200.0, 300.0));
    benchmark.setBenchmarkCurve(List.of(50.0, 100.0, 150.0));
    benchmark.setPortfolioReturnCurve(List.of(10.0, 21.0, 33.1));
    benchmark.setBenchmarkReturnCurve(List.of(5.0, 10.25, 15.7625));
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
