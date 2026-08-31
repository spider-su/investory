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
import java.util.function.Function;

/** Selected-year cash-flow summary. Cash funding and economic sources remain separate facts. */
public record CashFlowSectionView(
    int year,
    List<CashFlowFlowView> income,
    List<CashFlowFlowView> funding,
    List<CashFlowFlowView> destinations,
    BigDecimal cashIncome,
    BigDecimal spending,
    BigDecimal fundingGap,
    BigDecimal fundingSurplus,
    BigDecimal unfunded,
    boolean live) {

  public BigDecimal fundingRequired() {
    return positiveDifference(spending, cashIncome);
  }

  /** Structured names used by the funding summary; values share one temporal basis. */
  public BigDecimal spendingRequired() {
    return spending;
  }

  public BigDecimal incomeAmount() {
    return cashIncome;
  }

  public BigDecimal capitalFundingAmount() {
    return capitalFunding();
  }

  public BigDecimal unfundedAmount() {
    return remainingUnfunded();
  }

  public BigDecimal incomeUsed() {
    if (spending == null || cashIncome == null) return BigDecimal.ZERO;
    return cashIncome.min(spending).max(BigDecimal.ZERO);
  }

  public BigDecimal capitalFunding() {
    return funding.stream().map(CashFlowFlowView::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  public BigDecimal capitalFundingShown() {
    return live ? null : capitalFunding();
  }

  public String periodNote() {
    return live ? "Full-year plan · current balance to expected year end" : null;
  }

  public BigDecimal totalFunded() {
    return incomeUsed().add(capitalFunding());
  }

  public BigDecimal fundedAmount() {
    return totalFunded();
  }

  public BigDecimal remainingUnfunded() {
    if (unfunded != null) return unfunded.max(BigDecimal.ZERO);
    if (spending == null) return BigDecimal.ZERO;
    return spending.subtract(totalFunded()).max(BigDecimal.ZERO);
  }

  public BigDecimal fundingCoveragePercent() {
    if (spending == null || spending.signum() == 0) return BigDecimal.valueOf(100);
    return fundedAmount()
        .min(spending)
        .max(BigDecimal.ZERO)
        .multiply(BigDecimal.valueOf(100))
        .divide(spending, 1, RoundingMode.HALF_UP);
  }

  public BigDecimal incomeFundingPercent() {
    return percentOfSpending(incomeUsed());
  }

  public BigDecimal capitalFundingPercent() {
    return percentOfSpending(capitalFunding());
  }

  public BigDecimal unfundedPercent() {
    return percentOfSpending(remainingUnfunded());
  }

  public CashFlowSectionView(
      int year,
      List<CashFlowFlowView> income,
      List<CashFlowFlowView> funding,
      List<CashFlowFlowView> destinations,
      BigDecimal cashIncome,
      BigDecimal spending,
      BigDecimal fundingGap,
      BigDecimal fundingSurplus,
      BigDecimal unfunded) {
    this(
        year,
        income,
        funding,
        destinations,
        cashIncome,
        spending,
        fundingGap,
        fundingSurplus,
        unfunded,
        false);
  }

  private BigDecimal percentOfSpending(BigDecimal amount) {
    if (spending == null || spending.signum() == 0 || amount == null) return BigDecimal.ZERO;
    return amount
        .max(BigDecimal.ZERO)
        .min(spending)
        .multiply(BigDecimal.valueOf(100))
        .divide(spending, 1, RoundingMode.HALF_UP);
  }

  public CashFlowSectionView {
    income = List.copyOf(income);
    funding = List.copyOf(funding);
    destinations = List.copyOf(destinations);
  }

  public static CashFlowSectionView from(
      PlanningTimeline timeline,
      Map<Integer, PlanningTimelineMoney> moneyByYear,
      SimulationAssumptions assumptions) {
    return from(timeline, moneyByYear, assumptions, Function.identity());
  }

  public static CashFlowSectionView from(
      PlanningTimeline timeline,
      Map<Integer, PlanningTimelineMoney> moneyByYear,
      SimulationAssumptions assumptions,
      Function<BigDecimal, BigDecimal> displayMoney) {
    var row =
        timeline.years().stream()
            .filter(year -> year.state() == PlanningTimelineState.LIVE)
            .findFirst()
            .orElse(null);
    return row == null
        ? null
        : forYear(row, moneyByYear.get(row.year()), assumptions, displayMoney);
  }

  public static CashFlowSectionView forYear(
      com.smartbox.investory.retirement.planning.PlanningTimelineYear row,
      PlanningTimelineMoney money,
      SimulationAssumptions assumptions) {
    return forYear(row, money, assumptions, Function.identity());
  }

  public static CashFlowSectionView forYear(
      com.smartbox.investory.retirement.planning.PlanningTimelineYear row,
      PlanningTimelineMoney money,
      SimulationAssumptions assumptions,
      Function<BigDecimal, BigDecimal> displayMoney) {
    if (money == null) return null;

    var income = new ArrayList<CashFlowFlowView>();
    add(income, "Rents", "Cash", money.rentalIncome(), "INCOME");
    add(income, "Bond cash income", "Cash", money.bondIncome(), "INCOME");
    add(income, "Bonds", "Capital", money.bondReturn(), "RETURN");
    add(income, "Equities", "Capital", money.equityReturn(), "RETURN");
    if (assumptions != null
        && row.state() != PlanningTimelineState.ACTUAL
        && row.state() != PlanningTimelineState.NEEDS_REVIEW) {
      int age = row.age();
      add(
          income,
          "Salary",
          "Cash",
          age < assumptions.retirementAge()
              ? displayMoney.apply(assumptions.annualEmploymentIncome())
              : null,
          "INCOME");
      add(
          income,
          "Pension",
          "Cash",
          age >= assumptions.pensionStartAge()
              ? displayMoney.apply(assumptions.annualPension())
              : null,
          "INCOME");
      add(income, "Events", "Cash", eventIncome(assumptions, row.year(), displayMoney), "INCOME");
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

    BigDecimal fundingGap =
        money.fundingGap() == null ? positiveDifference(spending, incomeTotal) : money.fundingGap();
    BigDecimal economicSources =
        income.stream().map(CashFlowFlowView::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    return new CashFlowSectionView(
        row.year(),
        withShares(income, economicSources),
        List.copyOf(funding),
        List.copyOf(destinations),
        incomeTotal,
        spending,
        fundingGap,
        positiveDifference(incomeTotal, spending),
        money.unfunded(),
        row.state() == PlanningTimelineState.LIVE);
  }

  private static BigDecimal positiveDifference(BigDecimal left, BigDecimal right) {
    if (left == null || right == null) return null;
    BigDecimal difference = left.subtract(right);
    return difference.signum() > 0 ? difference : BigDecimal.ZERO;
  }

  private static BigDecimal eventIncome(
      SimulationAssumptions assumptions, int year, Function<BigDecimal, BigDecimal> displayMoney) {
    BigDecimal amount =
        assumptions.futureEvents().stream()
            .filter(
                event -> event.year() == year && event.type() == SimulationEventType.ONE_OFF_INCOME)
            .map(SimulationEvent::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return displayMoney.apply(amount);
  }

  private static void add(
      List<CashFlowFlowView> flows, String source, String target, BigDecimal amount, String type) {
    if (amount != null && amount.signum() > 0)
      flows.add(new CashFlowFlowView(source, target, amount, type, null));
  }

  private static List<CashFlowFlowView> withShares(List<CashFlowFlowView> flows, BigDecimal scale) {
    if (scale == null || scale.signum() == 0) {
      return flows.stream()
          .map(
              flow ->
                  new CashFlowFlowView(
                      flow.source(), flow.target(), flow.amount(), flow.type(), BigDecimal.ZERO))
          .toList();
    }
    return flows.stream()
        .map(
            flow ->
                new CashFlowFlowView(
                    flow.source(),
                    flow.target(),
                    flow.amount(),
                    flow.type(),
                    flow.amount()
                        .multiply(BigDecimal.valueOf(100))
                        .divide(scale, 1, RoundingMode.HALF_UP)))
        .toList();
  }
}
