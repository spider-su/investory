package com.smartbox.investory.controllers.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SimulationTemplateContractTest {
  @Test
  void javascriptInlineTemplateDoesNotUseArraySyntaxThatThymeleafParsesAsAnExpression()
      throws Exception {
    String html = Files.readString(Path.of("src/main/resources/templates/simulation.html"));
    assertAll(
        () -> assertFalse(html.contains("flowFields=[[")),
        () -> assertFalse(html.contains("fields=[[")),
        () -> assertTrue(html.contains("const funding = simulationCharts.funding")),
        () -> assertTrue(html.contains("requiredPortfolioFunding")),
        () -> assertTrue(html.contains("safeReserve")),
        () -> assertTrue(html.contains("plannedSpending")),
        () -> assertTrue(html.contains("simulation-liquid")),
        () -> assertFalse(html.contains("simulation-composition")),
        () -> assertFalse(html.contains("Market fixed income\"}, {key: \"equity")));
  }

  @Test
  void planningTimelineIsSummaryOnlyAndRoutesHistoricalWorkToDetail() throws Exception {
    String html = Files.readString(Path.of("src/main/resources/templates/simulation.html"));
    assertAll(
        () -> assertTrue(html.contains("<th>Action</th>")),
        () -> assertTrue(html.contains("<th>Annual costs</th>")),
        () -> assertTrue(html.contains("<th>Portfolio withdrawal</th>")),
        () -> assertTrue(html.contains("<th>Cash reserve</th>")),
        () -> assertTrue(html.contains("<th>Bonds value</th>")),
        () -> assertTrue(html.contains("<th>Equity gain</th>")),
        () -> assertFalse(html.contains("<th>Net worth</th>")),
        () -> assertFalse(html.contains("<th>Safe reserve</th>")),
        () -> assertTrue(html.contains("timeline.firstFailureYear()")),
        () -> assertTrue(html.contains("'Failed'")),
        () -> assertTrue(html.contains("Review")),
        () -> assertTrue(html.contains("planningDisplayCurrency=${planningDisplayCurrency}")),
        () ->
            assertTrue(
                html.contains(
                    "timeline.years.?[state.name() == 'ACTUAL' and year == 2025].isEmpty()")),
        () -> assertTrue(html.contains("currentYearCloseAllowed")),
        () -> assertFalse(html.contains("Approve and close")),
        () -> assertFalse(html.contains("Save correction")),
        () -> assertFalse(html.contains("/timeline/past/{year}/manual")),
        () -> assertFalse(html.contains("/timeline/past/{year}/close")));
  }

  @Test
  void simulationKeepsDecisionContentVisibleAndMovesConfigurationToEditor() throws Exception {
    String html = Files.readString(Path.of("src/main/resources/templates/simulation.html"));
    String editor =
        Files.readString(Path.of("src/main/resources/templates/simulation-plan-edit.html"));
    String header =
        Files.readString(Path.of("src/main/resources/templates/fragments/app-header.html"));
    assertAll(
        () -> assertTrue(html.contains("planningHeader('simulation'")),
        () -> assertTrue(html.contains("Retirement outlook")),
        () -> assertTrue(html.contains("Starting position")),
        () -> assertTrue(html.contains("Planning flexibility")),
        () -> assertTrue(html.contains("planningFlexibility.spending.conservativeLimit")),
        () -> assertTrue(html.contains("Plan risks")),
        () -> assertTrue(html.contains("planRisks.primaryRisks")),
        () -> assertTrue(html.contains("View all tested assumptions")),
        () -> assertTrue(html.contains("planRisks.planningLevers")),
        () -> assertFalse(html.contains("Plan sensitivity")),
        () -> assertTrue(html.contains("Spending flexibility")),
        () -> assertTrue(html.contains("planningFlexibility.retirement.conservative.earliest")),
        () -> assertFalse(html.contains("aria-label=\"Sustainable spending\"")),
        () -> assertFalse(html.contains("aria-label=\"Retirement timing\"")),
        () -> assertTrue(html.contains("Decision charts")),
        () -> assertTrue(html.contains("View yearly details")),
        () -> assertTrue(header.contains("Edit plan")),
        () -> assertFalse(html.contains("<summary>Diagnostics</summary>")),
        () -> assertFalse(html.contains("Advanced assumptions")),
        () -> assertFalse(html.contains("Saved plans")),
        () -> assertTrue(editor.contains("Saved plans")),
        () -> assertTrue(editor.contains("Life events")),
        () -> assertTrue(editor.contains("Funding &amp; reserve strategy")),
        () -> assertTrue(html.contains("simulation-scenario-tabs")),
        () -> assertTrue(html.contains("Plan vs reality")),
        () -> assertTrue(html.contains("Actual → needs review → current → projected")),
        () -> assertTrue(html.contains("aria-selected")),
        () -> assertTrue(html.contains("selectedSummary")),
        () -> assertFalse(html.contains("displaySummaries[selectedScenario]")),
        () -> assertTrue(html.contains("simulation-net-worth")));
  }

  @Test
  void developerPreviewIsConfigurableAndReadOnlyFactsAreNotPlanInputs() throws Exception {
    String editor =
        Files.readString(Path.of("src/main/resources/templates/simulation-plan-edit.html"));
    String config = Files.readString(Path.of("src/main/resources/application.yml"));
    assertAll(
        () -> assertTrue(config.contains("mode: ${DEVELOP_MODE:true}")),
        () -> assertTrue(editor.contains("currentRentalIncome")),
        () -> assertTrue(editor.contains("currentBondIncome")),
        () -> assertTrue(editor.contains("First projected-year costs")),
        () -> assertTrue(editor.contains("money(year.employmentIncome)")),
        () -> assertTrue(editor.contains("<td th:text=\"${year.year}\">")),
        () -> assertFalse(editor.contains("name=\"rentalIncome\"")),
        () -> assertFalse(editor.contains("name=\"bondIncome\"")));
  }
}
