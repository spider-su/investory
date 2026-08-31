package com.smartbox.investory.application.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.application.simulation.SimulationDecisionSummary;
import com.smartbox.investory.application.simulation.SimulationScenario;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SimulationScenarioComparisonTest {
  private static final BigDecimal ZERO = BigDecimal.ZERO;

  @Test
  void failedScenarioWithEarlierFailureIsLimiting() {
    var summaries =
        summaries(
            summary(SimulationScenario.BASE, false, null, null, "2.0", "100"),
            summary(SimulationScenario.CONSERVATIVE, true, 2055, 70, "0.5", "10"),
            summary(SimulationScenario.OPTIMISTIC, false, null, null, "4.0", "200"));

    var comparison = comparison(summaries, SimulationScenario.BASE);

    assertEquals(SimulationScenario.CONSERVATIVE, comparison.limitingScenario());
    assertEquals("Conservative fails at 2055 · age 70.", comparison.interpretation());
  }

  @Test
  void sustainableScenariosExplainTheWeakestMargin() {
    var summaries =
        summaries(
            summary(SimulationScenario.BASE, false, null, null, "2.0", "100"),
            summary(SimulationScenario.CONSERVATIVE, false, null, null, "1.2", "80"),
            summary(SimulationScenario.OPTIMISTIC, false, null, null, "4.0", "200"));

    var comparison = comparison(summaries, SimulationScenario.CONSERVATIVE);

    assertEquals(SimulationScenario.CONSERVATIVE, comparison.limitingScenario());
    assertEquals(
        "Plan remains sustainable in all scenarios. Conservative is the limiting scenario; "
            + "minimum reserve coverage is 1.2 years.",
        comparison.interpretation());
    assertEquals(3, comparison.scenarios().size());
  }

  @Test
  void incomeFundedScenarioIsNotRankedWorseThanReserveScenario() {
    var summaries =
        summaries(
            summary(SimulationScenario.BASE, false, null, null, "0.0", "100", false),
            summary(SimulationScenario.CONSERVATIVE, false, null, null, "2.0", "90", true),
            summary(SimulationScenario.OPTIMISTIC, false, null, null, "4.0", "200", true));

    var comparison = comparison(summaries, SimulationScenario.BASE);

    assertEquals(SimulationScenario.CONSERVATIVE, comparison.limitingScenario());
    assertEquals("N/A", comparison.scenarios().getFirst().minimumReserveCoverageDisplay());
  }

  private static SimulationScenarioComparison comparison(
      Map<SimulationScenario, SimulationDecisionSummary> summaries, SimulationScenario selected) {
    Map<SimulationScenario, SimulationDecisionSummaryMoney> display =
        new EnumMap<>(SimulationScenario.class);
    summaries.forEach((scenario, summary) -> display.put(scenario, money(summary)));
    return SimulationScenarioComparison.from(summaries, display, selected);
  }

  private static SimulationDecisionSummaryMoney money(SimulationDecisionSummary summary) {
    return new SimulationDecisionSummaryMoney(
        summary.scenario(),
        summary.failed(),
        summary.finalNetWorth(),
        summary.minimumLiquidAssets(),
        summary.lowestNetWorth(),
        summary.lifetimeActualWithdrawals(),
        summary.totalUnfundedAmount(),
        summary.firstYearPassiveIncomeCoverage(),
        summary.minimumPassiveIncomeCoverage(),
        summary.firstFailureYear(),
        summary.firstFailureAge(),
        summary.totalEquityHarvested(),
        summary.totalEmergencyEquityWithdrawals(),
        summary.totalManualLiquidReserveWithdrawals(),
        summary.minimumManualLiquidReserve(),
        summary.minimumSafeReserveCoverageYears(),
        summary.yearsWithEquityHarvest(),
        summary.yearsUsingEmergencyEquity(),
        summary.finalSafeReserve(),
        summary.recurringFundingGapRequired());
  }

  private static Map<SimulationScenario, SimulationDecisionSummary> summaries(
      SimulationDecisionSummary... values) {
    Map<SimulationScenario, SimulationDecisionSummary> result =
        new EnumMap<>(SimulationScenario.class);
    for (SimulationDecisionSummary value : values) {
      result.put(value.scenario(), value);
    }
    return result;
  }

  private static SimulationDecisionSummary summary(
      SimulationScenario scenario,
      boolean failed,
      Integer failureYear,
      Integer failureAge,
      String reserveCoverage,
      String spendableAssets) {
    return summary(
        scenario, failed, failureYear, failureAge, reserveCoverage, spendableAssets, true);
  }

  private static SimulationDecisionSummary summary(
      SimulationScenario scenario,
      boolean failed,
      Integer failureYear,
      Integer failureAge,
      String reserveCoverage,
      String spendableAssets,
      boolean recurringFundingGapRequired) {
    return new SimulationDecisionSummary(
        scenario,
        failed,
        new BigDecimal("1000"),
        ZERO,
        ZERO,
        new BigDecimal(spendableAssets),
        2026,
        40,
        ZERO,
        2026,
        40,
        ZERO,
        ZERO,
        failureYear,
        failureAge,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        ZERO,
        new BigDecimal(reserveCoverage),
        0,
        0,
        ZERO,
        recurringFundingGapRequired);
  }
}
