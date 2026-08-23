package com.smartbox.investory.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RetirementAnalysisTemplateContractTest {
  @Test
  void analysisIsASeparateReadOnlyBoardOverTheSimulationResult() throws Exception {
    String html =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/retirement-analysis.html"));

    assertTrue(html.contains("Analysis"));
    assertTrue(html.contains("data-analysis-tab=\"overview\""));
    assertTrue(html.contains("data-analysis-tab=\"cash-flow\""));
    assertTrue(html.contains("data-analysis-tab=\"risk\""));
    assertTrue(html.contains("data-analysis-tab=\"scenarios\""));
    assertTrue(html.contains("analysisPage.selectedSummary"));
    assertTrue(html.contains("analysisPage.scenarios"));
    assertTrue(html.contains("Minimum liquid assets"));
    assertTrue(html.contains("First failure"));
    assertTrue(html.contains("Base-plan sensitivity"));
    assertTrue(html.contains("Base / Conservative comparison"));
    assertFalse(html.contains("data-analysis-tab=\"portfolio\""));
    assertFalse(html.contains("analysis-reserve"));
    assertFalse(html.contains("Reserve coverage"));
    assertTrue(html.contains("/js/retirement-analysis.js"));
    assertFalse(html.contains("simulation engine"));
  }

  @Test
  void simulationAndAnalysisArePeerRoutes() throws Exception {
    String simulation =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/simulation.html"));
    String analysis =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/retirement-analysis.html"));

    assertTrue(simulation.contains("href=\"#\">Simulation</a>"));
    assertTrue(simulation.contains("/analysis("));
    assertTrue(simulation.contains("planId=${simulationPage.selectedPlanId}"));
    assertTrue(simulation.contains("/simulation/plan/edit("));
    assertTrue(analysis.contains("/simulation("));
    assertTrue(analysis.contains("planId=${analysisPage.planId}"));
    assertTrue(analysis.contains("planningDisplayCurrency=${analysisPage.displayCurrency}"));
    assertTrue(analysis.contains("selectedScenario=${analysisPage.selectedScenario}"));
    assertTrue(analysis.contains("aria-current=\"page\""));
  }
}
