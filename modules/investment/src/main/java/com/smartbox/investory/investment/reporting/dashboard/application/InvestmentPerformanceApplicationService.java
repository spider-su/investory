package com.smartbox.investory.investment.reporting.dashboard.application;

import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.investment.api.reporting.InvestmentPerformanceApi;
import com.smartbox.investory.investment.api.reporting.InvestmentPerformanceApi.AccountValueSeries;
import com.smartbox.investory.investment.api.reporting.InvestmentPerformanceApi.AccountValueView;
import com.smartbox.investory.investment.api.reporting.InvestmentPerformanceApi.AccountValueYear;
import com.smartbox.investory.investment.api.reporting.InvestmentPerformanceApi.PerformanceAccount;
import com.smartbox.investory.investment.api.reporting.InvestmentPerformanceApi.PerformanceBoardQuery;
import com.smartbox.investory.investment.api.reporting.InvestmentPerformanceApi.PerformanceBoardView;
import com.smartbox.investory.investment.api.reporting.InvestmentPerformanceApi.PerformanceKpiView;
import com.smartbox.investory.investment.api.reporting.InvestmentPerformanceApi.PerformanceSeries;
import com.smartbox.investory.investment.api.reporting.PerformanceAggregation;
import com.smartbox.investory.investment.api.reporting.PerformanceMetric;
import com.smartbox.investory.investment.api.reporting.PerformanceStyle;
import com.smartbox.investory.investment.api.reporting.model.Benchmark;
import com.smartbox.investory.investment.reporting.BenchmarkService;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Owns the financial transformations used by the performance board. */
@Service
@RequiredArgsConstructor
public class InvestmentPerformanceApplicationService implements InvestmentPerformanceApi {
  private final BenchmarkService benchmarkService;

  @Value("${app.portfolio.performance-kpi-start}")
  private String kpiStart = "2026-01";

