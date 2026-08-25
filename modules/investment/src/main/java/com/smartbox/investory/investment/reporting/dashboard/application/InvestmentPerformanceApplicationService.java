package com.smartbox.investory.investment.reporting.dashboard.application;

import com.smartbox.investory.investment.accounting.model.Benchmark;
import com.smartbox.investory.investment.api.InvestmentPerformanceApi;
import com.smartbox.investory.investment.api.InvestmentPerformanceApi.AccountValueSeries;
import com.smartbox.investory.investment.api.InvestmentPerformanceApi.AccountValueView;
import com.smartbox.investory.investment.api.InvestmentPerformanceApi.AccountValueYear;
import com.smartbox.investory.investment.api.InvestmentPerformanceApi.PerformanceAccount;
import com.smartbox.investory.investment.api.InvestmentPerformanceApi.PerformanceBoardQuery;
import com.smartbox.investory.investment.api.InvestmentPerformanceApi.PerformanceBoardView;
import com.smartbox.investory.investment.api.InvestmentPerformanceApi.PerformanceKpiView;
import com.smartbox.investory.investment.api.InvestmentPerformanceApi.PerformanceSeries;
import com.smartbox.investory.investment.reporting.BenchmarkService;
import com.smartbox.investory.investment.reporting.dashboard.service.DashboardPeriod;
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

  @Value("${app.portfolio.performance-kpi-start:2026-01}")
  private String kpiStart = "2026-01";

  @Override
  public PerformanceBoardView load(PerformanceBoardQuery query) {
    Benchmark benchmark =
        query.accountIds() == null
            ? benchmarkService.calculate()
            : benchmarkService.calculate(query.accountIds());
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

    boolean returns = "return".equalsIgnoreCase(query.metric());
    boolean allSelected = query.accountIds() == null || selected.size() == accounts.size();
    List<String> sourceLabels = benchmark.getLabels();
    List<PerformanceSeries> fullSeries =
        allSelected
            ? List.of(new PerformanceSeries("Portfolio", sourceCurve(benchmark, returns)))
            : selected.stream()
                .map(
                    series ->
                        new PerformanceSeries(
                            accountName(accounts, series.id()), accountCurve(series, returns)))
                .toList();
    int scopeStart = scopeStart(sourceLabels, query.period());
    List<String> scopedLabels = sourceLabels.subList(scopeStart, sourceLabels.size());
    List<PerformanceSeries> sourceSeries =
        fullSeries.stream()
            .map(
                row ->
                    new PerformanceSeries(
                        row.label(), scopedCurve(row.values(), scopeStart, returns)))
            .toList();
    List<Double> fullBenchmarkCurve =
        returns ? benchmark.getBenchmarkReturnCurve() : benchmark.getBenchmarkCurve();
    List<Double> benchmarkCurve = scopedCurve(fullBenchmarkCurve, scopeStart, returns);
    List<String> labels = groupedLabels(scopedLabels, query.aggregation());
    List<PerformanceSeries> series =
        sourceSeries.stream()
            .map(
                row ->
                    new PerformanceSeries(
                        row.label(),
                        transform(
                            row.values(),
                            scopedLabels,
                            query.aggregation(),
                            returns,
                            "bars".equalsIgnoreCase(query.style()))))
            .toList();
    List<Double> benchmarkValues =
        "return".equalsIgnoreCase(query.metric())
            ? transform(
                benchmarkCurve,
                scopedLabels,
                query.aggregation(),
                true,
                "bars".equalsIgnoreCase(query.style()))
            : transform(
                benchmarkCurve,
                scopedLabels,
                query.aggregation(),
                false,
                "bars".equalsIgnoreCase(query.style()));
    List<Double> excessValues =
        transform(
            differenceCurve(
                scopedCurve(benchmark.getPortfolioReturnCurve(), scopeStart, true),
                scopedCurve(benchmark.getBenchmarkReturnCurve(), scopeStart, true)),
            scopedLabels,
            query.aggregation(),
            true,
            "bars".equalsIgnoreCase(query.style()));
    List<Double> kpiSource = benchmark.getPortfolioReturnCurve();
    List<Double> kpiBenchmark = benchmark.getBenchmarkReturnCurve();
    List<Double> scopedKpiSource = scopedCurve(kpiSource, scopeStart, true);
    List<Double> scopedKpiBenchmark = scopedCurve(kpiBenchmark, scopeStart, true);
    Double portfolioReturn = last(scopedKpiSource);
    Double benchmarkReturn = last(scopedKpiBenchmark);
    List<Double> periodValues = periodValues(scopedKpiSource);
    PerformanceKpiView kpis =
        new PerformanceKpiView(
            portfolioReturn,
            benchmarkReturn,
            portfolioReturn == null || benchmarkReturn == null
                ? null
                : round(portfolioReturn - benchmarkReturn),
            profitLoss(benchmark, selectedIds, sourceLabels, scopeStart),
            periodLabel(periodValues, scopedLabels, true),
            extreme(periodValues, true),
            periodLabel(periodValues, scopedLabels, false),
            extreme(periodValues, false));
    return new PerformanceBoardView(
        true, labels, series, benchmarkValues, excessValues, kpis, accounts);
  }

  @Override
  public AccountValueView loadAccountValues(List<Long> accountIds) {
    Benchmark benchmark = benchmarkService.calculate(accountIds);
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
                                        series.profitValues(),
                                        series.profitPctValues()))
                            .toList(),
                        year.totalProfitValues(),
                        year.totalProfitPctValues()))
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

  private List<String> groupedLabels(List<String> labels, String aggregation) {
    return labels.stream().map(label -> group(label, aggregation)).distinct().toList();
  }

  private List<Double> transform(
      List<Double> values, List<String> labels, String aggregation, boolean returns, boolean bars) {
    if (!bars || "monthly".equalsIgnoreCase(aggregation)) return values;
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

  private int scopeStart(List<String> labels, String periodValue) {
    int start = labels.size();
    String configured = scopeStartLabel(periodValue);
    for (int i = 0; i < labels.size(); i++)
      if (labels.get(i).compareTo(configured) >= 0) {
        start = i;
        break;
      }
    return start;
  }

  private String scopeStartLabel(String periodValue) {
    if (periodValue == null || periodValue.isBlank()) {
      return kpiStart == null ? "" : kpiStart.substring(0, Math.min(7, kpiStart.length()));
    }
    ZonedDateTime start = DashboardPeriod.fromUrlValue(periodValue).startDate(ZonedDateTime.now());
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

  private String group(String label, String aggregation) {
    if ("annual".equalsIgnoreCase(aggregation)) return label.substring(0, 4);
    if ("quarterly".equalsIgnoreCase(aggregation))
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
