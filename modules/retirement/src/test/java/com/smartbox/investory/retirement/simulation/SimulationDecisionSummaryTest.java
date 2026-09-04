package com.smartbox.investory.retirement.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbox.investory.retirement.api.model.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Simulation Decision Summary")
class SimulationDecisionSummaryTest {
  @DisplayName("derives Decision Metrics From Yearly Results")
  @Test
  void derivesDecisionMetricsFromYearlyResults() {
    SimulationAssumptions assumptions = testAssumptions(42);
    List<SimulationYear> years =
        List.of(
            year(40, 2026, "1000", "100", "20", "150", "70", "1000", false, "0"),
            year(41, 2027, "1200", "100", "25", "100", "65", "900", true, "30"),
            year(42, 2028, "1100", "110", "30", "100", "50", "950", false, "0"));
    SimulationDecisionSummary summary =
        SimulationDecisionSummary.from(
            new SimulationResult(SimulationScenario.BASE, true, 41, new BigDecimal("30"), years),
            assumptions);
    assertEquals(new BigDecimal("950"), summary.finalLiquidAssets());
    assertEquals(new BigDecimal("900"), summary.minimumLiquidAssets());
    assertEquals(2027, summary.minimumLiquidYear());
    assertEquals(41, summary.minimumLiquidAge());
    assertEquals(new BigDecimal("1000"), summary.lowestNetWorth());
    assertEquals(2026, summary.lowestNetWorthYear());
    assertEquals(new BigDecimal("185"), summary.lifetimeRequiredPortfolioFunding());
    assertEquals(new BigDecimal("155"), summary.lifetimeActualWithdrawals());
    assertEquals(new BigDecimal("155"), summary.lifetimeWithdrawals());
    assertEquals(new BigDecimal("30"), summary.totalUnfundedAmount());
    assertEquals(
        summary.lifetimeRequiredPortfolioFunding(),
        summary.lifetimeActualWithdrawals().add(summary.totalUnfundedAmount()));
    assertEquals(BigDecimal.ZERO, summary.totalEquityHarvested());
    assertEquals(BigDecimal.ZERO, summary.totalEmergencyEquityWithdrawals());
    assertEquals(2027, summary.firstFailureYear());
    assertEquals(new BigDecimal("1.25000000"), summary.firstYearPassiveIncomeCoverage());
    assertEquals(new BigDecimal("0.71428571"), summary.minimumPassiveIncomeCoverage());
    assertFalse(summary.recurringFundingGapRequired());
  }

  @DisplayName("empty Result Has Safe Metrics")
  @Test
  void emptyResultHasSafeMetrics() {
    SimulationAssumptions assumptions = testAssumptions(40);
    SimulationDecisionSummary summary =
        SimulationDecisionSummary.from(
            new SimulationResult(SimulationScenario.BASE, false, null, BigDecimal.ZERO, List.of()),
            assumptions);
    assertEquals(BigDecimal.ZERO, summary.finalNetWorth());
    assertNull(summary.firstFailureYear());
  }

  @DisplayName("does Not Treat Income Funded Retirement Years As Zero Reserve Coverage")
  @Test
  void doesNotTreatIncomeFundedRetirementYearsAsZeroReserveCoverage() {
    SimulationAssumptions assumptions = testAssumptions(40);
    SimulationDecisionSummary summary =
        SimulationDecisionSummary.from(
            new SimulationResult(
                SimulationScenario.BASE,
                false,
                null,
                BigDecimal.ZERO,
                List.of(year(40, 2026, "1000", "100", "50", "150", "0", "900", false, "0"))),
            assumptions);

    assertFalse(summary.recurringFundingGapRequired());
    assertEquals(BigDecimal.ZERO, summary.minimumSafeReserveCoverageYears());
  }