  @Override
  public PerformanceBoardView load(PerformanceBoardQuery query) {
    Benchmark benchmark =
        query.accountIds() == null
            ? benchmarkService.calculate(query.portfolioId(), null)
            : benchmarkService.calculate(query.portfolioId(), query.accountIds());
    List<PerformanceAccount> accounts =
        benchmark.getAccountOptions().stream()
            .map(option -> new PerformanceAccount(option.id(), option.name(), option.selected()))
            .toList();
    if (!benchmark.isAvailable() || benchmark.getLabels().isEmpty()) {
      return new PerformanceBoardView(
          false, List.of(), List.of(), List.of(), List.of(), emptyKpis(), accounts);
    }

    Set<Long> selectedIds =
        accounts.stream()
            .filter(PerformanceAccount::selected)
            .map(PerformanceAccount::id)
            .collect(Collectors.toSet());
    List<Benchmark.AccountSeries> selected =
        benchmark.getAccountSeries().stream()
            .filter(series -> selectedIds.contains(series.id()))
            .toList();
    if (selected.isEmpty()) {
      return new PerformanceBoardView(
          false, List.of(), List.of(), List.of(), List.of(), emptyKpis(), accounts);
    }

    boolean returns = query.metric() == PerformanceMetric.RETURN;
    boolean allSelected = query.accountIds() == null || selected.size() == accounts.size();
    List<String> sourceLabels = benchmark.getLabels();
    List<PerformanceSeries> fullSeries =
        allSelected
            ? List.of(
                new PerformanceSeries(
                    null, "Portfolio", decimalValues(sourceCurve(benchmark, returns))))
            : selected.stream()
                .map(
                    series ->
                        new PerformanceSeries(
                            series.id(),
                            accountName(accounts, series.id()),
                            decimalValues(accountCurve(series, returns))))
                .toList();
    int scopeStart = scopeStart(sourceLabels, query.period());
    List<String> scopedLabels = sourceLabels.subList(scopeStart, sourceLabels.size());
    List<PerformanceSeries> sourceSeries =
        fullSeries.stream().map(row -> scopedSeries(row, scopeStart, returns)).toList();
    List<Double> fullBenchmarkCurve =
        returns ? benchmark.getBenchmarkReturnCurve() : benchmark.getBenchmarkCurve();
    List<Double> benchmarkCurve = scopedCurve(fullBenchmarkCurve, scopeStart, returns);
    List<String> labels = groupedLabels(scopedLabels, query.aggregation());
    List<PerformanceSeries> series =
        sourceSeries.stream()
            .map(
                row ->
                    new PerformanceSeries(
                        row.accountId(),
                        row.label(),
                        decimalValues(
                            transform(
                                doubleValues(row.values()),
                                scopedLabels,
                                query.aggregation(),
                                returns,
                                query.style() == PerformanceStyle.BARS))))
            .toList();
    List<Double> benchmarkValues =
        query.metric() == PerformanceMetric.RETURN
            ? transform(
                benchmarkCurve,
                scopedLabels,
                query.aggregation(),
                true,
                query.style() == PerformanceStyle.BARS)
            : transform(
                benchmarkCurve,
                scopedLabels,
                query.aggregation(),
                false,
                query.style() == PerformanceStyle.BARS);
    List<Double> excessValues =
        transform(
            differenceCurve(
                scopedCurve(benchmark.getPortfolioReturnCurve(), scopeStart, true),
                scopedCurve(benchmark.getBenchmarkReturnCurve(), scopeStart, true)),
            scopedLabels,
            query.aggregation(),
            true,
            query.style() == PerformanceStyle.BARS);
    List<Double> kpiSource = benchmark.getPortfolioReturnCurve();
    List<Double> kpiBenchmark = benchmark.getBenchmarkReturnCurve();
    List<Double> scopedKpiSource = scopedCurve(kpiSource, scopeStart, true);
    List<Double> scopedKpiBenchmark = scopedCurve(kpiBenchmark, scopeStart, true);
    Double portfolioReturn = last(scopedKpiSource);
    Double benchmarkReturn = last(scopedKpiBenchmark);
    List<Double> periodValues = periodValues(scopedKpiSource);
    PerformanceKpiView kpis =
        new PerformanceKpiView(
            decimal(portfolioReturn),
            decimal(benchmarkReturn),
            portfolioReturn == null || benchmarkReturn == null
                ? null
                : decimal(round(portfolioReturn - benchmarkReturn)),
            decimal(profitLoss(benchmark, selectedIds, sourceLabels, scopeStart)),
            periodLabel(periodValues, scopedLabels, true),
            decimal(extreme(periodValues, true)),
            periodLabel(periodValues, scopedLabels, false),
            decimal(extreme(periodValues, false)));
    return new PerformanceBoardView(
        true,
        labels,
        series,
        decimalValues(benchmarkValues),
        decimalValues(excessValues),
        kpis,
        accounts);
  }

  @Override
  public AccountValueView loadAccountValues(Long portfolioId, List<Long> accountIds) {
    Benchmark benchmark = benchmarkService.calculate(portfolioId, accountIds);
    return new AccountValueView(
        benchmark.getAccountValueYears().stream()
            .map(
                year ->
                    new AccountValueYear(
                        year.year(),
                        year.labels(),
                        year.accountSeries().stream()
                            .map(
                                series ->
                                    new AccountValueSeries(
                                        series.id(),
                                        series.name(),
                                        decimalValues(series.profitValues()),
                                        decimalValues(series.profitPctValues())))
                            .toList(),
                        decimalValues(year.totalProfitValues()),
                        decimalValues(year.totalProfitPctValues())))
            .toList());
  }

  private String accountName(List<PerformanceAccount> accounts, Long id) {
    return accounts.stream()
        .filter(account -> Objects.equals(account.id(), id))
        .map(PerformanceAccount::name)
        .findFirst()
        .orElse(String.valueOf(id));
  }

  private List<Double> sourceCurve(Benchmark benchmark, boolean returns) {
    return returns ? benchmark.getPortfolioReturnCurve() : benchmark.getPortfolioCurve();
  }

