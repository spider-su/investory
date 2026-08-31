package com.smartbox.investory.app;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SimulationTemplateContractTest {
  @Test
  void simulationTemplateKeepsProjectionPresentationServerBacked() throws Exception {
    String html =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/simulation.html"));
    assertAll(
        () -> assertTrue(html.contains("Expected year end")),
        () -> assertFalse(html.contains("Cash flow ·")),
        () -> assertTrue(html.contains("Cash flow / funding")),
        () -> assertTrue(html.contains("Income &amp; returns")),
        () -> assertTrue(html.contains("flow.target.toUpperCase()")),
        () -> assertTrue(html.contains("total economic sources")),
        () -> assertFalse(html.contains(">Capital <small>Expected year end</small>")),
        () -> assertFalse(html.contains("Funding sources")),
        () -> assertFalse(html.contains("Funded amount")),
        () -> assertTrue(html.contains("Funding coverage")),
        () -> assertTrue(html.contains("snapshot.fundingCoveragePercent()")),
        () -> assertTrue(html.contains("flow.sharePercent()")),
        () -> assertTrue(html.contains("Spending &amp; income")),
        () -> assertTrue(html.contains("Liquid capital")),
        () -> assertTrue(html.contains("data-simulation-chart-mode")),
        () -> assertTrue(html.contains("data-chart-mode=\"CASH_FLOW\"")),
        () -> assertTrue(html.contains("data-chart-mode=\"LIQUID_CAPITAL\"")),
        () -> assertTrue(html.contains("data-chart-panel=\"CASH_FLOW\"")),
        () -> assertTrue(html.contains("data-chart-panel=\"LIQUID_CAPITAL\"")),
        () -> assertTrue(html.contains("aria-selected=\"true\"")),
        () -> assertTrue(html.contains("aria-selected=\"false\"")),
        () ->
            assertTrue(
                html.contains(
                    "aria-selected=\"true\" aria-controls=\"liquid-capital-chart-panel\"")),
        () ->
            assertTrue(
                html.contains(
                    "data-chart-panel=\"CASH_FLOW\" aria-labelledby=\"simulation-chart-title\" hidden")),
        () -> assertTrue(html.contains("id=\"simulation-chart\"")),
        () -> assertTrue(html.contains("liquid-capital-chart")),
        () -> assertTrue(html.contains("iv-simulation-chart-layout")),
        () -> assertTrue(html.contains("retirementYear")),
        () -> assertTrue(html.contains("pensionStartYear")),
        () -> assertTrue(html.contains("simulationPage.chartData")),
        () -> assertTrue(html.contains("chart.js@4.4.1")),
        () -> assertTrue(html.contains("compactMoney(flow.amount)")),
        () -> assertTrue(html.contains("snapshot.incomeSources()")),
        () -> assertTrue(html.contains("timelineMoney[row.year].cashStart")),
        () -> assertFalse(html.contains("actualPortfolioWithdrawal")),
        () -> assertFalse(html.contains("manualLiquidReserveWithdrawal")),
        () -> assertFalse(html.contains("equityEnd")));
  }

  @Test
  void planningTimelineUsesCompactBucketSummaryColumns() throws Exception {
    String html =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/simulation.html"));
    assertAll(
        () -> assertTrue(html.contains("iv-simulation-projection-table")),
        () -> assertTrue(html.contains("iv-simulation-projection-row--historical")),
        () -> assertTrue(html.contains("iv-simulation-projection-row--current")),
        () -> assertTrue(html.contains("iv-simulation-projection-row--projected")),
        () -> assertTrue(html.contains("th:if=\"${summary.state == 'Actual'}\"")),
        () -> assertTrue(html.contains("th:unless=\"${summary.state == 'Actual'}\"")),
        () -> assertTrue(html.contains("title=\"View historical year review\"")),
        () -> assertTrue(html.contains("portfolioId=${simulationPage.profile.portfolioId}")),
        () -> assertTrue(html.contains("planId=${simulationPage.selectedPlanId}")),
        () ->
            assertTrue(html.contains("planningDisplayCurrency=${simulationPage.displayCurrency}")),
        () -> assertTrue(html.contains("selectedScenario=${simulationPage.selectedScenario}")),
        () -> assertTrue(html.contains("customInflationDelta")),
        () -> assertTrue(html.contains("customRentalGrowthDelta")),
        () -> assertTrue(html.contains("selectedScenario=CUSTOM")),
        () -> assertTrue(html.contains(">Reset</a>")),
        () -> assertTrue(html.contains("year=${summary.year}")),
        () -> assertTrue(html.contains("summary.state == 'Projected' ? summary.status : '—'")),
        () -> assertFalse(html.contains("year == 2025")),
        () -> assertFalse(html.contains("year == 2026")),
        () ->
            assertTrue(
                html.contains(
                    "<th class=\"text-start\">Year</th><th class=\"text-start\">State</th>")),
        () ->
            assertTrue(
                html.contains(
                    "<th class=\"text-end\">Spending</th><th class=\"text-end\">Income</th><th class=\"text-end\">Gap / surplus</th>")),
        () ->
            assertTrue(
                html.contains(
                    "<th class=\"text-end\">Cash</th><th class=\"text-end\">Bonds</th><th class=\"text-end\">Equities</th><th class=\"text-end\">Real estate</th><th class=\"text-start\">Status</th>")),
        () -> assertTrue(html.contains("yearlySummaries.values()")),
        () -> assertTrue(html.contains("compactMoney(summary.bonds.annualValue)")),
        () -> assertTrue(html.contains("compactMoney(summary.equities.annualValue)")),
        () -> assertTrue(html.contains("compactMoney(summary.realEstate.annualValue)")),
        () -> assertFalse(html.contains("<th>Annual costs</th>")),
        () -> assertFalse(html.contains("<th>Total income</th>")),
        () -> assertFalse(html.contains("<th>Funding gap</th>")),
        () -> assertFalse(html.contains("<th>Reserve withdrawal</th>")),
        () -> assertFalse(html.contains("<th>Long-Term funding</th>")),
        () -> assertFalse(html.contains("<th>Investment withdrawal</th>")),
        () -> assertFalse(html.contains("<th>Unfunded</th>")),
        () -> assertFalse(html.contains("<th>Reserve end</th>")),
        () -> assertFalse(html.contains("<th>Long-Term capital</th>")),
        () -> assertFalse(html.contains("<th>Investment end</th>")),
        () -> assertFalse(html.contains("<th>Funding need</th>")),
        () -> assertFalse(html.contains("<th>Portfolio withdrawal</th>")),
        () -> assertFalse(html.contains("<th>Cash reserve</th>")),
        () -> assertTrue(html.contains("Bonds</th>")),
        () -> assertTrue(html.contains("Equities</th>")),
        () -> assertTrue(html.contains("<th>Bucket</th>")),
        () -> assertTrue(html.contains("timelineMoney[row.year].bondReturn")));
  }

  @Test
  void simulationKeepsScenarioAndPlanNavigation() throws Exception {
    String html =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/simulation.html"));
    String editor =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/simulation-plan-edit.html"));
    String header =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/fragments/app-header.html"));
    int actionsStart = header.indexOf("iv-planning-actions--single");
    int secondaryStart = header.indexOf("iv-planning-topbar__secondary");
    assertAll(
        () -> assertTrue(html.contains("planningHeader('simulation'")),
        () -> assertFalse(html.contains("iv-simulation-scenario-tabs")),
        () -> assertFalse(html.contains("Edit assumptions")),
        () -> assertTrue(html.contains("iv-plan-timeline__assumptions")),
        () -> assertTrue(html.contains("format.percentage(row.effectiveRate)")),
        () -> assertTrue(html.contains("format.percentage(row.planRate)")),
        () -> assertTrue(html.contains("format.percentagePoints(row.deltaPercentagePoints)")),
        () -> assertTrue(html.contains("iv-plan-timeline__warning")),
        () -> assertFalse(html.contains("iv-simulation-failure")),
        () ->
            assertTrue(
                html.contains(
                    "<details class=\"card iv-simulation-section\" aria-labelledby=\"projection-title\">")),
        () -> assertTrue(html.contains("<summary id=\"projection-title\"")),
        () -> assertTrue(header.contains("Edit plan")),
        () -> assertEquals(1, occurrences(header, "iv-page-nav iv-planning-scenario-selector")),
        () -> assertTrue(header.contains("class=\"iv-planning-scenario-slot\"")),
        () ->
            assertTrue(
                header.contains(
                    "<nav class=\"iv-page-nav iv-planning-scenario-selector\" aria-label=\"Simulation scenario\">")),
        () ->
            assertFalse(
                header
                    .substring(actionsStart, secondaryStart)
                    .contains("iv-planning-scenario-selector")),
        () -> assertFalse(header.contains("role=\"button\">Base</a>")),
        () ->
            assertFalse(
                header.contains(
                    "<span class=\"iv-planning-scenario-selector__label\">Scenario</span>")),
        () -> assertTrue(header.contains("availableScenarios")),
        () -> assertTrue(header.contains("iv-plan-status--positive")),
        () -> assertTrue(header.contains("iv-plan-status--negative")),
        () -> assertTrue(header.contains("contextPlanId, contextScenario")),
        () -> assertTrue(header.contains("planId=${contextPlanId}")),
        () -> assertTrue(header.contains("selectedScenario=${contextScenario}")),
        () ->
            assertTrue(
                html.contains(
                    "${simulationPage.selectedPlanId}, ${simulationPage.selectedScenario}")),
        () -> assertFalse(header.contains("simulationPage.profile.portfolioId")),
        () -> assertTrue(html.contains("aria-selected")),
        () -> assertTrue(editor.contains("3. Income")),
        () -> assertTrue(editor.contains("4. Events")));
  }

  @Test
  void simulationKeepsOutcomeSectionsBeforeScenarioDetails() throws Exception {
    String html =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/simulation.html"));
    String css =
        Files.readString(Path.of("../adapters/web-ui/src/main/resources/static/css/main.css"));
    assertFalse(css.contains("aria-labelledby=\"plan-title\""));
    assertTrue(
        html.indexOf("aria-labelledby=\"plan-title\"")
            < html.indexOf("aria-labelledby=\"financial-outlook-title\""));
    assertTrue(
        html.indexOf("aria-labelledby=\"financial-outlook-title\"")
            < html.indexOf("aria-labelledby=\"projection-title\""));
    assertFalse(css.contains("grid-template-columns: minmax(0, .92fr) minmax(0, 1.08fr)"));
    assertFalse(css.contains("section[aria-labelledby=\"plan-title\"] { grid-column"));
    assertFalse(css.contains("section[aria-labelledby=\"financial-outlook-title\"] { grid-column"));
    assertFalse(css.contains("section[aria-labelledby=\"plan-title\"] { order"));
    assertFalse(html.contains("aria-labelledby=\"scenario-title\""));
    assertTrue(html.contains("aria-labelledby=\"simulation-details-title\""));
    assertTrue(html.contains("plan-cash-flow-title-' + snapshot.year"));
    assertTrue(html.contains("plan-capital-title-' + snapshot.year"));
    assertFalse(html.contains("id=\"plan-cash-flow-title\""));
    assertFalse(html.contains("id=\"plan-capital-title\""));
  }

  @Test
  void simulationChartPresentationKeepsSignedGapSemanticsWithoutObsoleteModeState()
      throws Exception {
    String javascript =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/static/js/retirement-simulation.js"));
    assertAll(
        () -> assertTrue(javascript.contains("Math.abs(Number(value))")),
        () -> assertTrue(javascript.contains("Funding gap")),
        () -> assertTrue(javascript.contains("Surplus")),
        () -> assertTrue(javascript.contains("semanticValues")),
        () -> assertFalse(javascript.contains("investory.simulation.chartMode")),
        () -> assertTrue(javascript.contains("retirementYear")),
        () -> assertTrue(javascript.contains("pensionStartYear")),
        () -> assertTrue(javascript.contains("selectMode('LIQUID_CAPITAL')")),
        () -> assertTrue(javascript.contains("button.dataset.chartMode")),
        () -> assertFalse(javascript.contains("Real Estate")),
        () -> assertFalse(javascript.contains("Cash")));
  }

  @Test
  void simulationHeaderPlacesBaseCurrencyAfterEditPlan() throws Exception {
    String header =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/fragments/app-header.html"));
    int actionsStart = header.indexOf("iv-planning-actions--single");
    int actionsEnd = header.indexOf("</div>", actionsStart);
    String simulationActions = header.substring(actionsStart, actionsEnd);

    assertTrue(
        simulationActions.indexOf(">Edit plan</a>")
            < simulationActions.indexOf("iv-planning-base"));
  }

  @Test
  void developerPreviewIsConfigurableAndReadOnlyFactsAreNotPlanInputs() throws Exception {
    String editor =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/simulation-plan-edit.html"));
    assertAll(
        () -> assertTrue(editor.contains("plannedRentalIncome")),
        () -> assertTrue(editor.contains("plannedBondIncome")),
        () -> assertFalse(editor.contains("name=\"rentalIncome\"")),
        () -> assertFalse(editor.contains("name=\"bondIncome\"")));
  }

  @Test
  void developerPreviewIsCancelledBeforePlanSave() throws Exception {
    String editor =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/simulation-plan-edit.html"));
    assertTrue(editor.contains("form.addEventListener('submit'"));
    assertTrue(editor.contains("form.addEventListener('formdata'"));
  }

  private static int occurrences(String value, String token) {
    int count = 0;
    for (int offset = 0; (offset = value.indexOf(token, offset)) >= 0; offset += token.length()) {
      count++;
    }
    return count;
  }
}
