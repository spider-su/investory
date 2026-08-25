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
    assertTrue(html.contains("Economic risks"));
    assertTrue(html.contains("Planning levers"));
    assertTrue(html.contains("moreHarmfulDirection"));
    assertTrue(html.contains("analysisPage.flexibility"));
    assertTrue(html.contains("analysisPage.flexibility.spending.baseLimit"));
    assertTrue(html.contains("analysisPage.flexibility.spending.conservativeLimit"));
    assertTrue(html.contains("analysisPage.flexibility.spending.baseHeadroom"));
    assertTrue(html.contains("analysisPage.flexibility.spending.conservativeHeadroom"));
    assertTrue(html.contains("analysisPage.flexibility.retirement.base.earliest"));
    assertTrue(html.contains("analysisPage.flexibility.retirement.base.headroom"));
    assertTrue(html.contains("analysisPage.flexibility.retirement.conservative.earliest"));
    assertTrue(html.contains("analysisPage.flexibility.retirement.conservative.headroom"));
    assertTrue(html.contains("analysisPage.analysisAvailable"));
    assertTrue(html.contains("Analysis unavailable"));
    assertTrue(html.contains("No future planning years remain"));
    assertTrue(
        html.contains(
            "<main class=\"iv-app iv-planning-page iv-simulation-page iv-retirement-surface\">"));
    assertFalse(html.contains("container-xl py-4"));
    assertFalse(html.contains("Evaluated horizon"));
    assertTrue(html.contains("Active plan:"));
    assertFalse(html.contains("data-analysis-tab=\"portfolio\""));
    assertFalse(html.contains("analysis-reserve"));
    assertTrue(html.contains("Final net worth"));
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
    String header =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/fragments/app-header.html"));

    assertTrue(simulation.contains("planningHeader('simulation'"));
    assertTrue(header.contains("/analysis("));
    assertTrue(simulation.contains("planId=${simulationPage.selectedPlanId}"));
    assertTrue(header.contains("/simulation/plan/edit("));
    assertTrue(header.contains("/simulation("));
    assertTrue(header.contains("planId=${analysisPage.planId}"));
    assertTrue(header.contains("planningDisplayCurrency=${analysisPage.displayCurrency}"));
    assertTrue(header.contains("selectedScenario=${analysisPage.selectedScenario}"));
    assertTrue(header.contains("aria-current=\"page\""));
  }
}
