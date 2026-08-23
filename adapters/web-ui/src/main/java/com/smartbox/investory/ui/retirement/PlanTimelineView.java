package com.smartbox.investory.ui.retirement;

import com.smartbox.investory.retirement.planning.PlanningTimeline;
import com.smartbox.investory.retirement.planning.PlanningTimelineState;
import java.util.List;

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
    List<YearSnapshotView> snapshots = timeline.years().stream()
        .map(row -> new YearSnapshotView(
            row.year(), row.age(), summaries.get(row.year()), lifecycle(row.year(), timeline, retirementYear, pensionStartYear)))
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

  public record YearSnapshotView(int year, int age, RetirementYearSummaryView summary, String lifecycleLabel) {
    public String heading() { return year + " · AGE " + age + (lifecycleLabel == null ? "" : " · " + lifecycleLabel.toUpperCase()); }
  }
}
