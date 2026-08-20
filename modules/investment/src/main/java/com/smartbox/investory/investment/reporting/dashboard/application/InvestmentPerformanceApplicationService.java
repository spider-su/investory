package com.smartbox.investory.investment.reporting.dashboard.application;

import com.smartbox.investory.investment.accounting.model.Benchmark;
import com.smartbox.investory.investment.api.InvestmentPerformanceApi;
import com.smartbox.investory.investment.api.InvestmentPerformanceApi.PerformanceAccount;
import com.smartbox.investory.investment.api.InvestmentPerformanceApi.PerformanceBoardQuery;
import com.smartbox.investory.investment.api.InvestmentPerformanceApi.PerformanceBoardView;
import com.smartbox.investory.investment.api.InvestmentPerformanceApi.PerformanceKpiView;
import com.smartbox.investory.investment.api.InvestmentPerformanceApi.PerformanceSeries;
import com.smartbox.investory.investment.reporting.BenchmarkService;
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
          false, List.of(), List.of(), List.of(), emptyKpis(), accounts);
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
          false, List.of(), List.of(), List.of(), emptyKpis(), accounts);
    }

    boolean returns = "return".equalsIgnoreCase(query.metric());
    boolean allSelected = query.accountIds() == null || selected.size() == accounts.size();
    List<String> sourceLabels = benchmark.getLabels();
    List<PerformanceSeries> sourceSeries =
        allSelected
            ? List.of(new PerformanceSeries("Portfolio", sourceCurve(benchmark, returns)))
            : selected.stream()
                .map(
                    series ->
                        new PerformanceSeries(
                            accountName(accounts, series.id()), accountCurve(series, returns)))
                .toList();
    List<Double> benchmarkCurve =
        returns ? benchmark.getBenchmarkReturnCurve() : benchmark.getBenchmarkCurve();
    List<String> labels = groupedLabels(sourceLabels, query.aggregation());
    List<PerformanceSeries> series =
        sourceSeries.stream()
            .map(
                row ->
                    new PerformanceSeries(
                        row.label(),
                        transform(
                            row.values(),
                            sourceLabels,
                            query.aggregation(),
                            returns,
                            "bars".equalsIgnoreCase(query.style()))))
            .toList();
    List<Double> benchmarkValues =
        "return".equalsIgnoreCase(query.metric())
            ? transform(
                benchmarkCurve,
                sourceLabels,
                query.aggregation(),
                true,
                "bars".equalsIgnoreCase(query.style()))
            : transform(
                benchmarkCurve,
                sourceLabels,
                query.aggregation(),
                false,
                "bars".equalsIgnoreCase(query.style()));
    List<Double> kpiSource = sourceSeries.getFirst().values();
    List<Double> kpiBenchmark = benchmark.getBenchmarkReturnCurve();
    Double portfolioReturn = last(rebase(kpiSource, sourceLabels));
    Double benchmarkReturn = last(rebase(kpiBenchmark, sourceLabels));
    List<Double> periodValues = periodValues(kpiSource);
    PerformanceKpiView kpis =
        new PerformanceKpiView(
            portfolioReturn,
            benchmarkReturn,
            portfolioReturn == null || benchmarkReturn == null
                ? null
                : round(portfolioReturn - benchmarkReturn),
            periodLabel(periodValues, sourceLabels, true),
            extreme(periodValues, true),
            periodLabel(periodValues, sourceLabels, false),
            extreme(periodValues, false));
    return new PerformanceBoardView(true, labels, series, benchmarkValues, kpis, accounts);
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
    List<Double> result = new ArrayList<>();
    for (int i = 0; i < monthly.size(); i++) {
      Double opening = i < capital.size() ? capital.get(i) : null;
      Double rate = monthly.get(i);
      if (opening == null
          || rate == null
          || !Double.isFinite(opening)
          || !Double.isFinite(rate)
          || opening == 0.0) result.add(null);
      else {
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
        if (group.equals(group(labels.get(i), aggregation))) bucket.add(period.get(i));
      result.add(
          returns
              ? compound(bucket)
              : bucket.stream().filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum());
    }
    return result;
  }

  private List<Double> periodValues(List<Double> cumulative) {
    List<Double> result = new ArrayList<>();
    for (int i = 0; i < cumulative.size(); i++) {
      Double value = cumulative.get(i);
      Double prior = i == 0 ? 0.0 : cumulative.get(i - 1);
      result.add(
          value == null || prior == null
              ? null
              : round(((1 + value / 100.0) / (1 + prior / 100.0) - 1) * 100.0));
    }
    return result;
  }

  private List<Double> differenceValues(List<Double> cumulative) {
    List<Double> result = new ArrayList<>();
    for (int i = 0; i < cumulative.size(); i++)
      result.add(
          cumulative.get(i) == null
              ? null
              : round(
                  cumulative.get(i)
                      - (i == 0 || cumulative.get(i - 1) == null ? 0 : cumulative.get(i - 1))));
    return result;
  }

  private Double compound(List<Double> values) {
    double factor = 1.0;
    boolean available = false;
    for (Double value : values)
      if (value != null) {
        factor *= 1 + value / 100.0;
        available = true;
      }
    return available ? round((factor - 1) * 100.0) : null;
  }

  private List<Double> rebase(List<Double> values, List<String> labels) {
    int start = 0;
    String configured =
        kpiStart == null ? "" : kpiStart.substring(0, Math.min(7, kpiStart.length()));
    for (int i = 0; i < labels.size(); i++)
      if (labels.get(i).compareTo(configured) >= 0) {
        start = i;
        break;
      }
    Double prior = start == 0 ? 0.0 : values.get(start - 1);
    if (prior == null || start >= values.size()) return List.of();
    return values.subList(start, values.size()).stream()
        .map(
            value ->
                value == null
                    ? null
                    : round(((1 + value / 100.0) / (1 + prior / 100.0) - 1) * 100.0))
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
    return new PerformanceKpiView(null, null, null, "—", null, "—", null);
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
