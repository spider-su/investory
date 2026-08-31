package com.smartbox.investory.investment.reporting.dashboard.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.investment.api.reporting.model.BenchmarkView;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Benchmark View")
class BenchmarkViewTest {

  @DisplayName("preserves Unavailable Benchmark Points In Long Range Series")
  @Test
  void preservesUnavailableBenchmarkPointsInLongRangeSeries() {
    BenchmarkView view =
        new BenchmarkView(
            true,
            true,
            false,
            "SPY",
            List.of("2025-01", "2025-02"),
            Arrays.asList(10.0, null),
            Arrays.asList(8.0, null),
            Arrays.asList(1.0, null),
            Arrays.asList(0.8, null),
            1_000.0,
            10.0,
            null,
            1.0,
            null,
            0.0,
            List.of(),
            List.of(),
            false,
            null,
            List.of());

    assertEquals(Arrays.asList(1.0, null), view.portfolioReturnCurve());
    assertEquals(Arrays.asList(0.8, null), view.benchmarkReturnCurve());
  }
}
