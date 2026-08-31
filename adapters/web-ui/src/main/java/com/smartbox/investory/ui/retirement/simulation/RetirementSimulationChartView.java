package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.RetirementFinancialCalculations;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.PlanningTimeline;
import com.smartbox.investory.retirement.api.model.PlanningTimelineMoney;
import com.smartbox.investory.retirement.api.model.PlanningTimelineYear;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Chart-ready presentation data sourced from the same timeline as the simulation tables. */
public record RetirementSimulationChartView(
    List<Point> points, Integer retirementYear, Integer pensionStartYear) {
  public RetirementSimulationChartView {
    points = com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(points);
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
        timeline.years().stream().map(row -> point(row, moneyByYear.get(row.year()))).toList(),
        assumptions == null ? null : assumptions.retirementYear(),
        pensionStartYear(assumptions));
  }

  private static Integer pensionStartYear(SimulationAssumptions assumptions) {
    if (assumptions == null || assumptions.pensionStartAge() == null) return null;
    return assumptions.planStartYear()
        + assumptions.pensionStartAge()
        - assumptions.ageAtPlanStart();
  }

  private static Point point(PlanningTimelineYear row, PlanningTimelineMoney money) {
    BigDecimal spending = money == null ? null : money.annualCosts();
    BigDecimal income = money == null ? null : money.totalIncome();
    BigDecimal gapOrSurplus = RetirementFinancialCalculations.difference(income, spending);
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
