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
        () -> assertTrue(html.contains("Current cash shows Now and Expected year end")),
        () -> assertTrue(html.contains("Cash flow ·")),
        () -> assertTrue(html.contains("Incoming cash")),
        () -> assertTrue(html.contains("Funding used")),
        () -> assertTrue(html.contains("compactMoney(flow.amount)")),
        () -> assertTrue(html.contains("flow.amount + ' ' + simulationPage.displayCurrency")),
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
        () -> assertTrue(html.contains("<th>Year</th><th>State</th><th>Spending</th><th>Income</th><th>Gap / surplus</th><th>Cash</th><th>Bonds</th><th>Equities</th><th>Real estate</th><th>Status</th>")),
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
        () -> assertTrue(html.contains("<th>Bonds</th>")),
        () -> assertTrue(html.contains("<th>Equities</th>")),
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
    assertAll(
        () -> assertTrue(html.contains("planningHeader('simulation'")),
        () -> assertTrue(html.contains("simulation-scenario-tabs")),
        () -> assertTrue(html.contains("Edit assumptions")),
        () -> assertTrue(html.contains("Yearly projection")),
        () -> assertTrue(header.contains("Edit plan")),
        () -> assertTrue(html.contains("aria-selected")),
        () -> assertTrue(editor.contains("3. Income &amp; assets")),
        () -> assertTrue(editor.contains("4. Events")));
  }

  @Test
  void simulationHeaderPlacesBaseCurrencyAfterEditPlan() throws Exception {
    String header =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/fragments/app-header.html"));
    int actionsStart = header.indexOf("iv-planning-actions--single");
    int actionsEnd = header.indexOf("</div>", actionsStart);
    String simulationActions = header.substring(actionsStart, actionsEnd);

    assertTrue(simulationActions.indexOf(">Edit plan</a>") < simulationActions.indexOf("iv-planning-base"));
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
    assertTrue(editor.contains("clearTimeout(timer)"));
    assertTrue(editor.contains("setTimeout(async()=>"));
  }
}
