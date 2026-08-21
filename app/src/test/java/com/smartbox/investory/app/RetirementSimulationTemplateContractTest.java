package com.smartbox.investory.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RetirementSimulationTemplateContractTest {
  @Test
  void planProgressIsCompactAndPlacedBeforeFlexibilityAndRisks() throws Exception {
    String html =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/simulation.html"));

    assertTrue(html.contains("planProgressView"));
    assertTrue(html.contains("Not available yet"));
    assertTrue(html.contains("Latest boundary"));
    assertTrue(html.indexOf("Scenario comparison") < html.indexOf("Plan progress"));
    assertTrue(html.indexOf("Plan progress") < html.indexOf("Planning flexibility"));
    assertTrue(html.indexOf("Planning flexibility") < html.indexOf("Plan risks"));
  }

  @Test
  void closedPlanningYearShowsAnHonestUnavailableProgressState() throws Exception {
    String html =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/planning-year.html"));

    assertTrue(html.contains("Year in review"));
    assertTrue(html.contains("Plan progress unavailable"));
    assertTrue(html.contains("frozen baseline net worth is missing"));
    assertTrue(html.contains("Other changes"));
    assertTrue(html.contains("Net impact"));
    assertTrue(html.contains("View reconciliation →"));
  }

  @Test
  void normalAssumptionsExposeIndependentGrowthRatesButNotRecurringOneOffExpenses()
      throws Exception {
    String html =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/simulation.html"));
    String editor =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/simulation-plan-edit.html"));
    assertTrue(editor.contains("Rental income growth"));
    assertFalse(editor.contains("Real-estate return"));
    assertTrue(editor.contains("name=\"rentalIncomeGrowth\""));
    assertTrue(editor.contains("Age at plan start"));
    assertTrue(editor.contains("Current planning age"));
    assertTrue(editor.contains("plannedRetirementYear"));
    assertTrue(editor.contains("existingPlan"));
    assertTrue(editor.contains("Monthly living costs"));
    assertTrue(editor.contains("Annual extras"));
    assertTrue(editor.contains("Cost growth"));
    assertTrue(editor.contains("name=\"monthlyLivingCosts\""));
    assertFalse(editor.contains("Core living expenses / year"));
    assertFalse(editor.contains("Discretionary spending / year"));
    assertFalse(editor.contains("Spending growth"));
    assertTrue(editor.contains("name=\"spendingGrowth\""));
    assertTrue(editor.contains("Funding order"));
    assertFalse(editor.contains("name=\"fundingStrategy\""));
    assertTrue(editor.contains("Safe-reserve target"));
    assertTrue(editor.contains("recurring portfolio funding need"));
    assertTrue(editor.contains("Target reserve amount"));
    assertTrue(editor.contains("Recurring portfolio need"));
    assertTrue(editor.contains("Market fixed income"));
    assertTrue(editor.contains("firstProjectedYear.fixedIncomeStart"));
    assertTrue(editor.contains("firstProjectedYear.fixedIncomeEnd"));
    assertFalse(editor.contains("Bonds / fixed income"));
    assertTrue(editor.contains("developMode"));
    assertTrue(editor.contains("Development preview"));
    assertTrue(editor.contains("/simulation/plans/preview"));
    assertTrue(editor.contains("Rental income / year"));
    assertTrue(editor.contains("Bond interest / year"));
    assertFalse(editor.contains("Rental income used by simulation"));
    assertFalse(editor.contains("temporary rental"));
    assertTrue(editor.contains("setTimeout"));
    assertTrue(editor.contains("AbortController"));
    assertTrue(editor.contains("name=\"equityGainHarvest\""));
    assertTrue(
        editor.contains(
            "name=\"spendingGrowth\" type=\"number\" step=\"0.1\" min=\"1\" max=\"99\""));
    assertTrue(
        editor.contains(
            "name=\"rentalIncomeGrowth\" type=\"number\" step=\"0.1\" min=\"1\" max=\"99\""));
    assertTrue(html.contains("for=\"planning-display-currency\">Currency"));
    assertFalse(html.contains("Planning currency"));
    assertTrue(html.contains("name=\"planningDisplayCurrency\""));
    assertTrue(editor.contains("moneyInput(displayMonthlyLivingCosts)"));
    assertTrue(editor.contains("percentageInput(assumptions.inflationRate)"));
    assertTrue(editor.contains("money(displayEventAmounts[event.id])"));
    assertTrue(editor.contains("displayEventAmounts"));
    assertFalse(editor.contains("/simulation/plans/save-as"));
    assertTrue(html.contains("Plan vs reality"));
    assertTrue(html.contains("Scenario comparison"));
    assertTrue(html.contains("Extra capacity"));
    assertTrue(html.contains("Over limit"));
    assertFalse(html.contains(">Headroom<"));
    assertTrue(html.contains("Portfolio withdrawal"));
    assertTrue(html.contains("scenarioComparison.interpretation"));
    assertTrue(html.contains("Minimum reserve coverage"));
    assertTrue(html.contains("Create 2025 snapshot"));
    assertTrue(html.contains("Update current-year values"));
    assertTrue(html.contains("Set baseline"));
    assertTrue(html.contains("displayProfile.marketPortfolioValueWholeDisplay"));
    assertTrue(html.contains("displayProfile.expectedLongTermAssetIncomeWholeDisplay"));
    assertTrue(html.contains("scenarioComparison"));
    assertFalse(html.contains("displaySummaries"));
    assertFalse(html.contains("displayYears"));
    assertFalse(html.contains("profile.currency"));
    assertFalse(html.contains("${results"));
    assertFalse(html.contains("${summaries"));
    assertFalse(html.contains("One-off expenses"));
  }
}
