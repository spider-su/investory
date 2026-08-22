package com.smartbox.investory.ui.retirement;

import com.smartbox.investory.retirement.planning.PlanningTimeline;
import com.smartbox.investory.retirement.planning.PlanningTimelineMoney;
import com.smartbox.investory.retirement.planning.PlanningTimelineState;
import com.smartbox.investory.retirement.simulation.SimulationAssumptions;
import com.smartbox.investory.retirement.simulation.SimulationEvent;
import com.smartbox.investory.retirement.simulation.SimulationEventType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Current-year cash-flow summary. Capital returns are intentionally not included. */
public record CashFlowSectionView(
    int year,
    List<CashFlowFlowView> income,
    List<CashFlowFlowView> funding,
    List<CashFlowFlowView> destinations) {

  public CashFlowSectionView {
    income = List.copyOf(income);
    funding = List.copyOf(funding);
    destinations = List.copyOf(destinations);
  }

  public static CashFlowSectionView from(
      PlanningTimeline timeline,
      Map<Integer, PlanningTimelineMoney> moneyByYear,
      SimulationAssumptions assumptions) {
    var row =
        timeline.years().stream()
            .filter(year -> year.state() == PlanningTimelineState.LIVE)
            .findFirst()
            .orElse(null);
    if (row == null) return null;
    PlanningTimelineMoney money = moneyByYear.get(row.year());
    if (money == null) return null;

    var income = new ArrayList<CashFlowFlowView>();
    add(income, "Rents", "Cash", money.rentalIncome(), "INCOME");
    add(income, "Bond cash income", "Cash", money.bondIncome(), "INCOME");
    if (assumptions != null) {
      int age = row.age();
      add(
          income,
          "Salary",
          "Cash",
          age < assumptions.retirementAge() ? assumptions.annualEmploymentIncome() : null,
          "INCOME");
      add(
          income,
          "Pension",
          "Cash",
          age >= assumptions.pensionStartAge() ? assumptions.annualPension() : null,
          "INCOME");
      add(
          income,
          "Events",
          "Cash",
          eventIncome(assumptions, row.year()),
          "INCOME");
    }

    var funding = new ArrayList<CashFlowFlowView>();
    add(funding, "Cash", "Spending", money.cashWithdrawal(), "FUNDING");
    add(funding, "Bonds", "Spending", money.bondWithdrawal(), "FUNDING");
    add(funding, "Equities", "Spending", money.equityWithdrawal(), "FUNDING");
    add(funding, "Real estate", "Spending", money.realEstateWithdrawal(), "FUNDING");

    var destinations = new ArrayList<CashFlowFlowView>();
    add(destinations, "Cash", "Spending", money.annualCosts(), "DESTINATION");
    BigDecimal incomeTotal = money.totalIncome();
    BigDecimal spending = money.annualCosts();
    if (incomeTotal != null && spending != null && incomeTotal.compareTo(spending) > 0)
      add(destinations, "Cash", "Surplus", incomeTotal.subtract(spending), "DESTINATION");

    BigDecimal scale = scale(income, funding, destinations);
    return new CashFlowSectionView(
        row.year(),
        withShares(income, scale),
        withShares(funding, scale),
        withShares(destinations, scale));
  }

  private static BigDecimal eventIncome(SimulationAssumptions assumptions, int year) {
    return assumptions.futureEvents().stream()
        .filter(event -> event.year() == year && event.type() == SimulationEventType.ONE_OFF_INCOME)
        .map(SimulationEvent::amount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static void add(
      List<CashFlowFlowView> flows,
      String source,
      String target,
      BigDecimal amount,
      String type) {
    if (amount != null && amount.signum() > 0)
      flows.add(new CashFlowFlowView(source, target, amount, type, null));
  }

  private static BigDecimal scale(
      List<CashFlowFlowView> income,
      List<CashFlowFlowView> funding,
      List<CashFlowFlowView> destinations) {
    return Stream.concat(income.stream(), Stream.concat(funding.stream(), destinations.stream()))
        .map(CashFlowFlowView::amount)
        .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
  }

  private static List<CashFlowFlowView> withShares(
      List<CashFlowFlowView> flows, BigDecimal scale) {
    if (scale.signum() == 0) return List.copyOf(flows);
    return flows.stream()
        .map(
            flow ->
                new CashFlowFlowView(
                    flow.source(),
                    flow.target(),
                    flow.amount(),
                    flow.type(),
                    flow.amount().multiply(BigDecimal.valueOf(100)).divide(scale, 1, RoundingMode.HALF_UP)))
        .toList();
  }

}
