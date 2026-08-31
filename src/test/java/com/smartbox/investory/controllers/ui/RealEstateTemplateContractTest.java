package com.smartbox.investory.controllers.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RealEstateTemplateContractTest {
  private static final String TEMPLATE = "src/main/resources/templates/real-estate-detail.html";

  @Test
  void realEstatePageKeepsOwnerMetricsAndHidesGenericAdministration() throws Exception {
    String html = Files.readString(Path.of(TEMPLATE));
    assertAll(
        () -> assertTrue(html.contains("Total payment")),
        () -> assertTrue(html.contains("Monthly income")),
        () -> assertTrue(html.contains("Monthly reduce")),
        () -> assertTrue(html.contains("Income yield")),
        () -> assertTrue(html.contains("Annual tax")),
        () -> assertTrue(html.contains("Rental economics")),
        () -> assertTrue(html.contains("Advanced planning")),
        () -> assertFalse(html.contains("name=\"acquisitionValue\"")),
        () -> assertTrue(html.contains("<label class=\"form-label\">Type</label>")),
        () -> assertFalse(html.contains("Net before tax")),
        () -> assertFalse(html.contains("<h2 class=\"card-title\">Planning periods")));
  }

  @Test
  void recurringLabelsUsePlanningPresentation() throws Exception {
    String html = Files.readString(Path.of(TEMPLATE));
    assertTrue(html.contains("PlanningPresentation).cashFlowType(flow.type)"));
    assertTrue(html.contains("PlanningPresentation).cashFlowType(type)"));
  }

  @Test
  void longTermAssetListPlacesCreationActionsInEverySection() throws Exception {
    String html = Files.readString(Path.of("src/main/resources/templates/long-term-assets.html"));
    assertAll(
        () -> assertFalse(html.contains("iv-planning-intro")),
        () -> assertFalse(html.contains("iv-planning-income-summary")),
        () -> assertTrue(html.contains("long-term-structure-title")),
        () -> assertTrue(html.contains("Asset allocation")),
        () -> assertTrue(html.contains("Asset currency")),
        () -> assertTrue(html.contains("longTermLargestClass")),
        () -> assertTrue(html.contains("format.wholeNumber(group.totalValue)")),
        () -> assertTrue(html.contains("format.wholeNumber(total.totalCurrentValue)")),
        () ->
            assertTrue(
                html.contains(
                    "th:style=\"|width:${group.shareWidth(total.totalCurrentValue)}%|\"")),
        () -> assertFalse(html.contains("group.title}'>0.0% · 0</span></div></div>")),
        () -> assertFalse(html.contains("th:text=\"${total.totalCurrentValue}\"")),
        () -> assertFalse(html.contains("<details class=\"iv-structure-card\" open>")),
        () ->
            assertFalse(
                html.contains(
                    "<details class=\"iv-structure-card iv-structure-card--allocation\" open>")),
        () -> assertFalse(html.contains("<details class=\"iv-structure-currency\" open>")),
        () -> assertTrue(html.contains("group.netMonthlyIncome()")),
        () -> assertFalse(html.contains("th:open=")),
        () -> assertTrue(html.contains("iv-collapsed-summary")),
        () -> assertTrue(html.contains("Total value")),
        () -> assertTrue(html.contains("Monthly income")),
        () -> assertTrue(html.contains("Monthly rent tax")),
        () -> assertTrue(html.contains("group.realEstatePlanning.monthlyRentTax")),
        () -> assertTrue(html.contains("Income yield")),
        () -> assertTrue(html.contains("Annual gross income")),
        () -> assertTrue(html.contains("Total Payment")),
        () -> assertTrue(html.contains("Monthly Reduce")),
        () -> assertTrue(html.contains("Rent End")),
        () -> assertTrue(html.contains("asset.rentEnd == null ? '—'")),
        () -> assertTrue(html.contains("asset.realEstatePlanning.netMonthlyIncome")),
        () -> assertTrue(html.contains("group.annualEconomics.grossAnnualIncome")),
        () -> assertTrue(html.contains("asset.annualEconomics.netAnnualIncomeAfterTax")),
        () -> assertTrue(html.contains("format.wholeNumber(asset.realEstatePlanning.taxBase)")),
        () -> assertFalse(html.contains("wholeNumberInput(asset.realEstatePlanning.taxBase)")),
        () -> assertFalse(html.contains("<details class=\"d-inline-block text-end\">")),
        () -> assertFalse(html.contains(">Edit</span>")),
        () -> assertFalse(html.contains("tax-base-form")),
        () ->
            assertTrue(
                html.contains(
                    "th:each=\"group : ${groups}\" class=\"iv-planning-section iv-card card\"")),
        () -> assertFalse(html.contains("Annual income")),
        () -> assertTrue(html.contains("of long-term assets")),
        () -> assertTrue(html.contains("No ' + group.title.toLowerCase() + '.'")));
  }

  @Test
  void rentalEditUsesMappedEndDateAndFormSafeNumberFormatting() throws Exception {
    String html = Files.readString(Path.of(TEMPLATE));
    assertAll(
        () -> assertTrue(html.contains("name=\"effectiveFrom\"")),
        () -> assertTrue(html.contains("name=\"endDate\"")),
        () -> assertTrue(html.contains("moneyInput(flow.amount)")),
        () -> assertTrue(html.contains("moneyInput(asset.currentValue)")),
        () -> assertTrue(html.contains("wholeNumberInput(asset.taxBase)")),
        () -> assertTrue(html.contains("percentageInput(expectedPropertyGrowth)")),
        () -> assertTrue(html.contains("<th>End date</th>")),
        () -> assertTrue(html.contains("<th>Effective from</th>")),
        () -> assertTrue(html.contains("name=\"validTo\"")),
        () -> assertTrue(html.contains("name=\"validFrom\"")),
        () -> assertTrue(html.contains("All economics types added")));
  }

  @Test
  void realEstateGrowthFormsUsePercentagePointsAtTheUiBoundary() throws Exception {
    String create = Files.readString(Path.of("src/main/resources/templates/real-estate-form.html"));
    String detail =
        Files.readString(Path.of("src/main/resources/templates/long-term-asset-detail.html"));
    assertAll(
        () -> assertTrue(create.contains("name=\"expectedAnnualGrowthRatePercent\"")),
        () -> assertFalse(create.contains("name=\"expectedAnnualGrowthRate\"")),
        () -> assertTrue(detail.contains("name=\"expectedAnnualGrowthRatePercent\"")),
        () -> assertFalse(detail.contains("name=\"expectedAnnualGrowthRate\"")),
        () ->
            assertTrue(
                detail.contains("PlanningPresentation).percentage(p.expectedAnnualGrowthRate)")));
  }

  @Test
  void longTermHeaderUsesSharedNavigationAndOnlyLongTermActions() throws Exception {
    String fragment =
        Files.readString(Path.of("src/main/resources/templates/fragments/app-header.html"));
    assertAll(
        () -> assertTrue(fragment.contains("appNavigation(${activePage}, ${portfolioId})")),
        () -> assertTrue(fragment.contains("</svg>Rental</a>")),
        () -> assertTrue(fragment.contains("</svg>Bond</a>")),
        () -> assertTrue(fragment.contains("</svg>Cash</a>")),
        () -> assertTrue(fragment.contains("th:text=\"${'Base: ' + currency}\">Base: PLN</span>")),
        () -> assertFalse(fragment.contains("Import")),
        () -> assertFalse(fragment.contains("Update History")));
  }

  @Test
  void longTermPageHasThreeCompactAssetSummaryCards() throws Exception {
    String html = Files.readString(Path.of("src/main/resources/templates/long-term-assets.html"));
    assertAll(
        () -> assertTrue(html.contains("iv-grid--long-term-overview")),
        () ->
            assertTrue(
                html.contains(
                    "th:classappend=\"| iv-asset-summary__icon--${group.key.toLowerCase().replace('_', '-')}|\"")),
        () -> assertTrue(html.contains("group.key == 'REAL_ESTATE'")),
        () -> assertTrue(html.contains("group.key == 'BOND'")),
        () -> assertTrue(html.contains("Share of long-term assets")),
        () -> assertTrue(html.contains("Annual gross income")),
        () -> assertTrue(html.contains("Gross yield")),
        () -> assertFalse(html.contains("iv-planning-section__summary")),
        () ->
            assertTrue(
                html.contains(
                    "th:classappend=\"| iv-planning-section__header--${group.key.toLowerCase()}|\"")),
        () -> assertFalse(html.contains("group.key.toLowerCase() + (group.key == 'OTHER'")),
        () -> assertFalse(html.contains("Add rental property")),
        () -> assertFalse(html.contains(">+ Rental</a>")),
        () -> assertFalse(html.contains(">+ Bond</a>")),
        () -> assertFalse(html.contains(">+ Cash</a>")),
        () -> assertTrue(html.contains(">+ Asset</a>")),
        () -> assertTrue(html.contains("#real-estate")),
        () -> assertTrue(html.contains("#bonds")),
        () -> assertTrue(html.contains("#cash-reserves")),
        () -> assertTrue(html.contains("Monthly income")),
        () -> assertTrue(html.contains("Gross yield")),
        () -> assertFalse(html.contains("#other-assets\">Details")));
  }
}
