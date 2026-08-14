package com.smartbox.investory.services.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbox.investory.services.models.Benchmark;
import com.smartbox.investory.services.models.Performance;
import com.smartbox.investory.services.models.Portfolio;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardPeriodRegressionTest {

  private final DashboardPeriodFilterService service = new DashboardPeriodFilterService();

  @Test
  void missingOrInvalidPeriodDefaultsToYearToDate() {
    assertEquals(DashboardPeriod.YEAR_TO_DATE, DashboardPeriod.fromUrlValue(null));
    assertEquals(DashboardPeriod.YEAR_TO_DATE, DashboardPeriod.fromUrlValue(""));
    assertEquals(DashboardPeriod.YEAR_TO_DATE, DashboardPeriod.fromUrlValue("unsupported"));
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
        List.of(januaryDay, februaryDay), benchmark.getAccountValueYears().getFirst().labels());
    assertEquals(
        List.of(2.0, 5.0), benchmark.getAccountValueYears().getFirst().totalProfitValues());
    assertEquals(
        List.of(1.98, 4.95), benchmark.getAccountValueYears().getFirst().totalProfitPctValues());
  }

  @Test
  void rebasesPortfolioAndSpyReturnsToTheSelectedRange() {
    int currentYear = Year.now().getValue();
    String previousDecember = YearMonth.of(currentYear - 1, 12).toString();
    String currentJanuary = YearMonth.of(currentYear, 1).toString();
    String currentFebruary = YearMonth.of(currentYear, 2).toString();
    Benchmark benchmark =
        benchmark(
            List.of(previousDecember, currentJanuary, currentFebruary),
            List.of(10.0, 30.0, 60.0),
            List.of(5.0, 15.0, 25.0));
    benchmark.setPortfolioReturnCurve(List.of(10.0, 21.0, 33.1));
    benchmark.setBenchmarkReturnCurve(List.of(5.0, 15.5, 27.05));

    service.apply(benchmark, DashboardPeriod.YEAR_TO_DATE);

    assertEquals(List.of(currentJanuary, currentFebruary), benchmark.getLabels());
    assertEquals(List.of(20.0, 50.0), benchmark.getPortfolioCurve());
    assertEquals(List.of(10.0, 21.0), benchmark.getPortfolioReturnCurve());
    assertEquals(List.of(10.0, 21.0), benchmark.getBenchmarkReturnCurve());
    assertEquals(21.0, benchmark.getPortfolioReturnPct());
    assertEquals(21.0, benchmark.getBenchmarkReturnPct());
    assertEquals(0.0, benchmark.getAlpha());
  }

  @Test
  void everyVisiblePeriodUsesOneNonEmptyRebasedPerformanceRange() {
    YearMonth firstAvailable = YearMonth.now().minusYears(6);
    List<DashboardPeriod> periods =
        List.of(
            DashboardPeriod.ONE_MONTH,
            DashboardPeriod.THREE_MONTHS,
            DashboardPeriod.SIX_MONTHS,
            DashboardPeriod.YEAR_TO_DATE,
            DashboardPeriod.ONE_YEAR,
            DashboardPeriod.FIVE_YEARS,
            DashboardPeriod.MAX);
    List<Double> profits = new ArrayList<>();

    for (DashboardPeriod period : periods) {
      Benchmark benchmark = benchmarkHistory(firstAvailable, YearMonth.now());
      Portfolio portfolio = portfolioHistory(benchmark.getLabels());

      service.apply(portfolio, period);
      service.apply(benchmark, period);

      assertTrue(benchmark.isAvailable(), period + " must remain available when history overlaps");
      assertFalse(benchmark.getLabels().isEmpty(), period + " must retain chart labels");
      assertEquals(
          expectedFirstLabel(period, firstAvailable),
          benchmark.getLabels().getFirst(),
          period + " start");
      assertEquals(benchmark.getPortfolioCurve().getLast(), benchmark.getPortfolioPl());
      assertEquals(
          benchmark.getPortfolioReturnCurve().getLast(), benchmark.getPortfolioReturnPct());
      assertEquals(
          benchmark.getBenchmarkReturnCurve().getLast(), benchmark.getBenchmarkReturnPct());
      assertEquals(
          benchmark.getPortfolioReturnPct() - benchmark.getBenchmarkReturnPct(),
          benchmark.getAlpha(),
          0.001);
      assertFalse(portfolio.getMonthlyPerformance().getCalculateMonthlyPerformance().isEmpty());
      assertEquals(
          benchmark.getLabels(),
          portfolio.getMonthlyPerformance().getCalculateMonthlyPerformance().keySet().stream()
              .toList());
      profits.add(benchmark.getPortfolioPl());
    }

    assertFalse(profits.get(0).equals(profits.get(1)), "1M and 3M P/L must differ");
    assertFalse(profits.get(1).equals(profits.get(2)), "3M and YTD P/L must differ");
  }

  @Test
  void clampsLongRequestedRangeToFirstAvailableObservation() {
    YearMonth firstAvailable = YearMonth.now().minusMonths(20);
    Benchmark benchmark = benchmarkHistory(firstAvailable, YearMonth.now());

    service.apply(benchmark, DashboardPeriod.FIVE_YEARS);

    assertTrue(benchmark.isAvailable());
    assertEquals(firstAvailable.toString(), benchmark.getLabels().getFirst());
  }

  @Test
  void maxNeverShowsDataBeforeTheFixedPortfolioStart() {
    YearMonth portfolioStart = YearMonth.of(2025, 1);
    Benchmark benchmark = benchmarkHistory(YearMonth.of(2023, 1), YearMonth.of(2026, 8));

    new DashboardPeriodFilterService(portfolioStart.atDay(1), java.time.Clock.systemUTC())
        .apply(benchmark, DashboardPeriod.MAX);

    assertEquals(portfolioStart.toString(), benchmark.getLabels().getFirst());
  }

  @Test
  void marksBenchmarkUnavailableOnlyWhenRequestedRangeHasNoObservation() {
    Benchmark benchmark =
        benchmarkHistory(YearMonth.now().minusMonths(4), YearMonth.now().minusMonths(2));

    service.apply(benchmark, DashboardPeriod.ONE_MONTH);

    assertFalse(benchmark.isAvailable());
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
        getClass().getResourceAsStream("/sql/migration/V01.005__portfolio_views.sql")) {
      assertTrue(stream != null, "checks and views migration must be present");
      migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }

    int benchmarkStart =
        migration.indexOf("CREATE OR REPLACE VIEW investory.account_monthly_benchmark");
    int benchmarkEnd =
        migration.indexOf("COMMENT ON VIEW investory.account_monthly_benchmark", benchmarkStart);
    assertTrue(benchmarkStart >= 0, "benchmark view must be present");
    assertTrue(benchmarkEnd > benchmarkStart, "benchmark view section must be bounded");
    String benchmarkView = migration.substring(benchmarkStart, benchmarkEnd);
    assertTrue(benchmarkView.contains("monthly.total_profit"));
    assertTrue(benchmarkView.contains("monthly.compounded_monthly_return"));
    assertFalse(benchmarkView.contains("closing_equity\n        - monthly.opening_equity"));
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
                benchmarkCurve,
                portfolioCurve.stream().map(value -> 100.0).toList(),
                portfolioCurve.stream().map(value -> 0.0).toList())));
    return benchmark;
  }

  private Benchmark benchmarkHistory(YearMonth first, YearMonth last) {
    List<String> labels = new ArrayList<>();
    List<Double> portfolioPl = new ArrayList<>();
    List<Double> benchmarkPl = new ArrayList<>();
    List<Double> portfolioReturn = new ArrayList<>();
    List<Double> benchmarkReturn = new ArrayList<>();
    double portfolioFactor = 1.0;
    double benchmarkFactor = 1.0;
    double profit = 0.0;
    double spyProfit = 0.0;
    for (YearMonth month = first; !month.isAfter(last); month = month.plusMonths(1)) {
      labels.add(month.toString());
      profit += 100.0;
      spyProfit += 50.0;
      portfolioFactor *= 1.02;
      benchmarkFactor *= 1.01;
      portfolioPl.add(profit);
      benchmarkPl.add(spyProfit);
      portfolioReturn.add(round((portfolioFactor - 1.0) * 100.0));
      benchmarkReturn.add(round((benchmarkFactor - 1.0) * 100.0));
    }
    Benchmark benchmark = benchmark(labels, portfolioPl, benchmarkPl);
    benchmark.setPortfolioReturnCurve(portfolioReturn);
    benchmark.setBenchmarkReturnCurve(benchmarkReturn);
    return benchmark;
  }

  private Portfolio portfolioHistory(List<String> labels) {
    Performance performance = new Performance();
    LinkedHashMap<String, Double> values = new LinkedHashMap<>();
    labels.forEach(label -> values.put(label, 100.0));
    performance.setCalculateMonthlyPerformance(values);
    Portfolio portfolio = new Portfolio();
    portfolio.setMonthlyPerformance(performance);
    return portfolio;
  }

  private String expectedFirstLabel(DashboardPeriod period, YearMonth firstAvailable) {
    if (period == DashboardPeriod.MAX) {
      return firstAvailable.toString();
    }
    YearMonth requested = YearMonth.from(period.startDate(ZonedDateTime.now()));
    return (requested.isBefore(firstAvailable) ? firstAvailable : requested).toString();
  }

  private double round(double value) {
    return Math.round(value * 100.0) / 100.0;
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
