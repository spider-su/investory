package com.smartbox.investory.ui.retirement;

import com.smartbox.investory.retirement.planning.PlanningTimeline;
import com.smartbox.investory.retirement.planning.PlanningTimelineMoney;
import com.smartbox.investory.retirement.planning.PlanningTimelineYear;
import com.smartbox.investory.retirement.simulation.ForwardSimulationContextFactory;
import com.smartbox.investory.retirement.simulation.SimulationAssumptions;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Chart-ready presentation data sourced from the same timeline as the simulation tables. */
public record RetirementSimulationChartView(
    List<Point> points, Integer retirementYear, Integer pensionStartYear) {
  public RetirementSimulationChartView {
    points = points == null ? List.of() : List.copyOf(points);
  }

  public RetirementSimulationChartView(List<Point> points) {
    this(points, null, null);
  }

  public static RetirementSimulationChartView from(
      PlanningTimeline timeline, Map<Integer, PlanningTimelineMoney> moneyByYear) {
    return from(timeline, moneyByYear, null);
  }

  public static RetirementSimulationChartView from(
      PlanningTimeline timeline,
      Map<Integer, PlanningTimelineMoney> moneyByYear,
      SimulationAssumptions assumptions) {
    return new RetirementSimulationChartView(
        timeline.years().stream()
            .map(row -> point(row, moneyByYear.get(row.year())))
            .toList(),
        assumptions == null ? null : ForwardSimulationContextFactory.retirementYear(assumptions),
        pensionStartYear(assumptions));
  }

  private static Integer pensionStartYear(SimulationAssumptions assumptions) {
    if (assumptions == null || assumptions.pensionStartAge() == Integer.MAX_VALUE) return null;
    return assumptions.planStartYear()
        + assumptions.pensionStartAge()
        - assumptions.ageAtPlanStart();
  }

  private static Point point(PlanningTimelineYear row, PlanningTimelineMoney money) {
    BigDecimal spending = money == null ? null : money.annualCosts();
    BigDecimal income = money == null ? null : money.totalIncome();
    BigDecimal gapOrSurplus =
        spending == null || income == null ? null : income.subtract(spending);
    return new Point(
        row.year(),
        row.state().name(),
        spending,
        income,
        gapOrSurplus,
        money == null ? null : money.bondsEnd(),
        money == null ? null : money.equitiesEnd());
  }

  public record Point(
      int year,
      String state,
      BigDecimal spending,
      BigDecimal income,
      BigDecimal gapOrSurplus,
      BigDecimal bondsEnd,
      BigDecimal equitiesEnd) {}
}
