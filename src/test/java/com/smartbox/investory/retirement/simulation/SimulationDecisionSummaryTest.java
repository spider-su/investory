package com.smartbox.investory.retirement.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SimulationDecisionSummaryTest {
  @Test
  void derivesDecisionMetricsFromYearlyResults() {
    SimulationAssumptions assumptions =
        new SimulationAssumptions(
            40,
            42,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            99,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            2026);
    List<SimulationYear> years =
        List.of(
            year(40, 0, "1000", "100", "20", "150", "70", "1000", false, "0"),
            year(41, 1, "1200", "100", "25", "100", "65", "900", true, "30"),
            year(42, 2, "1100", "110", "30", "100", "50", "950", false, "0"));
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

  @Test
  void emptyResultHasSafeMetrics() {
    SimulationAssumptions assumptions =
        new SimulationAssumptions(
            40,
            40,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            99,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            2026);
    SimulationDecisionSummary summary =
        SimulationDecisionSummary.from(
            new SimulationResult(SimulationScenario.BASE, false, null, BigDecimal.ZERO, List.of()),
            assumptions);
    assertEquals(BigDecimal.ZERO, summary.finalNetWorth());
    assertNull(summary.firstFailureYear());
  }

  @Test
  void doesNotTreatIncomeFundedRetirementYearsAsZeroReserveCoverage() {
    SimulationAssumptions assumptions =
        new SimulationAssumptions(
            40,
            40,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            99,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            2026);
    SimulationDecisionSummary summary =
        SimulationDecisionSummary.from(
            new SimulationResult(
                SimulationScenario.BASE,
                false,
                null,
                BigDecimal.ZERO,
                List.of(year(40, 0, "1000", "100", "50", "150", "0", "900", false, "0"))),
            assumptions);

    assertFalse(summary.recurringFundingGapRequired());
    assertEquals(BigDecimal.ZERO, summary.minimumSafeReserveCoverageYears());
  }

  @Test
  void chartDataMirrorsYearlyProjection() {
    SimulationAssumptions assumptions =
        new SimulationAssumptions(
            40,
            40,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            99,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            2026);
    SimulationYear projection = year(40, 0, "1000", "100", "20", "150", "70", "900", false, "0");
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
    assertEquals(
        projection.passiveIncome().add(projection.pensionIncome()),
        charts.incomeSpending().get(0).recurringIncome());
    assertEquals(
        projection.coreExpenses().add(projection.discretionaryExpenses()),
        charts.incomeSpending().get(0).plannedSpending());
    assertEquals(projection.equityEnd(), charts.composition().get(0).equities());
  }

  @Test
  void simplifiedCompositionGroupsManualCashAndContractualBondsWithoutChangingNetWorth() {
    SimulationAssumptions assumptions =
        new SimulationAssumptions(
            40,
            40,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            99,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            2026);
    SimulationYear year =
        new SimulationYear(
            40,
            0,
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
            ZERO);
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

  private static SimulationYear year(
      int age,
      int offset,
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
        offset,
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
        l,
        l,
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
        n,
        failed,
        u);
  }

  private static final BigDecimal ZERO = BigDecimal.ZERO;
}
