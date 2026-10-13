package com.smartbox.investory.investment.reporting.dashboard.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.investment.api.reporting.model.*;
import com.smartbox.investory.investment.performance.model.Performance;
import com.smartbox.investory.investment.performance.model.Portfolio;
import com.smartbox.investory.investment.reporting.dashboard.service.DashboardBenchmarkPeriodService;
import com.smartbox.investory.investment.reporting.dashboard.service.DashboardPeriodViewService;
import com.smartbox.investory.investment.reporting.dashboard.service.DashboardPortfolioPeriodService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

@DisplayName("Dashboard Period Projection")
class DashboardPeriodProjectionTest {

  @DisplayName("period Filtering Copy Does Not Mutate Cached Base Performance")
  @Test
  void periodFilteringCopyDoesNotMutateCachedBasePerformance() {
    Portfolio cached = new Portfolio();
    Performance performance = new Performance();
    performance.setCalculateMonthlyPerformance(
        new LinkedHashMap<>(java.util.Map.of("2025-01", 1.0, "2026-01", 2.0)));
    cached.setMonthlyPerformance(performance);

    Portfolio dashboard =
        new DashboardPeriodViewService("2020-01-01").filter(cached, DashboardPeriod.YEAR_TO_DATE);

    assertThat(dashboard.getMonthlyPerformance().getCalculateMonthlyPerformance())
        .containsKey("2026-01");
    assertThat(cached.getMonthlyPerformance().getCalculateMonthlyPerformance())
        .containsKeys("2025-01", "2026-01");
  }

  @DisplayName("period Filtering Handles Missing Final Benchmark Return")
  @Test
  void periodFilteringHandlesMissingFinalBenchmarkReturn() {
    Benchmark cached = new Benchmark();
    cached.setAvailable(true);
    cached.setLabels(java.util.List.of("2026-01", "2026-10"));
    cached.setPortfolioCurve(java.util.List.of(10.0, 20.0));
    cached.setBenchmarkCurve(Arrays.asList(5.0, null));
    cached.setPortfolioReturnCurve(java.util.List.of(1.0, 2.0));
    cached.setBenchmarkReturnCurve(Arrays.asList(0.5, null));

    Benchmark dashboard =
        new DashboardPeriodViewService(
                LocalDate.of(2020, 1, 1),
                Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneId.of("Europe/Warsaw")))
            .filter(cached, DashboardPeriod.YEAR_TO_DATE);

    assertThat(dashboard.isBenchmarkAvailable()).isFalse();
    assertThat(dashboard.getBenchmarkReturnPct()).isNull();
    assertThat(dashboard.getAlpha()).isZero();
  }

  @Test
  void benchmarkPeriodCopiesKeepCachedCurvesAndAccountSeriesUnchanged() {
    Benchmark cached = new Benchmark();
    cached.setAvailable(true);
    cached.setLabels(java.util.List.of("2025-12", "2026-01", "2026-10"));
    cached.setInvestedCapital(100.0);
    cached.setPortfolioCurve(java.util.List.of(10.0, 30.0, 60.0));
    cached.setBenchmarkCurve(Arrays.asList(5.0, 15.0, null));
    cached.setAccountSeries(
        java.util.List.of(
            new Benchmark.AccountSeries(
                1L,
                100.0,
                60.0,
                0.0,
                cached.getPortfolioCurve(),
                cached.getBenchmarkCurve(),
                java.util.List.of(100.0, 110.0, 130.0),
                java.util.List.of(0.0, 5.0, 10.0))));
    DashboardPeriodViewService service =
        new DashboardPeriodViewService(
            LocalDate.of(2020, 1, 1),
            Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneId.of("Europe/Warsaw")));

    Benchmark dashboard = service.filter(cached, DashboardPeriod.YEAR_TO_DATE);

    assertThat(dashboard.getPortfolioCurve()).containsExactly(20.0, 50.0);
    assertThat(dashboard.getAccountSeries().getFirst().portfolioCurve())
        .containsExactly(20.0, 50.0);
    assertThat(dashboard.getAccountSeries().getFirst().returnCapitalCurve())
        .containsExactly(110.0, 130.0);
    assertThat(dashboard.getBenchmarkPl()).isNull();
    assertThat(cached.getLabels()).containsExactly("2025-12", "2026-01", "2026-10");
    assertThat(cached.getPortfolioCurve()).containsExactly(10.0, 30.0, 60.0);
    assertThat(cached.getBenchmarkCurve()).containsExactly(5.0, 15.0, null);
    assertThat(cached.getAccountSeries().getFirst().portfolioCurve())
        .containsExactly(10.0, 30.0, 60.0);
    assertThat(cached.getAccountSeries().getFirst().returnCapitalCurve())
        .containsExactly(100.0, 110.0, 130.0);
    assertThat(service.filter(cached, DashboardPeriod.MAX).getPortfolioCurve())
        .containsExactly(10.0, 30.0, 60.0);
  }

  @Test
  void missingAndUnavailableModelsDoNotRequireAPeriodBoundary() {
    DashboardPeriodViewService service = new DashboardPeriodViewService((LocalDate) null, null);

    assertThat(service.filter((Portfolio) null, null)).isNull();
    assertThat(service.filter((Benchmark) null, null)).isNull();
    Portfolio portfolio = new Portfolio();
    assertThat(service.filter(portfolio, null)).isNotSameAs(portfolio);
    Benchmark benchmark = new Benchmark();
    Benchmark dashboard = service.filter(benchmark, null);
    assertThat(dashboard).isNotSameAs(benchmark);
    assertThat(dashboard.isAvailable()).isFalse();
  }

  @Test
  void springWiringPreservesDefaultHistoryBoundary() {
    try (var context =
        new AnnotationConfigApplicationContext(
            DashboardPeriodViewService.class,
            DashboardPortfolioPeriodService.class,
            DashboardBenchmarkPeriodService.class)) {
      Portfolio cached = new Portfolio();
      Performance performance = new Performance();
      performance.setCalculateMonthlyPerformance(
          new LinkedHashMap<>(java.util.Map.of("2010-01", 2.0)));
      cached.setMonthlyPerformance(performance);

      Portfolio dashboard =
          context.getBean(DashboardPeriodViewService.class).filter(cached, DashboardPeriod.MAX);

      assertThat(dashboard.getMonthlyPerformance().getCalculateMonthlyPerformance())
          .containsEntry("2010-01", 2.0);
      assertThat(dashboard).isNotSameAs(cached);
    }
  }
}
