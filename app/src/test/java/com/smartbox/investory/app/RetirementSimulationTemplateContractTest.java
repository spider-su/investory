package com.smartbox.investory.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RetirementSimulationTemplateContractTest {
  @Test
  void simulationOwnsRawProjectionAndNotAnalysisSections() throws Exception {
    String html = Files.readString(Path.of("../adapters/web-ui/src/main/resources/templates/simulation.html"));
    assertTrue(html.contains("Plan and actual scenario assumptions"));
    assertTrue(html.contains("iv-plan-timeline__matrix-label\">PLAN</strong>"));
    assertTrue(html.contains("iv-plan-timeline__matrix-label\">ACTUAL</strong>"));
    assertTrue(html.contains("iv-plan-timeline__matrix-value"));
    assertFalse(html.contains("iv-plan-timeline__assumption-delta"));
    assertTrue(html.contains("Yearly projection"));
    assertFalse(html.contains("Historical year review"));
    assertTrue(html.contains("View historical year review"));
    assertTrue(html.contains("/simulation/timeline/{year}"));
    assertFalse(html.contains("Scenario comparison"));
    assertFalse(html.contains("Core assumptions"));
    assertFalse(html.contains("Plan inputs"));
    assertFalse(html.contains("Planning buckets"));
    assertFalse(html.contains("Prefill historical years from Investory"));
    assertFalse(html.contains("Income shortfall"));
    assertTrue(html.contains("Capital funding"));
    assertTrue(html.contains("Details / Development"));
    assertTrue(html.contains("<details class=\"card iv-simulation-section\""));
    assertTrue(html.contains("Planning bucket projection"));
    assertTrue(html.contains("Spending required"));
    assertTrue(html.contains("snapshot.incomeUsed()"));
    assertTrue(html.contains("snapshot.unfunded()"));
    assertFalse(html.contains(".signum() &lt; 0"));
    assertTrue(html.contains("Annual spending"));
    assertEquals(0, occurrences(html, "aria-labelledby=\"scenario-title\""));
    assertTrue(html.contains("iv-plan-timeline__assumptions"));
    assertTrue(html.contains("iv-plan-timeline__warning"));
    assertFalse(html.contains("iv-simulation-failure"));
  }

  private static int occurrences(String value, String needle) {
    int count = 0;
    int offset = 0;
    while ((offset = value.indexOf(needle, offset)) >= 0) {
      count++;
      offset += needle.length();
    }
    return count;
  }

  @Test
  void planEditorSeparatesDecisionsFactsAndDevelopment() throws Exception {
    String editor = Files.readString(Path.of("../adapters/web-ui/src/main/resources/templates/simulation-plan-edit.html"));

    assertTrue(editor.contains("1. Timeline"));
    assertTrue(editor.contains("2. Spending &amp; Growth"));
    assertTrue(editor.contains("3. Income"));
    assertTrue(editor.contains("4. Events"));
    assertTrue(editor.contains("5. Reserve &amp; funding"));
    assertTrue(editor.contains("Total annual spending"));
    assertTrue(editor.contains("displayMonthlyTotalCosts"));
    assertFalse(editor.contains("T(java.math.BigDecimal)"));
    assertTrue(editor.contains("currentPlanningYear"));
    assertFalse(editor.contains("new Date()"));
    assertTrue(editor.contains("Spending growth vs inflation"));
    assertTrue(editor.contains("Plan start"));
    assertTrue(editor.contains("Plan start age"));
    assertTrue(editor.contains("Retirement age"));
    assertTrue(editor.contains("Plan exit age"));
    assertFalse(editor.contains("Age at plan start"));
    assertFalse(editor.contains("Planning through age"));
    assertFalse(editor.contains(">End age</label"));
    assertTrue(editor.contains("currentPlanningYear"));
    assertTrue(editor.contains("displayAnnualLivingCosts"));
    assertTrue(editor.contains("Rental growth vs inflation"));
    assertFalse(editor.contains("Global planning assumption"));
    assertFalse(editor.contains("Growth &amp; return assumptions"));
    assertTrue(editor.contains("Spending changes by age"));
    assertTrue(editor.contains("No future events."));
    assertTrue(editor.contains("+ Add event"));
  }

  @Test
  void planEditorKeepsRentalGrowthOutOfIncomeAssumptions() throws Exception {
    String editor = Files.readString(Path.of("../adapters/web-ui/src/main/resources/templates/simulation-plan-edit.html"));

    int incomeStart = editor.indexOf("3. Income");
    int rental = editor.indexOf("Rental growth vs inflation");
    assertTrue(rental >= 0 && rental < incomeStart);
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
          "name=\"fixedIncomeReturn\"",
          "name=\"equityReturn\"",
          "name=\"expenseProfile\""
        }) assertTrue(editor.contains(name), name);
    assertTrue(editor.contains("name=\"saveAs\" value=\"true\""));
    assertTrue(editor.contains("name=\"saveAs\" value=\"false\""));
    assertTrue(editor.contains("/simulation/plans/{id}/delete"));
    assertTrue(editor.contains("/simulation/plans/{id}/events"));
    assertTrue(editor.contains("/simulation/plans/{id}/events/{eventId}/delete"));
    assertTrue(editor.contains("form=\"plan-editor-form\""));
    assertTrue(editor.contains("data-expense-age"));
    assertTrue(editor.contains("ageAtPlanStart + step.fromYear"));
    assertFalse(editor.contains("Number(v[1])/100"));
    assertFalse(editor.contains("investment value × return"));
    assertFalse(editor.contains("investmentValue *"));
    assertFalse(editor.contains("rentalIncome *"));
    assertFalse(editor.contains("equity-return\" class=\"form-control\" form=\"plan-editor-form\" name=\"equityReturn\" type=\"number\" step=\"0.1\" min=\"1\""));
    int bond = editor.indexOf("for=\"fixed-income-return\"");
    int equity = editor.indexOf("for=\"equity-return\"");
    assertTrue(bond >= 0 && bond < equity);
    assertTrue(editor.substring(editor.indexOf("Asset return assumptions"), equity)
        .contains("iv-simulation-editor__grid"));
  }
}
