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
        () -> assertTrue(html.contains("Authoritative simulation output")),
        () -> assertTrue(html.contains("timelineMoney[row.year].fundingGap")),
        () -> assertFalse(html.contains("actualPortfolioWithdrawal")),
        () -> assertFalse(html.contains("manualLiquidReserveWithdrawal")),
        () -> assertFalse(html.contains("equityEnd")));
  }

  @Test
  void planningTimelineUsesExplicitCanonicalFundingColumns() throws Exception {
    String html =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/simulation.html"));
    assertAll(
        () -> assertTrue(html.contains("<th>Annual costs</th>")),
        () -> assertTrue(html.contains("<th>Total income</th>")),
        () -> assertTrue(html.contains("<th>Funding gap</th>")),
        () -> assertTrue(html.contains("<th>Reserve withdrawal</th>")),
        () -> assertTrue(html.contains("<th>Long-Term funding</th>")),
        () -> assertTrue(html.contains("<th>Investment withdrawal</th>")),
        () -> assertTrue(html.contains("<th>Unfunded</th>")),
        () -> assertTrue(html.contains("<th>Reserve end</th>")),
        () -> assertTrue(html.contains("<th>Long-Term capital</th>")),
        () -> assertTrue(html.contains("<th>Investment end</th>")),
        () -> assertFalse(html.contains("<th>Funding need</th>")),
        () -> assertFalse(html.contains("<th>Portfolio withdrawal</th>")),
        () -> assertFalse(html.contains("<th>Cash reserve</th>")),
        () -> assertFalse(html.contains("<th>Bonds</th>")),
        () -> assertFalse(html.contains("<th>Equity</th>")),
        () -> assertTrue(html.contains("'Failed'")),
        () -> assertTrue(html.contains("timelineMoney[row.year].totalIncome")),
        () -> assertTrue(html.contains("timelineMoney[row.year].longTermFunding")));
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
        () -> assertTrue(editor.contains("Current asset facts")),
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
        () -> assertTrue(editor.contains("currentRentalIncome")),
        () -> assertTrue(editor.contains("currentBondIncome")),
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
