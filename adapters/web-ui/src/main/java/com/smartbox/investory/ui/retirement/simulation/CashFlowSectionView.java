package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.RetirementFinancialCalculations;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.PlanningTimeline;
import com.smartbox.investory.retirement.api.model.PlanningTimelineMoney;
import com.smartbox.investory.retirement.api.model.PlanningTimelineState;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import java.math.BigDecimal;
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
    BigDecimal funded,
    boolean live,
    BigDecimal incomeUsed,
    BigDecimal capitalFunding) {

  public BigDecimal fundingRequired() {
    return fundingGap;
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
    return incomeUsed;
  }

  public BigDecimal capitalFunding() {
    return capitalFunding;
  }

  public BigDecimal capitalFundingShown() {
    return live ? null : capitalFunding();
  }

  public String periodNote() {
    return live ? "Full-year plan · current balance to expected year end" : null;
  }

  public BigDecimal totalFunded() {
    return funded;
  }

  public BigDecimal fundedAmount() {
    return totalFunded();
  }

  public BigDecimal remainingUnfunded() {
    return unfunded;
  }

  public BigDecimal fundingCoveragePercent() {
    if (spending == null || spending.signum() == 0) return BigDecimal.valueOf(100);
    return funded == null
        ? BigDecimal.ZERO
        : RetirementFinancialCalculations.cappedPercentageOf(funded, spending);
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
        null,
        false,
        null,
        null);
  }

  private BigDecimal percentOfSpending(BigDecimal amount) {
    if (spending == null || spending.signum() == 0 || amount == null) return BigDecimal.ZERO;
    return RetirementFinancialCalculations.cappedPercentageOf(amount, spending);
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
      com.smartbox.investory.retirement.api.model.PlanningTimelineYear row,
      PlanningTimelineMoney money,
      SimulationAssumptions assumptions) {
    return forYear(row, money, assumptions, Function.identity());
  }

  public static CashFlowSectionView forYear(
      com.smartbox.investory.retirement.api.model.PlanningTimelineYear row,
      PlanningTimelineMoney money,
      SimulationAssumptions assumptions,
      Function<BigDecimal, BigDecimal> displayMoney) {
    if (money == null) return null;

    var income = new ArrayList<CashFlowFlowView>();
    add(income, "Rents", "Cash", money.rentalIncome(), "INCOME");
    add(income, "Bond cash income", "Cash", money.bondIncome(), "INCOME");
    add(income, "Bonds", "Capital", money.bondReturn(), "RETURN");
    add(income, "Equities", "Capital", money.equityReturn(), "RETURN");
    add(income, "Salary", "Cash", money.employmentIncome(), "INCOME");
    add(income, "Pension", "Cash", money.pensionIncome(), "INCOME");
    add(income, "Events", "Cash", money.eventIncome(), "INCOME");

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

    BigDecimal economicSources =
        income.stream().map(CashFlowFlowView::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    return new CashFlowSectionView(
        row.year(),
        withShares(income, economicSources),
        List.copyOf(funding),
        List.copyOf(destinations),
        incomeTotal,
        spending,
        money.fundingGap(),
        money.fundingSurplus(),
        money.unfunded(),
        money.funded(),
        row.state() == PlanningTimelineState.LIVE,
        money.incomeUsed(),
        money.capitalFunding());
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
                    RetirementFinancialCalculations.percentageOf(flow.amount(), scale)))
        .toList();
  }
}
