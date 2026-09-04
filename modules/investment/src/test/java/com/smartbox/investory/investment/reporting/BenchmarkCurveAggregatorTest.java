package com.smartbox.investory.investment.reporting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.investment.api.reporting.model.Benchmark;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class BenchmarkCurveAggregatorTest {

  @Test
  void aggregatesPortfolioAndNullableBenchmarkPointsByMonth() {
    Benchmark.AccountSeries first =
        new Benchmark.AccountSeries(
            1L,
            100.0,
            30.0,
            10.0,
            List.of(10.0, 20.0),
            Arrays.asList(5.0, null),
            List.of(),
            List.of(),
            List.of());
    Benchmark.AccountSeries second =
        new Benchmark.AccountSeries(
            2L,
            200.0,
            7.0,
            3.0,
            Arrays.asList(1.0, 7.0),
            Arrays.asList(null, 3.0),
            List.of(),
            List.of(),
            List.of());

    BenchmarkCurveAggregator.AggregatedCurves result =
        BenchmarkCurveAggregator.aggregate(List.of(first, second), 2);

    assertEquals(List.of(11.0, 27.0), result.portfolio());
    assertEquals(List.of(5.0, 3.0), result.benchmark());
  }
}
