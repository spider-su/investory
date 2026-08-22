package com.smartbox.investory.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RetirementSimulationTemplateContractTest {
  @Test
  void simulationOwnsRawProjectionAndNotAnalysisSections() throws Exception {
    String html =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/simulation.html"));

    assertTrue(html.contains("Scenario"));
    assertTrue(html.contains("Retirement outcome"));
    assertTrue(html.contains("Plan inputs"));
    assertTrue(html.contains("Planning horizon"));
    assertTrue(html.contains("Yearly projection"));
    assertTrue(html.contains("simulationPage"));
    assertFalse(html.contains("Scenario comparison"));
    assertFalse(html.contains("Planning flexibility"));
    assertFalse(html.contains("Plan risks"));
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
  void normalAssumptionsExposeInflationRelativeGrowthSpreadsButNotRecurringOneOffExpenses()
      throws Exception {
    String html =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/simulation.html"));
    String editor =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/simulation-plan-edit.html"));
    assertTrue(editor.contains("Rental growth vs inflation"));
    assertFalse(editor.contains("Real-estate return"));
    assertTrue(editor.contains("name=\"rentalIncomeGrowthSpread\""));
    assertTrue(editor.contains("Age at plan start"));
    assertTrue(editor.contains("Current planning age"));
    assertTrue(editor.contains("plannedRetirementYear"));
    assertTrue(editor.contains("existingPlan"));
    assertTrue(editor.contains("Monthly living costs"));
    assertTrue(editor.contains("Annual extras"));
    assertTrue(editor.contains("Cost growth vs inflation"));
    assertTrue(editor.contains("name=\"monthlyLivingCosts\""));
    assertFalse(editor.contains("Core living expenses / year"));
    assertFalse(editor.contains("Discretionary spending / year"));
    assertFalse(editor.contains("Spending growth"));
    assertTrue(editor.contains("name=\"spendingGrowthSpread\""));
    assertTrue(editor.contains("Funding order"));
    assertFalse(editor.contains("name=\"fundingStrategy\""));
    assertTrue(editor.contains("developMode"));
    assertTrue(editor.contains("Development preview"));
    assertTrue(editor.contains("/simulation/plans/preview"));
    assertTrue(editor.contains("Rental income / year"));
    assertTrue(editor.contains("Bond interest / year"));
    assertFalse(editor.contains("Rental income used by simulation"));
    assertFalse(editor.contains("temporary rental"));
    assertTrue(editor.contains("setTimeout"));
    assertTrue(editor.contains("AbortController"));
    assertTrue(
        editor.contains(
            "name=\"spendingGrowthSpread\" type=\"number\" step=\"0.1\" min=\"-99\" max=\"999\""));
    assertTrue(
        editor.contains(
            "name=\"rentalIncomeGrowthSpread\" type=\"number\" step=\"0.1\" min=\"-99\" max=\"999\""));
    assertTrue(editor.contains("Effective rental growth"));
    assertTrue(editor.contains("Effective cost growth"));
    assertTrue(editor.contains("Event income"));
    assertTrue(editor.contains("Event expense"));
    assertTrue(html.contains("for=\"planning-display-currency\">Currency"));
    assertFalse(html.contains("Planning currency"));
    assertTrue(html.contains("name=\"planningDisplayCurrency\""));
    assertTrue(editor.contains("moneyInput(displayMonthlyLivingCosts)"));
    assertTrue(editor.contains("percentageInput(assumptions.inflationRate)"));
    assertTrue(editor.contains("money(displayEventAmounts[event.id])"));
    assertTrue(editor.contains("displayEventAmounts"));
    assertFalse(editor.contains("/simulation/plans/save-as"));
    assertTrue(html.contains("Portfolio withdrawal"));
    assertTrue(html.contains("Minimum reserve coverage"));
    assertTrue(html.contains("simulationPage.startingPosition.marketPortfolioValueWholeDisplay"));
    assertTrue(html.contains("simulationPage.startingPosition.expectedLongTermAssetIncomeWholeDisplay"));
    assertFalse(html.contains("scenarioComparison"));
    assertFalse(html.contains("displaySummaries"));
    assertFalse(html.contains("displayYears"));
    assertFalse(html.contains("profile.currency"));
    assertFalse(html.contains("${results"));
    assertFalse(html.contains("${summaries"));
    assertFalse(html.contains("One-off expenses"));
  }

  @Test
  void planManagementAndDevelopmentPreviewsUseOneCollapsedPlanArea() throws Exception {
    String editor =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/simulation-plan-edit.html"));

    assertTrue(editor.contains("Active plan"));
    assertTrue(editor.contains("Not saved"));
    assertTrue(editor.contains("Saved plans"));
    assertTrue(editor.contains("Revision history ("));
    assertFalse(editor.contains("Plan revision"));
    assertTrue(editor.contains("<details class=\"iv-expandable-card\""));
    assertTrue(editor.contains("Development preview · temporal assumptions"));
    assertTrue(editor.contains("Development preview · income"));
    assertFalse(editor.contains("Investment · first projected year"));
    assertFalse(editor.contains("Portfolio assumptions"));
    assertTrue(editor.contains("Investment · current value"));
    assertTrue(editor.contains("Investment · equity return"));
    assertTrue(editor.contains("Investment · projected first-year return"));
    assertTrue(editor.contains("Current Investment fact · read-only"));
    assertTrue(editor.contains("<th class=\"text-end\">Investment interest</th>"));
    assertFalse(editor.contains("<th>State</th><th>Lifecycle</th>"));
    assertFalse(editor.contains("<th class=\"text-end\">Employment</th>"));
    assertFalse(editor.contains("<th class=\"text-end\">Event expense</th>"));
    assertFalse(editor.contains("<th class=\"text-end\">Contribution</th>"));
    assertTrue(editor.indexOf("for=\"inflation\"") < editor.indexOf("iv-card-section-header__title\">Spending"));
    assertTrue(editor.indexOf("Investment · current value") < editor.indexOf("Funding &amp; reserve strategy"));
    assertFalse(editor.contains("preview-income-investment-withdrawal"));
    assertFalse(editor.contains("preview-income-investment-end"));
    assertFalse(editor.contains("iv-simulation-editor__advanced"));
    assertTrue(editor.contains("name=\"startYear\""));
    assertTrue(editor.contains("name=\"annualEmploymentIncome\""));
    assertTrue(editor.contains("name=\"equityReturn\""));
  }
}
