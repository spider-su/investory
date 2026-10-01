package com.smartbox.investory.investment.reporting.dashboard.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.investment.api.reporting.model.*;
import com.smartbox.investory.investment.performance.model.Performance;
import com.smartbox.investory.investment.performance.model.Portfolio;
import com.smartbox.investory.investment.reporting.dashboard.service.DashboardPeriodFilterService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
        new DashboardPeriodFilterService("2020-01-01").filter(cached, DashboardPeriod.YEAR_TO_DATE);

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
        new DashboardPeriodFilterService(
                LocalDate.of(2020, 1, 1),
                Clock.fixed(
                    Instant.parse("2026-10-01T10:00:00Z"), ZoneId.of("Europe/Warsaw")))
            .filter(cached, DashboardPeriod.YEAR_TO_DATE);

    assertThat(dashboard.isBenchmarkAvailable()).isFalse();
    assertThat(dashboard.getBenchmarkReturnPct()).isNull();
    assertThat(dashboard.getAlpha()).isZero();
  }
}
