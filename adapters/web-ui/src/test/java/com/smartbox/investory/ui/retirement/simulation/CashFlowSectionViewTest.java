package com.smartbox.investory.ui.retirement.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.PlanningTimeline;
import com.smartbox.investory.retirement.api.model.PlanningTimelineMoney;
import com.smartbox.investory.retirement.api.model.PlanningTimelineState;
import com.smartbox.investory.retirement.api.model.PlanningTimelineYear;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.retirement.api.model.SimulationEvent;
import com.smartbox.investory.retirement.api.model.SimulationEventType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Cash Flow Section View")
class CashFlowSectionViewTest {
  @DisplayName("includes Cash And Capital Sources But Keeps Funding Income Cash Only")
  @Test
  void includesCashAndCapitalSourcesButKeepsFundingIncomeCashOnly() {
    var money =
        new PlanningTimelineMoney(
            bd("240000"),
            bd("213683.62"),
            bd("174803.62"),
            bd("38880"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            bd("45979.88"),
            bd("45979.88"),
            null);
    var timeline =
        new PlanningTimeline(
            List.of(
                new PlanningTimelineYear(2026, 41, PlanningTimelineState.LIVE, null, null, null)));

    var flow = CashFlowSectionView.from(timeline, Map.of(2026, money), null);

    assertEquals(4, flow.income().size());
    assertEquals("Rents", flow.income().get(0).source());
    assertEquals("Bond cash income", flow.income().get(1).source());
    assertEquals(bd("213683.62"), money.totalIncome());
    assertTrue(
        flow.income().stream()
            .anyMatch(
                item ->
                    item.source().equals("Bonds")
                        && item.target().equals("Capital")
                        && item.amount().equals(bd("45979.88"))));
    assertTrue(
        flow.income().stream()
            .anyMatch(
                item ->
                    item.source().equals("Equities")
                        && item.target().equals("Capital")
                        && item.amount().equals(bd("45979.88"))));
    assertEquals("Spending", flow.destinations().get(0).target());
    assertFalse(flow.destinations().stream().anyMatch(item -> "Surplus".equals(item.target())));
    assertEquals(0, flow.funding().size());
    assertEquals(bd("213683.62"), flow.incomeUsed());
    assertEquals(bd("213683.62"), flow.totalFunded());
    assertEquals(bd("26316.38"), flow.fundingRequired());
    assertEquals(
        bd("99.9"),
        flow.income().stream()
            .map(CashFlowFlowView::sharePercent)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    assertEquals(bd("57.2"), flow.income().get(0).sharePercent());
    assertEquals(bd("12.7"), flow.income().get(1).sharePercent());
  }

  @DisplayName("converts Assumption Income Sources Into The Selected Display Currency")
  @Test
  void convertsAssumptionIncomeSourcesIntoTheSelectedDisplayCurrency() {
    var money =
        new PlanningTimelineMoney(
            bd("400"), bd("380"), bd("200"), bd("40"), null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null);
    var timelineYear =
        new PlanningTimelineYear(2026, 40, PlanningTimelineState.LIVE, null, null, null);
    var assumptions =
        SimulationAssumptions.defaults(null, 40, 90, 2026)
            .withRetirementAge(60)
            .withAnnualEmploymentIncome(bd("30"))
            .rebasedTo(
                40,
                2026,
                List.of(
                    new SimulationEvent(
                        1L, 2026, "Bonus", bd("40"), SimulationEventType.ONE_OFF_INCOME, null)));
    var toDisplayCurrency = (Function<BigDecimal, BigDecimal>) amount -> amount.multiply(bd("2"));

    var flow = CashFlowSectionView.forYear(timelineYear, money, assumptions, toDisplayCurrency);

    assertEquals(bd("380"), flow.cashIncome());
    assertEquals(
        List.of("Rents", "Bond cash income", "Salary", "Events"),
        flow.income().stream().map(CashFlowFlowView::source).toList());
    assertTrue(
        flow.income().stream()
            .anyMatch(item -> item.source().equals("Salary") && item.amount().equals(bd("60"))));
    assertTrue(
        flow.income().stream()
            .anyMatch(item -> item.source().equals("Events") && item.amount().equals(bd("80"))));
    assertEquals(
        bd("380"),
        flow.income().stream()
            .map(CashFlowFlowView::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
  }

  @DisplayName("converts Pension And Keeps Capital Returns Out Of Spendable Income")
  @Test
  void convertsPensionAndKeepsCapitalReturnsOutOfSpendableIncome() {
    var money =
        new PlanningTimelineMoney(
            bd("300"), bd("280"), bd("200"), bd("40"), null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null, null, null, null, bd("100"),
            bd("100"), null);
    var year =
        new PlanningTimelineYear(2030, 67, PlanningTimelineState.PROJECTED, null, null, null);
    var assumptions =
        SimulationAssumptions.defaults(null, 40, 90, 2026)
            .withRetirementAge(60)
            .withPensionStartAge(67)
            .withAnnualPension(bd("35"));
    var flow =
        CashFlowSectionView.forYear(year, money, assumptions, amount -> amount.multiply(bd("2")));

    assertTrue(
        flow.income().stream()
            .anyMatch(item -> item.source().equals("Pension") && item.amount().equals(bd("70"))));
    assertEquals(
        List.of("Rents", "Bond cash income", "Bonds", "Equities", "Pension"),
        flow.income().stream().map(CashFlowFlowView::source).toList());
    assertEquals(
        bd("510"),
        flow.income().stream()
            .map(CashFlowFlowView::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    assertEquals(bd("280"), flow.cashIncome());
  }

  @DisplayName("prepares Funding Coverage Segments Without Divide By Zero")
  @Test
  void preparesFundingCoverageSegmentsWithoutDivideByZero() {
    var incomeFullyFunds = view("100", "100", "0", "0");
    assertEquals(bd("100.0"), incomeFullyFunds.fundingCoveragePercent());
    assertEquals(bd("100.0"), incomeFullyFunds.incomeFundingPercent());
    assertEquals(bd("0.0"), incomeFullyFunds.capitalFundingPercent());

    var capitalCompletesFunding = view("100", "60", "40", "0");
    assertEquals(bd("100.0"), capitalCompletesFunding.fundingCoveragePercent());
    assertEquals(bd("60.0"), capitalCompletesFunding.incomeFundingPercent());
    assertEquals(bd("40.0"), capitalCompletesFunding.capitalFundingPercent());
    assertEquals(bd("0.0"), capitalCompletesFunding.unfundedPercent());

    var capitalPartiallyFunds = view("100", "60", "20", "20");
    assertEquals(bd("80.0"), capitalPartiallyFunds.fundingCoveragePercent());
    assertEquals(bd("20.0"), capitalPartiallyFunds.unfundedPercent());

    var zeroSpending = view("0", "0", "0", "0");
    assertEquals(bd("100"), zeroSpending.fundingCoveragePercent());
    assertEquals(bd("0"), zeroSpending.incomeFundingPercent());
  }

  @DisplayName("derives Remaining Unfunded When Projection Does Not Provide It")
  @Test
  void derivesRemainingUnfundedWhenProjectionDoesNotProvideIt() {
    var flow =
        new CashFlowSectionView(
            2026,
            List.of(),
            List.of(new CashFlowFlowView("Cash", "Spending", bd("20"), "FUNDING", null)),
            List.of(),
            bd("60"),
            bd("100"),
            bd("40"),
            BigDecimal.ZERO,
            null);

    assertEquals(bd("20"), flow.remainingUnfunded());
    assertEquals(bd("80.0"), flow.fundingCoveragePercent());
    assertEquals(bd("20.0"), flow.unfundedPercent());
  }

  @DisplayName("handles Zero Income Without Divide By Zero")
  @Test
  void handlesZeroIncomeWithoutDivideByZero() {
    var flow = view("100", "0", "0", "100");

    assertEquals(bd("0.0"), flow.incomeFundingPercent());
    assertEquals(bd("0.0"), flow.capitalFundingPercent());
    assertEquals(bd("100.0"), flow.unfundedPercent());
    assertEquals(
        bd("0"),
        flow.income().stream()
            .map(CashFlowFlowView::sharePercent)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
  }

  @DisplayName("income Source Shares Use Total Economic Sources")
  @Test
  void incomeSourceSharesUseTotalEconomicSources() {
    var flow =
        new CashFlowSectionView(
            2026,
            List.of(
                new CashFlowFlowView("Rents", "Cash", bd("80"), "INCOME", bd("80.0")),
                new CashFlowFlowView("Bond cash income", "Cash", bd("20"), "INCOME", bd("20.0"))),
            List.of(),
            List.of(),
            bd("100"),
            bd("100"),
            bd("0"),
            bd("0"),
            bd("0"));

    assertEquals(
        bd("100.0"),
        flow.income().stream()
            .map(CashFlowFlowView::sharePercent)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
  }

  private static CashFlowSectionView view(
      String spending, String income, String capital, String unfunded) {
    var funding =
        capital.equals("0")
            ? List.<CashFlowFlowView>of()
            : List.of(new CashFlowFlowView("Cash", "Spending", bd(capital), "FUNDING", null));
    return new CashFlowSectionView(
        2026,
        List.of(),
        funding,
        List.of(),
        bd(income),
        bd(spending),
        bd(unfunded),
        BigDecimal.ZERO,
        bd(unfunded));
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }
}
