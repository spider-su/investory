package com.smartbox.investory.investment.accounting.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * Comparison of account monthly P/L against SPY performance. Amount curves are cumulative base-
 * currency P/L; return curves are geometrically linked, flow-adjusted returns.
 */
@Data
public class Benchmark {

  private boolean available;
  private boolean portfolioPerformanceAvailable;
  private boolean benchmarkAvailable;
  private String symbol = "SPY";

  /** Month labels ("yyyy-MM") shared by both curves. */
  private List<String> labels = new ArrayList<>();

  /** Cumulative account monthly P/L excluding external funding flows, in base USD. */
  private List<Double> portfolioCurve = new ArrayList<>();

  /** Cumulative P/L if each account's starting value had tracked SPY, in base USD. */
  private List<Double> benchmarkCurve = new ArrayList<>();

  /** Geometrically linked canonical portfolio and benchmark returns for each label. */
  private List<Double> portfolioReturnCurve = new ArrayList<>();

  private List<Double> benchmarkReturnCurve = new ArrayList<>();

  /** Starting valuation used only to express the absolute benchmark P/L curves. */
  private double investedCapital;

  private double portfolioPl;
  private Double benchmarkPl;
  private double portfolioReturnPct;
  private Double benchmarkReturnPct;

  /**
   * Portfolio return minus benchmark return, in percentage points. This is not risk-adjusted alpha.
   */
  private double alpha;

  private List<AccountOption> accountOptions = new ArrayList<>();
  private List<AccountSeries> accountSeries = new ArrayList<>();
  private boolean accountValuesAvailable;
  private Integer selectedAccountValueYear;
  private List<AccountValueYear> accountValueYears = new ArrayList<>();

  public record AccountOption(Long id, String name, boolean selected) {}

  public record AccountSeries(
      Long id,
      /** First non-zero opening valuation; not net deposits and not the return denominator. */
      double investedCapital,
      double portfolioPl,
      double benchmarkPl,
      List<Double> portfolioCurve,
      List<Double> benchmarkCurve,
      /** Opening capital and return contribution for each monthly label. */
      List<Double> returnCapitalCurve,
      List<Double> returnContributionCurve,
      /** Canonical flow-adjusted account return for each monthly label, in percent. */
      List<Double> returnPctCurve) {

    public AccountSeries(
        Long id,
        double investedCapital,
        double portfolioPl,
        double benchmarkPl,
        List<Double> portfolioCurve,
        List<Double> benchmarkCurve,
        List<Double> returnCapitalCurve,
        List<Double> returnContributionCurve) {
      this(
          id,
          investedCapital,
          portfolioPl,
          benchmarkPl,
          portfolioCurve,
          benchmarkCurve,
          returnCapitalCurve,
          returnContributionCurve,
          List.of());
    }
  }

  public record AccountValueYear(
      int year,
      List<String> labels,
      List<AccountValueSeries> accountSeries,
      List<Double> totalProfitValues,
      List<Double> totalProfitPctValues) {}

  public record AccountValueSeries(
      Long id, String name, List<Double> profitValues, List<Double> profitPctValues) {}
}
