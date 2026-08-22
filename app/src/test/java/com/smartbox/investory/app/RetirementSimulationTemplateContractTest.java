package com.smartbox.investory.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RetirementSimulationTemplateContractTest {
  @Test
  void simulationOwnsRawProjectionAndNotAnalysisSections() throws Exception {
    String html = Files.readString(Path.of("../adapters/web-ui/src/main/resources/templates/simulation.html"));
    assertTrue(html.contains("Scenario"));
    assertTrue(html.contains("Retirement outcome"));
    assertFalse(html.contains("Scenario comparison"));
  }

  @Test
  void planEditorSeparatesDecisionsFactsAndDevelopment() throws Exception {
    String editor = Files.readString(Path.of("../adapters/web-ui/src/main/resources/templates/simulation-plan-edit.html"));

    assertTrue(editor.contains("1. Timeline"));
    assertTrue(editor.contains("2. Spending"));
    assertTrue(editor.contains("3. Income &amp; assets"));
    assertTrue(editor.contains("4. Events"));
    assertTrue(editor.contains("5. Advanced"));
    assertTrue(editor.contains("Base for cost and rental growth."));
    assertTrue(editor.contains("Total annual spending"));
    assertTrue(editor.contains("Growth vs inflation"));
    assertTrue(editor.contains("Spending changes by age"));
    assertTrue(editor.contains("Current asset facts"));
    assertTrue(editor.contains("Current fact"));
    assertTrue(editor.contains("Investment value"));
    assertTrue(editor.contains("Investment return"));
    assertTrue(editor.contains("Assumption"));
    assertTrue(editor.contains("No future events."));
    assertTrue(editor.contains("+ Add event"));
    assertTrue(editor.contains("Funding policy"));
    assertTrue(editor.contains("Fixed by the planning model."));
    assertTrue(editor.contains("Development"));
    assertTrue(editor.contains("Timeline inputs"));
    assertTrue(editor.contains("Spending calculation"));
    assertTrue(editor.contains("Income projection"));
    assertTrue(editor.contains("Funding calculation"));

    assertFalse(editor.contains("Funding &amp; reserve strategy"));
    assertFalse(editor.contains("Investment · projected first-year return"));
    assertFalse(editor.contains("Development preview ·"));
    assertFalse(editor.contains("Saved plans</span>"));
  }

  @Test
  void planEditorPreservesPostBindingsAndPlanEventOperations() throws Exception {
    String editor = Files.readString(Path.of("../adapters/web-ui/src/main/resources/templates/simulation-plan-edit.html"));

    for (String name :
        new String[] {
          "name=\"name\"",
          "name=\"startYear\"",
          "name=\"ageAtPlanStart\"",
          "name=\"retirementAge\"",
          "name=\"endAge\"",
          "name=\"inflation\"",
          "name=\"monthlyLivingCosts\"",
          "name=\"discretionaryExpenses\"",
          "name=\"spendingGrowthSpread\"",
          "name=\"annualEmploymentIncome\"",
          "name=\"annualPreRetirementContribution\"",
          "name=\"annualPension\"",
          "name=\"pensionStartAge\"",
          "name=\"rentalIncomeGrowthSpread\"",
          "name=\"equityReturn\"",
          "name=\"expenseProfile\""
        }) assertTrue(editor.contains(name), name);
    assertTrue(editor.contains("name=\"saveAs\" value=\"true\""));
    assertTrue(editor.contains("name=\"saveAs\" value=\"false\""));
    assertTrue(editor.contains("/simulation/plans/{id}/delete"));
    assertTrue(editor.contains("/simulation/plans/{id}/events"));
    assertTrue(editor.contains("/simulation/plans/{id}/events/{eventId}/delete"));
    assertTrue(editor.contains("form=\"plan-editor-form\""));
  }
}
