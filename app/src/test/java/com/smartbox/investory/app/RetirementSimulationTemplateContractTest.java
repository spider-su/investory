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
    assertTrue(html.contains("Scenario"));
    assertTrue(html.contains("Yearly projection"));
    assertTrue(html.contains("Historical year review"));
    assertTrue(html.contains("Needs review"));
    assertTrue(html.contains("View review"));
    assertTrue(html.contains("/simulation/timeline/past/{year}"));
    assertFalse(html.contains("Scenario comparison"));
    assertFalse(html.contains("Scenario assumptions"));
    assertFalse(html.contains("Core assumptions"));
    assertFalse(html.contains("Plan inputs"));
    assertFalse(html.contains("Planning buckets"));
    assertTrue(html.contains("Annual costs"));
    assertTrue(html.contains("simulationPage.annualCosts"));
    assertTrue(html.contains("simulationPage.chartData.pensionStartYear"));
    assertTrue(html.contains("Cash</span><b>→</b><span>Bonds</span><b>→</b><span>Equities</span><b>→</b><span>Real Estate"));
    assertEquals(1, occurrences(html, "aria-labelledby=\"scenario-title\""));
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
    assertTrue(editor.contains("<th>State</th><th class=\"text-end\">Total spending</th><th class=\"text-end\">Event spending</th><th class=\"text-end\">Growth</th>"));
    assertTrue(editor.contains("<th>State</th><th class=\"text-end\">Income total</th><th class=\"text-end\">Rents</th><th class=\"text-end\">Bonds</th><th class=\"text-end\">Equities</th><th class=\"text-end\">Salary</th><th class=\"text-end\">Pension</th><th class=\"text-end\">Events</th>"));
    assertTrue(editor.contains("compactMoney(year.totalCosts)"));
    assertTrue(editor.contains("compactMoney(year.totalIncome)"));
    assertFalse(editor.contains("<th class=\"text-end\">Recurring costs</th>"));
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
    assertTrue(editor.contains("data-expense-age"));
    assertTrue(editor.contains("Calendar year"));
    assertTrue(editor.contains("year+age-planAge"));
    assertTrue(editor.contains("ageAtPlanStart + step.fromYear"));
    assertTrue(editor.contains("planPreview.ageAtPlanStart + step.fromYear"));
    assertTrue(editor.contains("planPreview.planStartYear + step.fromYear"));
    assertTrue(editor.contains("Stage age cannot be before plan-start age"));
    assertFalse(editor.contains("Number(v[1])/100"));
    assertFalse(editor.contains("investment value × return"));
    assertFalse(editor.contains("investmentValue *"));
    assertFalse(editor.contains("rentalIncome *"));
    assertFalse(editor.contains("equity-return\" class=\"form-control\" form=\"plan-editor-form\" name=\"equityReturn\" type=\"number\" step=\"0.1\" min=\"1\""));
  }
}