  @DisplayName("chart Data Mirrors Yearly Projection")
  @Test
  void chartDataMirrorsYearlyProjection() {
    SimulationAssumptions assumptions = testAssumptions(40);
    SimulationYear projection = year(40, 2026, "1000", "100", "20", "150", "70", "900", false, "0");
    SimulationChartData charts =
        SimulationChartData.from(
            java.util.Map.of(
                SimulationScenario.BASE,
                new SimulationResult(
                    SimulationScenario.BASE, false, null, ZERO, List.of(projection))),
            assumptions);
    assertEquals(2026, charts.balances().get(SimulationScenario.BASE).get(0).year());
    assertEquals(
        projection.endNetWorth(), charts.balances().get(SimulationScenario.BASE).get(0).netWorth());
    assertEquals(projection.totalIncome(), charts.incomeSpending().get(0).recurringIncome());
    assertEquals(
        projection.coreExpenses().add(projection.discretionaryExpenses()),
        charts.incomeSpending().get(0).plannedSpending());
    assertEquals(projection.equityEnd(), charts.composition().get(0).equities());
    assertEquals(2026, charts.incomeSpending().getFirst().year());
    assertEquals(2026, charts.composition().getFirst().year());
    assertEquals(2026, charts.funding().get(SimulationScenario.BASE).getFirst().year());
    assertEquals(2026, charts.reserves().get(SimulationScenario.BASE).getFirst().year());
    assertEquals(2026, charts.metadata().retirementYear());
    assertEquals(2026, charts.metadata().horizonEndYear());
    assertTrue(charts.metadata().failures().isEmpty());
  }

  @DisplayName(
      "simplified Composition Groups Manual Cash And Contractual Bonds Without Changing Net Worth")
  @Test
  void simplifiedCompositionGroupsManualCashAndContractualBondsWithoutChangingNetWorth() {
    SimulationAssumptions assumptions = SimulationAssumptions.defaults(40, 40, 2026);
    SimulationYear year =
        new SimulationYear(
            40,
            2026,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            new BigDecimal("10"),
            new BigDecimal("20"),
            ZERO,
            new BigDecimal("30"),
            ZERO,
            new BigDecimal("40"),
            ZERO,
            new BigDecimal("50"),
            ZERO,
            ZERO,
            ZERO,
            new BigDecimal("60"),
            ZERO,
            new BigDecimal("70"),
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            new BigDecimal("200"),
            false,
            ZERO,
            SimulationLifecyclePhase.WORKING,
            ZERO,
            ZERO,
            false,
            ZERO,
            ZERO,
            ZERO,
            ZERO,
            new SimulationFunding(
                ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO));
    SimulationChartData charts =
        SimulationChartData.from(
            Map.of(
                SimulationScenario.BASE,
                new SimulationResult(SimulationScenario.BASE, false, null, ZERO, List.of(year))),
            assumptions);
    SimulationChartData.CompositionPoint point = charts.composition().getFirst();
    assertFalse(charts.funding().get(SimulationScenario.BASE).isEmpty());
    assertFalse(charts.reserves().get(SimulationScenario.BASE).isEmpty());
    assertEquals(new BigDecimal("80"), point.cash());
    assertEquals(new BigDecimal("100"), point.bonds());
    assertEquals(BigDecimal.ZERO, point.apartments());
    assertEquals(new BigDecimal("40"), point.equities());
  }

  private static SimulationAssumptions testAssumptions(int endAge) {
    return new SimulationAssumptions(
        40,
        endAge,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        99,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        2026,
        BigDecimal.ZERO,
        List.of(),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        SimulationFundingStrategy.SIMPLE_WATERFALL,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        true,
        40,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        SimulationAssumptions.DEFAULT_FUNDING_ORDER,
        ExpenseProfile.EMPTY);
  }

  private static SimulationYear year(
      int age,
      int calendarYear,
      String netWorth,
      String core,
      String discretionary,
      String passive,
      String withdrawal,
      String liquid,
      boolean failed,
      String unfunded) {
    BigDecimal n = new BigDecimal(netWorth),
        c = new BigDecimal(core),
        d = new BigDecimal(discretionary),
        p = new BigDecimal(passive),
        w = new BigDecimal(withdrawal),
        l = new BigDecimal(liquid),
        u = new BigDecimal(unfunded);
    return new SimulationYear(
        age,
        calendarYear,
        n,
        c,
        d,
        BigDecimal.ZERO,
        c.add(d),
        p,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        p,
        w,
        w.subtract(u),
        l,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        l,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        l,
        l,
        l,
        ZERO,
        n,
        failed,
        u,
        SimulationLifecyclePhase.WORKING,
        ZERO,
        ZERO,
        false,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        new SimulationFunding(
            ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO));
  }

  private static final BigDecimal ZERO = BigDecimal.ZERO;
}
