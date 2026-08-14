package com.smartbox.investory.application.dashboard;

import com.smartbox.investory.services.models.Benchmark;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record BenchmarkView(
    boolean available,
    boolean portfolioPerformanceAvailable,
    boolean benchmarkAvailable,
    String symbol,
    List<String> labels,
    List<Double> portfolioCurve,
    List<Double> benchmarkCurve,
    List<Double> portfolioReturnCurve,
    List<Double> benchmarkReturnCurve,
    double investedCapital,
    double portfolioPl,
    Double benchmarkPl,
    double portfolioReturnPct,
    Double benchmarkReturnPct,
    double alpha,
    List<Benchmark.AccountOption> accountOptions,
    List<Benchmark.AccountSeries> accountSeries,
    boolean accountValuesAvailable,
    Integer selectedAccountValueYear,
    List<Benchmark.AccountValueYear> accountValueYears) {

  public BenchmarkView {
    labels = copy(labels);
    portfolioCurve = copy(portfolioCurve);
    benchmarkCurve = copy(benchmarkCurve);
    portfolioReturnCurve = copy(portfolioReturnCurve);
    benchmarkReturnCurve = copy(benchmarkReturnCurve);
    accountOptions = copy(accountOptions);
    accountSeries = copy(accountSeries);
    accountValueYears = copy(accountValueYears);
  }

  private static <T> List<T> copy(List<T> source) {
    return source == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(source));
  }
}
