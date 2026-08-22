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
    assertTrue(editor.contains("Projected plan value"));
    assertTrue(editor.contains("Investment profit"));
    assertTrue(editor.contains("Planned annual income"));
    assertTrue(editor.contains("Investment return"));
    assertTrue(editor.contains("Assumption"));
    assertTrue(editor.contains("No future events."));
    assertTrue(editor.contains("+ Add event"));
    assertTrue(editor.contains("Funding policy"));
    assertTrue(editor.contains("Fixed by the planning model."));
    assertTrue(editor.contains("Development"));
    assertTrue(editor.contains("<summary>Timeline</summary>"));
    assertTrue(editor.contains("<summary>Spending</summary>"));
    assertTrue(editor.contains("<summary>Income</summary>"));
    assertTrue(editor.contains("<summary>Funding &amp; balances</summary>"));
    assertTrue(editor.contains("Reserve transfer"));
    assertTrue(editor.contains("Investment withdrawal"));
    assertTrue(editor.contains("year.investmentReturn"));
    assertTrue(editor.contains("year.rentalIncome == null ? '—'"));
    assertTrue(editor.contains("developMode != null and developMode and planPreview != null"));

    assertFalse(editor.contains("Funding &amp; reserve strategy"));
    assertFalse(editor.contains("Investment · projected first-year return"));
    assertFalse(editor.contains("Development preview ·"));
    assertFalse(editor.contains("Timeline inputs"));
    assertFalse(editor.contains("Funding calculation"));
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
    assertTrue(editor.contains("data-plan-warning=\"inflation\""));
    assertTrue(editor.contains("data-plan-warning=\"equityReturn\""));
    assertTrue(editor.contains("map(v=>v[0]+':'+v[1])"));
    assertFalse(editor.contains("Number(v[1])/100"));
    assertFalse(editor.contains("investment value × return"));
    assertFalse(editor.contains("investmentValue *"));
    assertFalse(editor.contains("rentalIncome *"));
    assertFalse(editor.contains("equity-return\" class=\"form-control\" form=\"plan-editor-form\" name=\"equityReturn\" type=\"number\" step=\"0.1\" min=\"1\""));
  }
}