  private List<Double> accountCurve(Benchmark.AccountSeries series, boolean returns) {
    return returns
        ? compoundCurve(series.returnCapitalCurve(), series.returnPctCurve())
        : series.portfolioCurve();
  }

  private List<Double> compoundCurve(List<Double> capital, List<Double> monthly) {
    double factor = 1.0;
    boolean returnStarted = false;
    boolean returnComplete = true;
    List<Double> result = new ArrayList<>();
    for (int i = 0; i < monthly.size(); i++) {
      Double opening = i < capital.size() ? capital.get(i) : null;
      Double rate = monthly.get(i);
      if (opening == null
          || rate == null
          || !Double.isFinite(opening)
          || !Double.isFinite(rate)
          || opening == 0.0) {
        if (returnStarted) returnComplete = false;
        result.add(null);
      } else if (!returnComplete) {
        result.add(null);
      } else {
        returnStarted = true;
        factor = rate <= -100.0 ? 0.0 : factor * (1.0 + rate / 100.0);
        result.add(round((factor - 1.0) * 100.0));
      }
    }
    return result;
  }

  private List<String> groupedLabels(List<String> labels, PerformanceAggregation aggregation) {
    return labels.stream().map(label -> group(label, aggregation)).distinct().toList();
  }

  private List<Double> transform(
      List<Double> values,
      List<String> labels,
      PerformanceAggregation aggregation,
      boolean returns,
      boolean bars) {
    if (!bars || aggregation == PerformanceAggregation.MONTHLY) return values;
    List<Double> period = returns ? periodValues(values) : differenceValues(values);
    List<Double> result = new ArrayList<>();
    for (String group : groupedLabels(labels, aggregation)) {
      List<Double> bucket = new ArrayList<>();
      for (int i = 0; i < labels.size(); i++)
        if (group.equals(group(labels.get(i), aggregation)))
          bucket.add(i < period.size() ? period.get(i) : null);
      if (returns) {
        result.add(compound(bucket));
      } else {
        result.add(
            bucket.isEmpty() || bucket.stream().anyMatch(Objects::isNull)
                ? null
                : bucket.stream().mapToDouble(Double::doubleValue).sum());
      }
    }
    return result;
  }

  private List<Double> periodValues(List<Double> cumulative) {
    List<Double> result = new ArrayList<>();
    for (int i = 0; i < cumulative.size(); i++) {
      Double value = cumulative.get(i);
      Double prior = i == 0 ? Double.valueOf(0.0) : cumulative.get(i - 1);
      result.add(
          value == null || prior == null
              ? null
              : round(((1 + value / 100.0) / (1 + prior / 100.0) - 1) * 100.0));
    }
    return result;
  }

  private List<Double> differenceValues(List<Double> cumulative) {
    List<Double> result = new ArrayList<>();
    for (int i = 0; i < cumulative.size(); i++) {
      Double value = cumulative.get(i);
      Double prior = i == 0 ? Double.valueOf(0.0) : cumulative.get(i - 1);
      result.add(value == null || prior == null ? null : round(value - prior));
    }
    return result;
  }

  private Double compound(List<Double> values) {
    if (values.isEmpty() || values.stream().anyMatch(Objects::isNull)) return null;
    double factor = 1.0;
    for (Double value : values) factor *= 1 + value / 100.0;
    return round((factor - 1) * 100.0);
  }

  private List<Double> differenceCurve(List<Double> left, List<Double> right) {
    List<Double> result = new ArrayList<>();
    for (int i = 0; i < left.size(); i++) {
      Double a = left.get(i), b = i < right.size() ? right.get(i) : null;
      result.add(a == null || b == null ? null : round(a - b));
    }
    return result;
  }

  private int scopeStart(List<String> labels, DashboardPeriod period) {
    int start = labels.size();
    String configured = scopeStartLabel(period);
    for (int i = 0; i < labels.size(); i++)
      if (labels.get(i).compareTo(configured) >= 0) {
        start = i;
        break;
      }
    return start;
  }

