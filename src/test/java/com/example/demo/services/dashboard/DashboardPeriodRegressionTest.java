package com.example.demo.services.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.services.models.Benchmark;
import com.example.demo.services.models.Performance;
import com.example.demo.services.models.Portfolio;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardPeriodRegressionTest {

  private final DashboardPeriodFilterService service = new DashboardPeriodFilterService();

  @Test
  void missingOrInvalidPeriodDefaultsToOneYear() {
    assertEquals(DashboardPeriod.ONE_YEAR, DashboardPeriod.fromUrlValue(null));
    assertEquals(DashboardPeriod.ONE_YEAR, DashboardPeriod.fromUrlValue(""));
    assertEquals(DashboardPeriod.ONE_YEAR, DashboardPeriod.fromUrlValue("unsupported"));
  }

  @Test
  void filtersMonthlyAndDailyLabelsFromTheSamePeriodBoundary() {
    int currentYear = Year.now().getValue();
    String previousDecember = YearMonth.of(currentYear - 1, 12).toString();
    String currentJanuary = YearMonth.of(currentYear, 1).toString();
    String currentFebruary = YearMonth.of(currentYear, 2).toString();

    Performance performance = new Performance();
    performance.setCalculateMonthlyPerformance(
        linkedMap(previousDecember, 10.0, currentJanuary, 20.0, currentFebruary, 30.0));
    Portfolio portfolio = new Portfolio();
    portfolio.setMonthlyPerformance(performance);

    service.apply(portfolio, DashboardPeriod.YEAR_TO_DATE);

    assertEquals(
        List.of(currentJanuary, currentFebruary),
        performance.getCalculateMonthlyPerformance().keySet().stream().toList());
    assertEquals(50.0, performance.getTotalProfit());

    String previousDay = LocalDate.of(currentYear - 1, 12, 31).toString();
    String januaryDay = LocalDate.of(currentYear, 1, 15).toString();
    String februaryDay = LocalDate.of(currentYear, 2, 15).toString();

    Benchmark benchmark =
        benchmark(
            List.of(previousDecember, currentJanuary, currentFebruary),
            List.of(10.0, 30.0, 60.0),
            List.of(5.0, 15.0, 25.0));
    benchmark.setAccountValueYears(
        List.of(
            new Benchmark.AccountValueYear(
                currentYear,
                List.of(previousDay, januaryDay, februaryDay),
                List.of(
                    new Benchmark.AccountValueSeries(
                        1L, "PLN account", List.of(1.0, 3.0, 6.0), List.of(1.0, 3.0, 6.0))),
                List.of(1.0, 3.0, 6.0),
                List.of(1.0, 3.0, 6.0))));

    service.apply(benchmark, DashboardPeriod.YEAR_TO_DATE);

    assertEquals(List.of(currentJanuary, currentFebruary), benchmark.getLabels());
    assertEquals(
        List.of(previousDay, januaryDay, februaryDay),
        benchmark.getAccountValueYears().getFirst().labels());
  }

  @Test
  void shorterPeriodReturnsOnlyExpectedLabels() {
    YearMonth now = YearMonth.now();
    String tooOld = now.minusMonths(2).toString();
    String boundary = now.minusMonths(1).toString();
    String current = now.toString();

    Benchmark benchmark =
        benchmark(
            List.of(tooOld, boundary, current),
            List.of(10.0, 20.0, 35.0),
            List.of(5.0, 10.0, 15.0));

    service.apply(benchmark, DashboardPeriod.ONE_MONTH);

    assertEquals(List.of(boundary, current), benchmark.getLabels());
    assertEquals(List.of(10.0, 25.0), benchmark.getPortfolioCurve());
    assertEquals(2, benchmark.getBenchmarkCurve().size());
  }

  @Test
  void plnBenchmarkUsesPortfolioBaseCashFlows() throws IOException {
    double openingEquityUsd = 10_000.0;
    double closingEquityUsd = 11_000.0;
    double depositPln = 4_000.0;
    double depositUsd = 1_000.0;

    double correctProfitUsd = closingEquityUsd - openingEquityUsd - depositUsd;
    double mixedCurrencyProfit = closingEquityUsd - openingEquityUsd - depositPln;

    assertEquals(0.0, correctProfitUsd);
    assertEquals(-3_000.0, mixedCurrencyProfit);

    String migration;
    try (var stream =
        getClass()
            .getResourceAsStream("/sql/migration/V01.002__checks_and_views.sql")) {
      assertTrue(stream != null, "checks and views migration must be present");
      migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }

    int benchmarkStart =
        migration.indexOf("CREATE OR REPLACE VIEW investory.account_monthly_benchmark");
    int benchmarkEnd = migration.indexOf("CREATE TABLE investory.materialized_view_refresh_history");
    assertTrue(benchmarkStart >= 0, "benchmark view must be present");
    assertTrue(benchmarkEnd > benchmarkStart, "benchmark view section must be bounded");
    String benchmarkView = migration.substring(benchmarkStart, benchmarkEnd);
    assertTrue(benchmarkView.contains("amount_in_portfolio_base_currency"));
    assertFalse(benchmarkView.contains("amount_in_account_currency"));
  }

  private Benchmark benchmark(
      List<String> labels, List<Double> portfolioCurve, List<Double> benchmarkCurve) {
    Benchmark benchmark = new Benchmark();
    benchmark.setAvailable(true);
    benchmark.setLabels(labels);
    benchmark.setPortfolioCurve(portfolioCurve);
    benchmark.setBenchmarkCurve(benchmarkCurve);
    benchmark.setInvestedCapital(100.0);
    benchmark.setAccountSeries(
        List.of(
            new Benchmark.AccountSeries(
                1L,
                100.0,
                portfolioCurve.getLast(),
                benchmarkCurve.getLast(),
                portfolioCurve,
                benchmarkCurve)));
    return benchmark;
  }

  private LinkedHashMap<String, Double> linkedMap(
      String firstKey,
      double firstValue,
      String secondKey,
      double secondValue,
      String thirdKey,
      double thirdValue) {
    LinkedHashMap<String, Double> values = new LinkedHashMap<>();
    values.put(firstKey, firstValue);
    values.put(secondKey, secondValue);
    values.put(thirdKey, thirdValue);
    return values;
  }
}
