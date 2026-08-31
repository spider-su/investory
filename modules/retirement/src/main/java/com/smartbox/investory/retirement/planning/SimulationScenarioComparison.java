package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.retirement.simulation.SimulationDecisionSummary;
import com.smartbox.investory.retirement.simulation.SimulationScenario;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Compact comparison of scenario outputs already calculated by the simulator. */
public record SimulationScenarioComparison(
    List<Scenario> scenarios, String interpretation, SimulationScenario limitingScenario) {

  public static SimulationScenarioComparison from(
      Map<SimulationScenario, SimulationDecisionSummary> summaries,
      Map<SimulationScenario, SimulationDecisionSummaryMoney> displaySummaries,
      SimulationScenario selectedScenario) {
    Map<SimulationScenario, SimulationDecisionSummary> available =
        new EnumMap<>(SimulationScenario.class);
    available.putAll(summaries);
    if (available.isEmpty()) {
      return new SimulationScenarioComparison(List.of(), "No scenario results available.", null);
    }
    SimulationScenario limiting =
        available.values().stream().min(worstFirst()).orElseThrow().scenario();
    List<Scenario> rows =
        List.of(
                SimulationScenario.BASE,
                SimulationScenario.CONSERVATIVE,
                SimulationScenario.OPTIMISTIC,
                SimulationScenario.CUSTOM)
            .stream()
            .filter(available::containsKey)
            .map(
                scenario -> {
                  SimulationDecisionSummary summary = available.get(scenario);
                  SimulationDecisionSummaryMoney display = displaySummaries.get(scenario);
                  return new Scenario(
                      scenario,
                      label(scenario),
                      scenario == selectedScenario,
                      summary.failed(),
                      healthLabel(summary),
                      display.minimumSafeReserveCoverageYearsDisplay(),
                      failureDisplay(summary),
                      display.minimumSpendableAssetsDisplay(),
                      display.finalNetWorthDisplay(),
                      display.finalSpendableAssetsDisplay());
                })
            .toList();
    return new SimulationScenarioComparison(rows, interpretation(available, limiting), limiting);
  }

  private static Comparator<SimulationDecisionSummary> worstFirst() {
    return Comparator.comparing(SimulationDecisionSummary::failed)
        .reversed()
        .thenComparing(
            summary ->
                summary.firstFailureYear() == null ? Integer.MAX_VALUE : summary.firstFailureYear())
        .thenComparing(SimulationDecisionSummary::minimumLiquidAssets);
  }

  private static String interpretation(
      Map<SimulationScenario, SimulationDecisionSummary> summaries, SimulationScenario limiting) {
    SimulationDecisionSummary worst = summaries.get(limiting);
    if (!worst.failed()) {
      return "Plan remains sustainable in all scenarios. "
          + label(limiting)
          + " is the limiting scenario; minimum liquid assets are "
          + PlanningPresentation.wholeNumber(worst.minimumLiquidAssets())
          + ".";
    }
    long failed = summaries.values().stream().filter(SimulationDecisionSummary::failed).count();
    String failure = failureDisplay(worst);
    return failed == 1
        ? label(limiting) + " fails at " + failure + "."
        : failed + " scenarios fail; " + label(limiting) + " first fails at " + failure + ".";
  }

  private static String failureDisplay(SimulationDecisionSummary summary) {
    if (summary.firstFailureYear() == null) {
      return "None";
    }
    if (summary.firstFailureAge() == null) {
      return summary.firstFailureYear().toString();
    }
    return summary.firstFailureYear() + " · age " + summary.firstFailureAge();
  }

  private static String healthLabel(SimulationDecisionSummary summary) {
    if (summary.failed()) return "Fails";
    if (summary.recurringFundingGapRequired()
        && summary.minimumLiquidAssets().compareTo(java.math.BigDecimal.ONE) < 0) {
      return "Fragile";
    }
    return "Sustainable";
  }

  private static String label(SimulationScenario scenario) {
    String value = scenario.name().toLowerCase();
    return Character.toUpperCase(value.charAt(0)) + value.substring(1);
  }

  public record Scenario(
      SimulationScenario scenario,
      String label,
      boolean selected,
      boolean failed,
      String statusLabel,
      String minimumReserveCoverageDisplay,
      String firstFailureDisplay,
      String minimumSpendableAssetsDisplay,
      String finalNetWorthDisplay,
      String finalSpendableAssetsDisplay) {
    public String minimumLiquidAssetsDisplay() {
      return minimumSpendableAssetsDisplay;
    }
  }
}
