package com.smartbox.investory.ui.retirement;

import com.smartbox.investory.retirement.planning.PlanningTimeline;
import com.smartbox.investory.retirement.planning.PlanningTimelineMoney;
import com.smartbox.investory.retirement.planning.PlanningTimelineState;
import com.smartbox.investory.retirement.simulation.SimulationAssumptions;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Server-prepared selectable snapshots for the simulation plan timeline. */
public record PlanTimelineView(
    int startYear, int endYear, int selectedYear, Integer retirementYear,
    Integer pensionStartYear, List<YearSnapshotView> years) {

  public PlanTimelineView { years = List.copyOf(years); }

  public static PlanTimelineView from(
      PlanningTimeline timeline, java.util.Map<Integer, RetirementYearSummaryView> summaries,
      Integer retirementYear, Integer pensionStartYear) {
    return from(timeline, summaries, retirementYear, pensionStartYear, Integer.MIN_VALUE);
  }

  public static PlanTimelineView from(
      PlanningTimeline timeline, java.util.Map<Integer, RetirementYearSummaryView> summaries,
      Integer retirementYear, Integer pensionStartYear, int currentYear) {
    return from(timeline, summaries, Map.of(), null, retirementYear, pensionStartYear, currentYear);
  }

  public static PlanTimelineView from(
      PlanningTimeline timeline, Map<Integer, RetirementYearSummaryView> summaries,
      Map<Integer, PlanningTimelineMoney> moneyByYear, SimulationAssumptions assumptions,
      Integer retirementYear, Integer pensionStartYear, int currentYear) {
    return from(timeline, summaries, moneyByYear, assumptions, retirementYear, pensionStartYear,
        currentYear, Function.identity());
  }

  public static PlanTimelineView from(
      PlanningTimeline timeline, Map<Integer, RetirementYearSummaryView> summaries,
      Map<Integer, PlanningTimelineMoney> moneyByYear, SimulationAssumptions assumptions,
      Integer retirementYear, Integer pensionStartYear, int currentYear,
      Function<BigDecimal, BigDecimal> displayMoney) {
    List<YearSnapshotView> snapshots = timeline.years().stream()
        .map(row -> new YearSnapshotView(
            row.year(), row.age(), summaries.get(row.year()),
            lifecycle(row.year(), timeline, retirementYear, pensionStartYear),
            CashFlowSectionView.forYear(row, moneyByYear.get(row.year()), assumptions, displayMoney)))
        .toList();
    int selected = timeline.years().stream().filter(row -> row.state() == PlanningTimelineState.LIVE)
        .mapToInt(row -> row.year()).findFirst()
        .orElseGet(() -> snapshots.isEmpty() ? 0 : snapshots.stream()
            .min(java.util.Comparator.comparingInt(row -> currentYear == Integer.MIN_VALUE
                ? row.year() : Math.abs(row.year() - currentYear)))
            .orElse(snapshots.get(0)).year());
    return new PlanTimelineView(
        snapshots.isEmpty() ? 0 : snapshots.get(0).year(),
        snapshots.isEmpty() ? 0 : snapshots.get(snapshots.size() - 1).year(),
        selected, retirementYear, pensionStartYear, snapshots);
  }

  private static String lifecycle(int year, PlanningTimeline timeline, Integer retirementYear, Integer pensionStartYear) {
    if (timeline.years().isEmpty()) return null;
    if (year == timeline.years().get(0).year()) return "Plan start";
    if (retirementYear != null && year == retirementYear) return "Retirement";
    if (pensionStartYear != null && year == pensionStartYear) return "Pension start";
    if (year == timeline.years().get(timeline.years().size() - 1).year()) return "Plan end";
    return null;
  }

  public record YearSnapshotView(
      int year, int age, RetirementYearSummaryView summary, String lifecycleLabel,
      CashFlowSectionView cashFlow) {
    public String heading() { return year + " · AGE " + age + (lifecycleLabel == null ? "" : " · " + lifecycleLabel.toUpperCase()); }

    public List<CashFlowFlowView> incomeSources() { return cashFlow == null ? List.of() : cashFlow.income(); }
    public List<CashFlowFlowView> fundingSources() { return cashFlow == null ? List.of() : cashFlow.funding(); }
    public BigDecimal capitalFunding() { return cashFlow == null ? BigDecimal.ZERO : cashFlow.capitalFunding(); }
    public BigDecimal capitalFundingShown() { return cashFlow == null ? null : cashFlow.capitalFundingShown(); }
    public BigDecimal incomeUsed() { return cashFlow == null ? BigDecimal.ZERO : cashFlow.incomeUsed(); }
    public BigDecimal totalFunded() { return cashFlow == null ? null : cashFlow.totalFunded(); }
    public BigDecimal fundedAmount() { return cashFlow == null ? null : cashFlow.fundedAmount(); }
    public BigDecimal unfunded() { return cashFlow == null ? null : cashFlow.remainingUnfunded(); }
    public BigDecimal fundingCoveragePercent() { return cashFlow == null ? BigDecimal.ZERO : cashFlow.fundingCoveragePercent(); }
    public BigDecimal incomeFundingPercent() { return cashFlow == null ? BigDecimal.ZERO : cashFlow.incomeFundingPercent(); }
    public BigDecimal capitalFundingPercent() { return cashFlow == null ? BigDecimal.ZERO : cashFlow.capitalFundingPercent(); }
    public BigDecimal unfundedPercent() { return cashFlow == null ? BigDecimal.ZERO : cashFlow.unfundedPercent(); }
    public String periodNote() { return cashFlow == null ? null : cashFlow.periodNote(); }

    public BigDecimal liquidCapitalTotal() {
      return liquidValue(summary.cash()) .add(liquidValue(summary.bonds())).add(liquidValue(summary.equities()));
    }

    public BigDecimal cashExpected() { return expectedValue(summary.cash()); }
    public BigDecimal bondsExpected() { return expectedValue(summary.bonds()); }
    public BigDecimal equitiesExpected() { return expectedValue(summary.equities()); }
    public BigDecimal cashCurrent() { return currentValue(summary.cash()); }
    public BigDecimal bondsCurrent() { return currentValue(summary.bonds()); }
    public BigDecimal equitiesCurrent() { return currentValue(summary.equities()); }
    public BigDecimal cashChange() { return change(cashExpected(), cashCurrent()); }
    public BigDecimal bondsChange() { return change(bondsExpected(), bondsCurrent()); }
    public BigDecimal equitiesChange() { return change(equitiesExpected(), equitiesCurrent()); }
    public String capitalComparisonLabel() { return "Live".equals(summary.state()) ? "Now" : "Start"; }
    public BigDecimal realEstateCapital() { return summary.realEstate() == null ? null : summary.realEstate().endValue(); }
    public BigDecimal realEstateMonthlyIncome() {
      if (summary.realEstate() == null || summary.realEstate().annualValue() == null) return null;
      return summary.realEstate().annualValue().divide(BigDecimal.valueOf(12), 2, java.math.RoundingMode.HALF_UP);
    }

    public BigDecimal cashPercent() { return liquidCapitalPercent(summary.cash()); }
    public BigDecimal bondsPercent() { return liquidCapitalPercent(summary.bonds()); }
    public BigDecimal equitiesPercent() { return liquidCapitalPercent(summary.equities()); }

    private BigDecimal liquidCapitalPercent(RetirementYearSummaryView.BucketSummary bucket) {
      BigDecimal total = liquidCapitalTotal();
      if (total.signum() == 0) return BigDecimal.ZERO;
      return liquidValue(bucket).multiply(BigDecimal.valueOf(100)).divide(total, 1, java.math.RoundingMode.HALF_UP);
    }

    private static BigDecimal expectedValue(RetirementYearSummaryView.BucketSummary bucket) {
      return bucket == null ? null : bucket.endValue();
    }

    private static BigDecimal currentValue(RetirementYearSummaryView.BucketSummary bucket) {
      return bucket == null ? null : bucket.startValue();
    }

    private static BigDecimal change(BigDecimal expected, BigDecimal current) {
      return expected == null || current == null ? null : expected.subtract(current);
    }

    private static BigDecimal liquidValue(RetirementYearSummaryView.BucketSummary bucket) {
      return bucket == null || bucket.endValue() == null ? BigDecimal.ZERO : bucket.endValue().max(BigDecimal.ZERO);
    }
  }
}
