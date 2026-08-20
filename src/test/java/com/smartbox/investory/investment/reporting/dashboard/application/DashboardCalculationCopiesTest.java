package com.smartbox.investory.investment.reporting.dashboard.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.investment.accounting.model.models.Performance;
import com.smartbox.investory.investment.accounting.model.models.Portfolio;
import com.smartbox.investory.investment.reporting.dashboard.service.DashboardPeriod;
import com.smartbox.investory.investment.reporting.dashboard.service.DashboardPeriodFilterService;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

class DashboardCalculationCopiesTest {

  @Test
  void periodFilteringCopyDoesNotMutateCachedBasePerformance() {
    Portfolio cached = new Portfolio();
    Performance performance = new Performance();
    performance.setCalculateMonthlyPerformance(
        new LinkedHashMap<>(java.util.Map.of("2025-01", 1.0, "2026-01", 2.0)));
    cached.setMonthlyPerformance(performance);

    Portfolio dashboard = DashboardCalculationCopies.portfolio(cached);
    new DashboardPeriodFilterService().apply(dashboard, DashboardPeriod.YEAR_TO_DATE);

    assertThat(dashboard.getMonthlyPerformance().getCalculateMonthlyPerformance())
        .containsKey("2026-01");
    assertThat(cached.getMonthlyPerformance().getCalculateMonthlyPerformance())
        .containsKeys("2025-01", "2026-01");
  }
}
