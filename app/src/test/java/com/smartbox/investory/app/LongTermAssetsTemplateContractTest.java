package com.smartbox.investory.app;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LongTermAssetsTemplateContractTest {
  private static final Path TEMPLATE =
      Path.of("../adapters/web-ui/src/main/resources/templates/long-term-assets.html");
  private static final Path HEADER =
      Path.of("../adapters/web-ui/src/main/resources/templates/fragments/app-header.html");
  private static final Path GENERIC_DETAIL =
      Path.of("../adapters/web-ui/src/main/resources/templates/long-term-asset-detail.html");
  private static final Path REAL_ESTATE_DETAIL =
      Path.of("../adapters/web-ui/src/main/resources/templates/real-estate-detail.html");
  private static final Path BOND_DETAIL =
      Path.of("../adapters/web-ui/src/main/resources/templates/bond-detail.html");
  private static final Path CASH_DETAIL =
      Path.of("../adapters/web-ui/src/main/resources/templates/cash-reserve-detail.html");
  private static final Path CSS =
      Path.of("../adapters/web-ui/src/main/resources/static/css/main.css");

  @Test
  void realEstateGroupUsesPublicViewProperties() throws Exception {
    String html = Files.readString(TEMPLATE);

    assertAll(
        () -> assertTrue(html.contains("group.realEstatePlanning.netMonthlyIncome")),
        () -> assertTrue(html.contains("group.realEstatePlanning.totalPaymentMonthly")),
        () -> assertTrue(html.contains("group.realEstatePlanning.monthlyTax")),
        () -> assertTrue(html.contains("group.realEstatePlanning.monthlyReduce")),
        () -> assertTrue(html.contains("group.realEstatePlanning.taxBase")),
        () -> assertTrue(html.contains("group.realEstatePlanning.netYield")),
        () -> assertTrue(html.contains("asset.realEstatePlanning.taxBase")),
        () -> assertTrue(html.contains("asset.realEstatePlanning.incomeYield")),
        () -> assertFalse(html.contains("group.netMonthlyIncome()")),
        () -> assertFalse(html.contains("group.totalPaymentMonthly()")),
        () -> assertFalse(html.contains("group.shareDisplay(")),
        () -> assertFalse(html.contains("group.shareWidth(")),
        () -> assertFalse(html.contains("monthlyRentTax")));
  }

  @Test
  void summaryPageKeepsOneClearCreationAndPortfolioStructureContract() throws Exception {
    String html = Files.readString(TEMPLATE);
    String header = Files.readString(HEADER);
    String css = Files.readString(CSS);
    String assetActions =
        header.substring(
            header.indexOf("aria-label=\"Long-term asset actions\""),
            header.indexOf("aria-label=\"Simulation actions\""));

    assertAll(
        () -> assertFalse(html.contains("Show archived assets")),
        () -> assertFalse(html.contains("showArchived")),
        () -> assertFalse(html.contains("Archived assets")),
        () -> assertFalse(html.contains("archivedAssets")),
        () -> assertTrue(header.contains("appNavigation(${activePage}, ${portfolioId})")),
        () -> assertTrue(header.contains("iv-planning-topbar--assets")),
        () -> assertEquals(4, occurrences(assetActions, "aria-label=\"Create ")),
        () -> assertTrue(assetActions.contains("</svg>Rental</a>")),
        () -> assertTrue(assetActions.contains("</svg>Bond</a>")),
        () -> assertTrue(assetActions.contains("</svg>Cash</a>")),
        () -> assertTrue(assetActions.contains("</svg>Other asset</a>")),
        () ->
            assertTrue(
                assetActions.contains("@{/long-term-assets/new(portfolioId=${portfolioId})}")),
        () -> assertFalse(assetActions.contains("iv-planning-base")),
        () -> assertFalse(assetActions.contains("th:text=\"${currency}\"")),
        () -> assertFalse(html.contains("iv-planning-section__header-action")),
        () -> assertFalse(html.contains("/long-term-assets/new/deposit")),
        () -> assertFalse(html.contains("<summary><span>Income yield</span>")),
        () -> assertFalse(html.contains("<summary><span>Annual net income</span>")),
        () -> assertTrue(html.contains("iv-portfolio-structure__grid--long-term")),
        () -> assertTrue(css.contains(".iv-portfolio-structure__grid--long-term")),
        () -> assertTrue(css.contains("grid-template-columns: minmax(0, 1fr)")),
        () -> assertTrue(html.contains("iv-long-term-allocation__legend")),
        () -> assertTrue(html.contains("format.compactMoney(group.totalValue)")),
        () -> assertTrue(html.contains("format.compactMoney(group.realEstatePlanning.netMonthlyIncome)")),
        () -> assertTrue(html.contains("format.compactMoney(group.annualEconomics.netAnnualIncomeAfterTax)")),
        () -> assertTrue(html.contains("th:text=\"${groupShares[group.key]}\"")),
        () ->
            assertTrue(
                html.contains("th:if=\"${group.totalValue != null and group.totalValue > 0}\"")),
        () ->
            assertTrue(
                css.contains("iv-portfolio-structure--long-term .iv-structure-bar__segment")),
        () -> assertTrue(header.contains("Reporting currency")),
        () -> assertFalse(html.contains("Asset currency")),
        () -> assertFalse(html.contains("Long-term asset currency")),
        () -> assertFalse(html.contains("iv-structure-currency--reporting")),
        () -> assertFalse(html.contains("iv-grid--long-term-overview")),
        () -> assertTrue(html.contains("Expected annual net income")),
        () -> assertFalse(html.contains("Expected monthly net income")),
        () -> assertTrue(html.contains("'Net yield', ${longTermHeaderYield}")),
        () ->
            assertTrue(css.contains(".iv-planning-topbar--assets .iv-planning-topbar__secondary")),
        () ->
            assertTrue(
                html.contains(
                    "class=\"iv-collapsed-summary\" th:if=\"${!#lists.isEmpty(group.assets)}\"")),
        () -> assertTrue(html.contains(">Net yield</span>")),
        () -> assertTrue(html.contains("? 'Monthly rent tax' : 'Annual tax'")),
        () -> assertTrue(css.contains("grid-template-columns: repeat(4, minmax(0, 1fr))")),
        () -> assertTrue(css.contains("grid-auto-rows: auto")));
  }

  @Test
  void assetTypeIsReadOnlyAndEffectiveDatedRecordsHaveCorrectionActions() throws Exception {
    String generic = Files.readString(GENERIC_DETAIL);
    String summary = Files.readString(TEMPLATE);
    String realEstate = Files.readString(REAL_ESTATE_DETAIL);
    String bond = Files.readString(BOND_DETAIL);
    String cash = Files.readString(CASH_DETAIL);

    assertAll(
        () -> assertFalse(generic.contains("<select id=\"asset-type\"")),
        () -> assertTrue(generic.contains("type=\"hidden\" name=\"type\"")),
        () -> assertTrue(realEstate.contains("valuation-periods/{periodId}")),
        () -> assertTrue(realEstate.contains("valuation-periods/{periodId}/delete")),
        () -> assertTrue(cash.contains("valuation-periods/{periodId}")),
        () -> assertTrue(cash.contains("valuation-periods/{periodId}/delete")),
        () -> assertTrue(bond.contains("bond-rate-periods/{periodId}")),
        () -> assertTrue(bond.contains("bond-rate-periods/{periodId}/delete")),
        () -> assertFalse(summary.contains("Rental tax policies")),
        () -> assertFalse(summary.contains("rental-tax-policy")));
  }

  private static int occurrences(String value, String token) {
    return (value.length() - value.replace(token, "").length()) / token.length();
  }
}
