package com.smartbox.investory.app;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Simulation Template Contract")
class SimulationTemplateContractTest {
  @DisplayName("simulation Template Keeps Projection Presentation Server Backed")
  @Test
  void simulationTemplateKeepsProjectionPresentationServerBacked() throws Exception {
    String html =
        HtmlTestSupport.readTemplateWithFragments(
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
        () -> assertTrue(html.contains("fragments/theme-head :: theme")),
        () -> assertFalse(html.contains("src=\"/js/simulation-page.js\"")),
        () -> assertFalse(html.contains("src=\"/js/retirement-simulation.js\"")),
        () -> assertTrue(html.contains("simulationPage.chartData")),
        () -> assertTrue(html.contains("compactMoney(flow.amount)")),
        () -> assertTrue(html.contains("snapshot.incomeSources()")),
        () -> assertTrue(html.contains("timelineMoney[row.year].cashStart")),
        () -> assertFalse(html.contains("actualPortfolioWithdrawal")),
        () -> assertFalse(html.contains("manualLiquidReserveWithdrawal")),
        () -> assertFalse(html.contains("equityEnd")));
  }

  @DisplayName("planning Timeline Uses Compact Bucket Summary Columns")
  @Test
  void planningTimelineUsesCompactBucketSummaryColumns() throws Exception {
    String html =
        HtmlTestSupport.readTemplateWithFragments(
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
        () -> assertFalse(html.contains("customInflationDelta")),
        () -> assertFalse(html.contains("selectedScenario=CUSTOM")),
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

  @DisplayName("sandbox exposes simple inputs and the matching chart/table outputs")
  @Test
  void sandboxTemplateContract() throws Exception {
    String html =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/simulation-sandbox.html"));
    String javascript =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/static/js/retirement-sandbox.js"));
    assertAll(
        () -> assertTrue(html.contains("/simulation/sandbox")),
        () -> assertTrue(html.contains("name=\"monthlyRentalIncome\"")),
        () -> assertTrue(html.contains("name=\"cash\"")),
        () -> assertTrue(html.contains("name=\"bonds\"")),
        () -> assertTrue(html.contains("name=\"equities\"")),
        () -> assertTrue(html.contains("OK — spending is funded")),
        () -> assertTrue(html.contains("sandbox-chart")),
        () -> assertTrue(html.contains("Projection starts at retirement age")),
        () -> assertTrue(html.contains("<th>Unfunded</th>")),
        () -> assertTrue(html.contains("Yearly values")),
        () -> assertTrue(javascript.contains("turbo:load")),
        () -> assertTrue(javascript.contains("sandboxChart?.destroy()")),
        () -> assertTrue(javascript.contains("label: 'Unfunded'")),
        () -> assertTrue(html.contains("name=\"portfolioId\"")),
        () -> assertTrue(html.contains("name=\"planId\"")));
  }

  @DisplayName("simulation Keeps Scenario And Plan Navigation")
  @Test
  void simulationKeepsScenarioAndPlanNavigation() throws Exception {
    String html =
        HtmlTestSupport.readTemplateWithFragments(
            Path.of("../adapters/web-ui/src/main/resources/templates/simulation.html"));
    String editor =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/simulation-plan-edit.html"));
    String script =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/static/js/simulation-plan-edit.js"));
    String header =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/fragments/app-header.html"));
    String css = CssTestSupport.readComposedStylesheet();
    int actionsStart = header.indexOf("iv-planning-actions--simulation");
    int secondaryStart = header.indexOf("iv-planning-topbar__secondary");
    assertAll(
        () -> assertTrue(html.contains("planningHeader('simulation'")),
        () -> assertFalse(html.contains("iv-simulation-scenario-tabs")),
        () -> assertFalse(html.contains("Edit assumptions")),
        () -> assertTrue(html.contains("iv-plan-timeline__assumptions")),
        () -> assertFalse(html.contains("iv-card-section-header__action")),
        () -> assertFalse(html.contains(">Sync years</button>")),
        () -> assertTrue(header.contains(">Sync years</button>")),
        () -> assertFalse(html.contains("Sync planning years")),
        () ->
            assertFalse(
                html.contains(
                    "<span class=\"text-secondary\" th:text=\"${simulationPage.displayCurrency}\">PLN</span>")),
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
        () -> assertTrue(header.contains("class=\"iv-planning-context-slot\"")),
        () ->
            assertTrue(
                header
                    .substring(actionsStart, secondaryStart)
                    .contains("iv-planning-actions__row")),
        () ->
            assertTrue(
                header
                    .substring(actionsStart, secondaryStart)
                    .contains("iv-planning-scenario-selector")),
        () -> assertTrue(header.substring(actionsStart, secondaryStart).contains(">Edit plan</a>")),
        () ->
            assertTrue(
                header.substring(actionsStart, secondaryStart).contains(">Sync years</button>")),
        () -> assertFalse(header.contains("role=\"button\">Base</a>")),
        () ->
            assertFalse(
                header.contains(
                    "<span class=\"iv-planning-scenario-selector__label\">Scenario</span>")),
        () -> assertTrue(header.contains("availableScenarios")),
        () -> assertTrue(css.contains(".iv-planning-actions--simulation {")),
        () ->
            assertTrue(
                css.contains(
                    ".iv-planning-actions--simulation > .iv-planning-scenario-selector {")),
        () -> assertTrue(css.contains("grid-template-columns: repeat(4, minmax(0, 1fr));")),
        () -> assertTrue(css.contains("@media (max-width: 1200px) and (min-width: 901px)")),
        () -> assertTrue(css.contains("grid-template-columns: repeat(2, minmax(0, 1fr));")),
        () -> assertTrue(css.contains("position: static;")),
        () -> assertTrue(header.contains("iv-plan-status--positive")),
        () -> assertTrue(header.contains("iv-plan-status--negative")),
        () -> assertTrue(header.contains("contextPlanId, contextScenario")),
        () -> assertTrue(header.contains("planId=${header.contextPlanId}")),
        () -> assertTrue(header.contains("selectedScenario=${header.contextScenario}")),
        () ->
            assertTrue(
                html.contains(
                    "${simulationPage.selectedPlanId}, ${simulationPage.selectedScenario}")),
        () -> assertFalse(header.contains("simulationPage.profile.portfolioId")),
        () -> assertTrue(html.contains("aria-selected")),
        () -> assertTrue(editor.contains("3. Income")),
        () -> assertTrue(editor.contains("4. Events")));
  }

  @DisplayName("simulation Keeps Outcome Sections Before Scenario Details")
  @Test
  void simulationKeepsOutcomeSectionsBeforeScenarioDetails() throws Exception {
    String html =
        HtmlTestSupport.readTemplateWithFragments(
            Path.of("../adapters/web-ui/src/main/resources/templates/simulation.html"));
    String css = CssTestSupport.readComposedStylesheet();
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

  @DisplayName(
      "simulation Chart Presentation Keeps Signed Gap Semantics Without Obsolete Mode State")
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

  @DisplayName("simulation Header Keeps Reporting Currency In The Shared Context Slot")
  @Test
  void simulationHeaderKeepsReportingCurrencyInTheSharedContextSlot() throws Exception {
    String header =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/fragments/app-header.html"));
    int actionsStart = header.indexOf("iv-planning-actions--simulation");
    int actionsEnd = header.indexOf("iv-planning-topbar__secondary", actionsStart);
    String simulationActions = header.substring(actionsStart, actionsEnd);
    int contextStart = header.indexOf("iv-planning-context-slot");
    int contextEnd = header.indexOf("</div>", contextStart);
    String planningContext = header.substring(contextStart, contextEnd);

    assertAll(
        () -> assertTrue(simulationActions.contains(">Edit plan</a>")),
        () -> assertTrue(simulationActions.contains(">Sync years</button>")),
        () -> assertTrue(simulationActions.contains("iv-planning-scenario-selector")),
        () -> assertFalse(simulationActions.contains("iv-planning-base")),
        () -> assertTrue(planningContext.contains("Reporting currency")),
        () -> assertFalse(planningContext.contains("iv-planning-scenario-selector")));
  }

  @DisplayName("assumptions And Capital Use Shared Structural Grids")
  @Test
  void assumptionsAndCapitalUseSharedStructuralGrids() throws Exception {
    String html =
        HtmlTestSupport.readTemplateWithFragments(
            Path.of("../adapters/web-ui/src/main/resources/templates/simulation.html"));
    String css = CssTestSupport.readComposedStylesheet();

    assertAll(
        () -> assertTrue(html.contains("iv-plan-timeline__assumption-row--header")),
        () -> assertTrue(html.contains("iv-plan-timeline__assumption-row--plan")),
        () -> assertTrue(html.contains("iv-plan-timeline__assumption-row--actual")),
        () -> assertTrue(css.contains("grid-template-columns: subgrid")),
        () -> assertTrue(css.contains("repeat(3, minmax(0, 1fr))")),
        () -> assertTrue(html.contains(">Cash</span>")),
        () -> assertTrue(html.contains(">Bonds</span>")),
        () -> assertTrue(html.contains(">Equities</span>")),
        () -> assertTrue(html.contains(">Real Estate</span>")),
        () -> assertTrue(html.contains("' / month · '")),
        () -> assertTrue(html.contains("+ ' total'")),
        () -> assertTrue(html.contains("outlook.minimumLiquidAssetsContext")));
  }

  @DisplayName("developer Preview Is Configurable And Read Only Facts Are Not Plan Inputs")
  @Test
  void developerPreviewIsConfigurableAndReadOnlyFactsAreNotPlanInputs() throws Exception {
    String editor =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/simulation-plan-edit.html"));
    assertAll(
        () -> assertTrue(editor.contains("plannedRentalIncome")),
        () -> assertTrue(editor.contains("plannedBondIncome")),
        () -> assertFalse(editor.contains("name=\"rentalIncome\"")),
        () -> assertFalse(editor.contains("name=\"bondIncome\"")),
        () -> assertFalse(editor.contains("Manual projected income")),
        () -> assertFalse(editor.contains("Manual planning value")),
        () -> assertFalse(editor.contains("id=\"manual-rental-income\"")),
        () -> assertFalse(editor.contains("id=\"manual-bond-cash-income\"")),
        () -> assertFalse(editor.contains("manualRentalIncome")),
        () -> assertFalse(editor.contains("manualBondCashIncome")));
  }

  @DisplayName("developer Preview Is Cancelled Before Plan Save")
  @Test
  void developerPreviewIsCancelledBeforePlanSave() throws Exception {
    String editor =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/simulation-plan-edit.html"));
    String script =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/static/js/simulation-plan-edit.js"));
    assertTrue(script.contains("form.addEventListener('submit'"));
    assertTrue(script.contains("form.addEventListener('formdata'"));
  }

  private static int occurrences(String value, String token) {
    int count = 0;
    for (int offset = 0; (offset = value.indexOf(token, offset)) >= 0; offset += token.length()) {
      count++;
    }
    return count;
  }
}
