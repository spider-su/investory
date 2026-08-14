package com.smartbox.investory.services.dashboard;

import com.smartbox.investory.services.models.Benchmark;
import com.smartbox.investory.services.models.Performance;
import com.smartbox.investory.services.models.Portfolio;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DashboardPeriodFilterService {

  private final LocalDate historyStart;
  private final Clock clock;

  public DashboardPeriodFilterService() {
    this(null, Clock.systemDefaultZone());
  }

  @Autowired
  public DashboardPeriodFilterService(
      @Value("${app.history-start:2025-01-01}") String historyStart) {
    this(LocalDate.parse(historyStart), Clock.systemDefaultZone());
  }

  public DashboardPeriodFilterService(LocalDate historyStart, Clock clock) {
    this.historyStart = historyStart;
    this.clock = clock;
  }

  public void apply(Portfolio portfolio, DashboardPeriod period) {
    if (portfolio == null || portfolio.getMonthlyPerformance() == null) {
      return;
    }
    Performance performance = portfolio.getMonthlyPerformance();
    YearMonth start = startMonth(period);
    if (start == null) {
      return;
    }
    performance.setCalculateMonthlyPerformance(
        filter(performance.getCalculateMonthlyPerformance(), start));
    performance.setMonthlyOperationsCount(filter(performance.getMonthlyOperationsCount(), start));
    performance.setMonthlyCashflow(filter(performance.getMonthlyCashflow(), start));
    performance.setMonthlyAttributions(filter(performance.getMonthlyAttributions(), start));
    performance.setTotalProfit(
        performance.getCalculateMonthlyPerformance().values().stream()
            .mapToDouble(Double::doubleValue)
            .sum());
  }

  public void apply(Benchmark benchmark, DashboardPeriod period) {
    if (benchmark == null || !benchmark.isAvailable()) {
      return;
    }
    YearMonth start = startMonth(period);
    if (start == null) {
      refreshSummary(benchmark);
      return;
    }
    int firstIndex = firstIncludedIndex(benchmark.getLabels(), start);
    if (firstIndex < 0) {
      benchmark.setAvailable(false);
      return;
    }

    RebasedSeries total =
        rebase(
            benchmark.getInvestedCapital(),
            benchmark.getPortfolioCurve(),
            benchmark.getBenchmarkCurve(),
            firstIndex);
    List<Double> portfolioReturnCurve =
        rebaseReturnCurve(benchmark.getPortfolioReturnCurve(), firstIndex);
    List<Double> benchmarkReturnCurve =
        rebaseReturnCurve(benchmark.getBenchmarkReturnCurve(), firstIndex);
    benchmark.setLabels(
        List.copyOf(benchmark.getLabels().subList(firstIndex, benchmark.getLabels().size())));
    benchmark.setPortfolioCurve(total.portfolioCurve());
    benchmark.setBenchmarkCurve(total.benchmarkCurve());
    benchmark.setPortfolioReturnCurve(portfolioReturnCurve);
    benchmark.setBenchmarkReturnCurve(benchmarkReturnCurve);
    benchmark.setInvestedCapital(round(total.investedCapital()));
    benchmark.setPortfolioPerformanceAvailable(!benchmark.getPortfolioCurve().isEmpty());
    benchmark.setBenchmarkAvailable(
        benchmarkReturnCurve.stream().anyMatch(java.util.Objects::nonNull));
    refreshSummary(benchmark);
    benchmark.setAccountSeries(
        benchmark.getAccountSeries().stream()
            .map(
                series -> {
                  RebasedSeries rebased =
                      rebase(
                          series.investedCapital(),
                          series.portfolioCurve(),
                          series.benchmarkCurve(),
                          firstIndex);
                  return new Benchmark.AccountSeries(
                      series.id(),
                      round(rebased.investedCapital()),
                      last(rebased.portfolioCurve()),
                      lastOrZero(rebased.benchmarkCurve()),
                      rebased.portfolioCurve(),
                      rebased.benchmarkCurve(),
                      sliceValues(series.returnCapitalCurve(), firstIndex),
                      sliceValues(series.returnContributionCurve(), firstIndex),
                      sliceValues(series.returnPctCurve(), firstIndex));
                })
            .filter(series -> !series.portfolioCurve().isEmpty())
            .toList());
    benchmark.setAccountValueYears(
        benchmark.getAccountValueYears().stream()
            .map(year -> filterAccountValueYear(year, start))
            .filter(year -> !year.labels().isEmpty())
            .toList());
    benchmark.setAccountValuesAvailable(!benchmark.getAccountValueYears().isEmpty());
    benchmark.setSelectedAccountValueYear(
        benchmark.isAccountValuesAvailable()
            ? benchmark.getAccountValueYears().getFirst().year()
            : null);
  }

  private Benchmark.AccountValueYear filterAccountValueYear(
      Benchmark.AccountValueYear year, YearMonth start) {
    int firstIndex = firstIncludedIndex(year.labels(), start);
    if (firstIndex < 0) {
      return new Benchmark.AccountValueYear(
          year.year(), List.of(), List.of(), List.of(), List.of());
    }
    return new Benchmark.AccountValueYear(
        year.year(),
        List.copyOf(year.labels().subList(firstIndex, year.labels().size())),
        year.accountSeries().stream()
            .map(
                series ->
                    new Benchmark.AccountValueSeries(
                        series.id(),
                        series.name(),
                        rebaseValues(series.profitValues(), firstIndex),
                        rebaseReturnCurve(series.profitPctValues(), firstIndex)))
            .toList(),
        rebaseValues(year.totalProfitValues(), firstIndex),
        rebaseReturnCurve(year.totalProfitPctValues(), firstIndex));
  }

  private RebasedSeries rebase(
      double investedCapital,
      List<Double> portfolioCurve,
      List<Double> benchmarkCurve,
      int firstIndex) {
    if (firstIndex >= portfolioCurve.size()) {
      return new RebasedSeries(0.0, List.of(), List.of());
    }
    int priorIndex = firstIndex - 1;
    double priorPortfolioProfit = priorIndex >= 0 ? portfolioCurve.get(priorIndex) : 0.0;
    Double priorBenchmarkProfit =
        priorIndex >= 0 && priorIndex < benchmarkCurve.size()
            ? benchmarkCurve.get(priorIndex)
            : 0.0;
    double periodCapital = investedCapital + priorPortfolioProfit;
    double benchmarkStartValue =
        priorBenchmarkProfit == null ? 0.0 : investedCapital + priorBenchmarkProfit;

    List<Double> portfolio =
        portfolioCurve.subList(firstIndex, portfolioCurve.size()).stream()
            .map(value -> round(value - priorPortfolioProfit))
            .toList();
    List<Double> benchmark =
        (benchmarkCurve.size() <= firstIndex
                ? java.util.stream.Stream.<Double>generate(() -> null).limit(portfolio.size())
                : benchmarkCurve.subList(firstIndex, benchmarkCurve.size()).stream())
            .map(
                value -> {
                  if (value == null || priorBenchmarkProfit == null || benchmarkStartValue == 0.0) {
                    return null;
                  }
                  double benchmarkValue = investedCapital + value;
                  return round(periodCapital * (benchmarkValue / benchmarkStartValue - 1.0));
                })
            .toList();
    return new RebasedSeries(periodCapital, portfolio, benchmark);
  }

  private List<Double> rebaseValues(List<Double> values, int firstIndex) {
    if (firstIndex >= values.size()) {
      return List.of();
    }
    double prior = firstIndex > 0 ? values.get(firstIndex - 1) : 0.0;
    return values.subList(firstIndex, values.size()).stream()
        .map(value -> round(value - prior))
        .toList();
  }

  private void refreshSummary(Benchmark benchmark) {
    benchmark.setPortfolioPl(last(benchmark.getPortfolioCurve()));
    benchmark.setBenchmarkPl(lastNullable(benchmark.getBenchmarkCurve()));
    if (!benchmark.getPortfolioReturnCurve().isEmpty()) {
      benchmark.setPortfolioReturnPct(last(benchmark.getPortfolioReturnCurve()));
      benchmark.setBenchmarkReturnPct(lastNullable(benchmark.getBenchmarkReturnCurve()));
    } else {
      benchmark.setPortfolioReturnPct(
          percent(benchmark.getPortfolioPl(), benchmark.getInvestedCapital()));
      benchmark.setBenchmarkReturnPct(
          benchmark.getBenchmarkPl() == null
              ? null
              : percent(benchmark.getBenchmarkPl(), benchmark.getInvestedCapital()));
    }
    boolean benchmarkDataAvailable =
        benchmark.getBenchmarkReturnCurve().stream().anyMatch(java.util.Objects::nonNull);
    benchmark.setBenchmarkAvailable(benchmarkDataAvailable);
    benchmark.setAlpha(
        benchmarkDataAvailable
            ? round(benchmark.getPortfolioReturnPct() - benchmark.getBenchmarkReturnPct())
            : 0.0);
  }

  private List<Double> rebaseReturnCurve(List<Double> values, int firstIndex) {
    if (values == null || firstIndex >= values.size()) {
      return List.of();
    }
    Double prior = firstIndex > 0 ? values.get(firstIndex - 1) : 0.0;
    double priorFactor = prior == null ? 1.0 : 1.0 + prior / 100.0;
    if (priorFactor == 0.0) {
      return values.subList(firstIndex, values.size()).stream()
          .map(value -> (Double) null)
          .toList();
    }
    return values.subList(firstIndex, values.size()).stream()
        .map(
            value ->
                value == null ? null : round(((1.0 + value / 100.0) / priorFactor - 1.0) * 100.0))
        .toList();
  }

  private List<Double> sliceValues(List<Double> values, int firstIndex) {
    if (firstIndex >= values.size()) {
      return List.of();
    }
    return new java.util.ArrayList<>(values.subList(firstIndex, values.size()));
  }

  private <T> Map<String, T> filter(Map<String, T> values, YearMonth start) {
    if (values == null || values.isEmpty()) {
      return new LinkedHashMap<>();
    }
    Map<String, T> filtered = new LinkedHashMap<>();
    values.forEach(
        (label, value) -> {
          if (!labelMonth(label).isBefore(start)) {
            filtered.put(label, value);
          }
        });
    return filtered;
  }

  private int firstIncludedIndex(List<String> labels, YearMonth start) {
    for (int i = 0; i < labels.size(); i++) {
      if (!labelMonth(labels.get(i)).isBefore(start)) {
        return i;
      }
    }
    return -1;
  }

  private YearMonth labelMonth(String label) {
    try {
      return YearMonth.parse(label);
    } catch (DateTimeParseException ignored) {
      return YearMonth.from(LocalDate.parse(label));
    }
  }

  private YearMonth startMonth(DashboardPeriod period) {
    // Reporting inputs are monthly. A trailing date range includes the calendar month containing
    // its start date, so a 1M selection on 15 August includes the July monthly observation.
    ZonedDateTime now = ZonedDateTime.now(clock);
    ZonedDateTime requested = period.startDate(now);
    YearMonth configured = historyStart == null ? null : YearMonth.from(historyStart);
    return requested == null
        ? configured
        : configured != null && YearMonth.from(requested).isBefore(configured)
            ? configured
            : YearMonth.from(requested);
  }

  private double last(List<Double> values) {
    return values.isEmpty() ? 0.0 : values.getLast();
  }

  private Double lastNullable(List<Double> values) {
    return values == null || values.isEmpty() ? null : values.getLast();
  }

  private double lastOrZero(List<Double> values) {
    Double last = lastNullable(values);
    return last == null ? 0.0 : last;
  }

  private double percent(double value, double base) {
    return base == 0.0 ? 0.0 : round(value / base * 100.0);
  }

  private double round(double value) {
    return Math.round(value * 100.0) / 100.0;
  }

  private record RebasedSeries(
      double investedCapital, List<Double> portfolioCurve, List<Double> benchmarkCurve) {}
}
