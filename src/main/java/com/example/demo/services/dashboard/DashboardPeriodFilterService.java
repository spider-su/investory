package com.example.demo.services.dashboard;

import com.example.demo.services.models.Benchmark;
import com.example.demo.services.models.Performance;
import com.example.demo.services.models.Portfolio;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DashboardPeriodFilterService {

  public void apply(Portfolio portfolio, DashboardPeriod period) {
    if (portfolio == null
        || portfolio.getMonthlyPerformance() == null
        || period == DashboardPeriod.MAX) {
      return;
    }
    Performance performance = portfolio.getMonthlyPerformance();
    YearMonth start = startMonth(period);
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
    if (benchmark == null || !benchmark.isAvailable() || period == DashboardPeriod.MAX) {
      return;
    }
    YearMonth start = startMonth(period);
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
    benchmark.setLabels(
        List.copyOf(benchmark.getLabels().subList(firstIndex, benchmark.getLabels().size())));
    benchmark.setPortfolioCurve(total.portfolioCurve());
    benchmark.setBenchmarkCurve(total.benchmarkCurve());
    benchmark.setInvestedCapital(round(total.investedCapital()));
    benchmark.setPortfolioPl(last(total.portfolioCurve()));
    benchmark.setBenchmarkPl(last(total.benchmarkCurve()));
    benchmark.setPortfolioReturnPct(
        percent(benchmark.getPortfolioPl(), benchmark.getInvestedCapital()));
    benchmark.setBenchmarkReturnPct(
        percent(benchmark.getBenchmarkPl(), benchmark.getInvestedCapital()));
    benchmark.setAlpha(
        round(benchmark.getPortfolioReturnPct() - benchmark.getBenchmarkReturnPct()));
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
                      last(rebased.benchmarkCurve()),
                      rebased.portfolioCurve(),
                      rebased.benchmarkCurve());
                })
            .filter(series -> !series.portfolioCurve().isEmpty())
            .toList());
    // Account value years are calendar-year charts and do not follow the global period.
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
                        rebaseValues(series.profitPctValues(), firstIndex)))
            .toList(),
        rebaseValues(year.totalProfitValues(), firstIndex),
        rebaseValues(year.totalProfitPctValues(), firstIndex));
  }

  private RebasedSeries rebase(
      double investedCapital,
      List<Double> portfolioCurve,
      List<Double> benchmarkCurve,
      int firstIndex) {
    if (firstIndex >= portfolioCurve.size() || firstIndex >= benchmarkCurve.size()) {
      return new RebasedSeries(0.0, List.of(), List.of());
    }
    int priorIndex = firstIndex - 1;
    double priorPortfolioProfit = priorIndex >= 0 ? portfolioCurve.get(priorIndex) : 0.0;
    double priorBenchmarkProfit = priorIndex >= 0 ? benchmarkCurve.get(priorIndex) : 0.0;
    double periodCapital = investedCapital + priorPortfolioProfit;
    double benchmarkStartValue = investedCapital + priorBenchmarkProfit;

    List<Double> portfolio =
        portfolioCurve.subList(firstIndex, portfolioCurve.size()).stream()
            .map(value -> round(value - priorPortfolioProfit))
            .toList();
    List<Double> benchmark =
        benchmarkCurve.subList(firstIndex, benchmarkCurve.size()).stream()
            .map(
                value -> {
                  if (benchmarkStartValue == 0.0) {
                    return 0.0;
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
    return YearMonth.from(period.startDate(ZonedDateTime.now()));
  }

  private double last(List<Double> values) {
    return values.isEmpty() ? 0.0 : values.getLast();
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