  private String scopeStartLabel(DashboardPeriod period) {
    if (period == null) {
      return kpiStart == null ? "" : kpiStart.substring(0, Math.min(7, kpiStart.length()));
    }
    ZonedDateTime start = period.startDate(ZonedDateTime.now());
    return start == null ? "" : YearMonth.from(start).toString();
  }

  private List<Double> scopedCurve(List<Double> values, int start, boolean returns) {
    if (start >= values.size()) return List.of();
    Double prior = start == 0 ? Double.valueOf(0.0) : values.get(start - 1);
    if (prior == null)
      return values.subList(start, values.size()).stream().map(ignored -> (Double) null).toList();
    return values.subList(start, values.size()).stream()
        .map(
            value ->
                value == null
                    ? null
                    : returns
                        ? round(((1 + value / 100.0) / (1 + prior / 100.0) - 1) * 100.0)
                        : round(value - prior))
        .toList();
  }

  private String periodLabel(List<Double> values, List<String> labels, boolean max) {
    if (values.isEmpty()) return "—";
    int index = -1;
    for (int i = 0; i < values.size(); i++)
      if (values.get(i) != null
          && (index < 0
              || (max ? values.get(i) > values.get(index) : values.get(i) < values.get(index))))
        index = i;
    return index < 0 || index >= labels.size() ? "—" : labels.get(index);
  }

  private Double extreme(List<Double> values, boolean max) {
    return values.stream()
        .filter(Objects::nonNull)
        .max(max ? Comparator.naturalOrder() : Comparator.reverseOrder())
        .orElse(null);
  }

  private PerformanceKpiView emptyKpis() {
    return new PerformanceKpiView(null, null, null, null, "—", null, "—", null);
  }

  private List<BigDecimal> decimalValues(List<Double> values) {
    return values.stream().map(value -> value == null ? null : BigDecimal.valueOf(value)).toList();
  }

  private PerformanceSeries scopedSeries(PerformanceSeries row, int scopeStart, boolean returns) {
    return new PerformanceSeries(
        row.accountId(),
        row.label(),
        decimalValues(scopedCurve(doubleValues(row.values()), scopeStart, returns)));
  }

  private List<Double> doubleValues(List<BigDecimal> values) {
    return values.stream().map(value -> value == null ? null : value.doubleValue()).toList();
  }

  private BigDecimal decimal(Double value) {
    return value == null ? null : BigDecimal.valueOf(value);
  }

  private Double profitLoss(
      Benchmark benchmark, Set<Long> selectedIds, List<String> labels, int start) {
    if (labels.isEmpty() || start >= labels.size()) return null;
    List<Benchmark.AccountSeries> selected =
        benchmark.getAccountSeries().stream()
            .filter(row -> selectedIds.contains(row.id()))
            .toList();
    if (selected.isEmpty()) return null;
    int end = labels.size() - 1;
    double result = 0;
    for (Benchmark.AccountSeries row : selected) {
      List<Double> curve = row.portfolioCurve();
      if (end >= curve.size()) return null;
      Double endingValue = curve.get(end);
      Double openingValue = start == 0 ? Double.valueOf(0.0) : curve.get(start - 1);
      if (endingValue == null || openingValue == null) return null;
      result += endingValue - openingValue;
    }
    return round(result);
  }

  private String group(String label, PerformanceAggregation aggregation) {
    if (aggregation == PerformanceAggregation.ANNUAL) return label.substring(0, 4);
    if (aggregation == PerformanceAggregation.QUARTERLY)
      return label.substring(0, 4) + "-Q" + ((Integer.parseInt(label.substring(5, 7)) - 1) / 3 + 1);
    return label;
  }

  private Double last(List<Double> values) {
    return values.isEmpty() ? null : values.getLast();
  }

  private double round(double value) {
    return Math.round(value * 100.0) / 100.0;
  }
}
