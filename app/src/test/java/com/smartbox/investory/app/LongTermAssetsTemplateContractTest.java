package com.smartbox.investory.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class LongTermAssetsTemplateContractTest {
  private static final Path TEMPLATE =
      Path.of("../adapters/web-ui/src/main/resources/templates/long-term-assets.html");
  private static final Path HEADER =
      Path.of("../adapters/web-ui/src/main/resources/templates/fragments/app-header.html");

  @Test
  void keepsKnownGoodLayoutAndExplicitProductRoutes() throws Exception {
    String html = Files.readString(TEMPLATE);
    String header = Files.readString(HEADER);

    assertTrue(html.contains("iv-planning-section__details"));
    assertTrue(html.contains("iv-structure-bar"));
    assertTrue(html.contains("longTermHeaderTotal"));
    assertTrue(html.contains("overview.groupShares"));
    assertTrue(html.contains("format.compactMoney"));
    assertTrue(html.contains("format.percentage"));
    assertTrue(header.contains("new/real-estate"));
    assertTrue(header.contains("new/bond"));
    assertTrue(header.contains("new/cash-reserve"));
    assertTrue(header.contains("new/personal-asset"));
    assertTrue(
        Pattern.compile("aria-label=\\\"Create [^\\\"]+\\\"").matcher(header).results().count()
            == 4);
    assertFalse(header.contains("new/deposit"));
    assertTrue(header.contains(">Personal</a>"));
    assertTrue(html.contains("Net income / month"));
    assertTrue(html.contains("Rent tax / month"));
    assertTrue(html.contains("Total payment / month"));
    assertTrue(html.contains("asset.totalPaymentMonthly"));
    assertFalse(html.contains("<th>Value</th><th>Tax base / month</th>"));
    assertTrue(html.contains("Net income / year"));
    assertTrue(html.contains("Tax / year"));
    assertTrue(html.contains("assetGroupType"));
    assertFalse(html.contains("iv-report-table__sub"));
    assertTrue(html.contains("group.key == 'CASH_RESERVE'"));
    assertTrue(html.contains("group.key == 'PERSONAL_ASSET'"));
    assertTrue(html.contains("group.key == 'BOND'"));
    assertFalse(html.contains("DEPOSIT"));
    assertTrue(html.contains("/cash-reserve'"));
    assertTrue(
        html.contains(
            "'/portfolios/' + portfolioId + '/long-term-assets/' + asset.id + '/real-estate'"));
    assertFalse(html.contains("format.compactMoney(group.totalValue) + ' ' + group.currency"));
    assertTrue(html.contains("format.compactMoney(group.totalValue)}"));
  }

  @Test
  void doesNotExposeRemovedPlanningSemanticsOrRawValues() throws Exception {
    String html = Files.readString(TEMPLATE);
    String header = Files.readString(HEADER);
    String combined = html + header;

    assertFalse(combined.contains("Other asset"));
    assertFalse(combined.contains("InterestTreatment"));
    assertFalse(combined.contains("PAY_OUT"));
    assertFalse(combined.contains("CAPITALIZE"));
    assertTrue(html.contains("monthlyTax"));
    assertFalse(combined.contains("realEstatePlanning"));
    assertFalse(combined.contains("bondPlanning"));
    assertFalse(combined.contains("annualReturnPercent"));
    assertFalse(combined.contains("th:text=\"${asset.currentValue}\""));
  }

  @Test
  void rentalOverviewKeepsMonthlyTaxBaseAndMonthlyTaxDistinct() throws Exception {
    String html = Files.readString(TEMPLATE);

    assertTrue(html.contains("<th>Rent tax / month</th>"));
    assertTrue(html.contains("Rent tax / month"));
    assertTrue(
        html.contains("format.compactMoney(asset.annualEconomics.monthlyTaxBase) + ' / month'"));
    assertTrue(
        html.contains("format.compactMoney(group.annualEconomics.monthlyTaxBase) + ' / month'"));
    assertFalse(
        html.contains("format.compactMoney(group.annualEconomics.monthlyTax) + ' / month'"));
    assertFalse(html.contains("monthly(group.annualEconomics.annualTax)"));
  }

  @Test
  void realEstateFormsLabelThePersistedTaxBaseAsAnnual() throws Exception {
    String forms =
        Files.readString(
                Path.of("../adapters/web-ui/src/main/resources/templates/real-estate-form.html"))
            + Files.readString(
                Path.of(
                    "../adapters/web-ui/src/main/resources/templates/real-estate/fragments/settings.html"));

    assertTrue(forms.contains("Annual rental tax base"));
    assertFalse(forms.contains("Monthly rental tax base"));
    assertTrue(forms.contains("name=\"taxBase\""));
  }

  @Test
  void rentalContractTaxCardRendersAuthoritativePropertyEconomics() throws Exception {
    String html =
        Files.readString(
            Path.of(
                "../adapters/web-ui/src/main/resources/templates/real-estate/fragments/contracts.html"));

    assertTrue(html.contains("<h3>Tax</h3>"));
    assertTrue(html.contains("Annual tax base"));
    assertTrue(html.contains("Tax rate"));
    assertTrue(html.contains("Tax / year"));
    assertTrue(html.contains("Tax / month"));
    assertTrue(html.contains("asset.taxBase"));
    assertTrue(html.contains("summary.annualEconomics.annualTax"));
    assertTrue(html.contains("summary.annualEconomics.monthlyTax"));
    assertFalse(html.contains("rentalTaxPaidByTenant"));
  }

  @Test
  void allLongTermFormsUseThePlanningShellAndExplicitFields() throws Exception {
    String forms =
        String.join(
            "\n",
            Files.readString(
                Path.of("../adapters/web-ui/src/main/resources/templates/real-estate-form.html")),
            Files.readString(
                Path.of("../adapters/web-ui/src/main/resources/templates/bond-form.html")),
            Files.readString(
                Path.of("../adapters/web-ui/src/main/resources/templates/cash-reserve-form.html")),
            Files.readString(
                Path.of(
                    "../adapters/web-ui/src/main/resources/templates/personal-asset-form.html")),
            Files.readString(
                Path.of(
                    "../adapters/web-ui/src/main/resources/templates/real-estate/fragments/contract-form.html")));
    String cashForm =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/cash-reserve-form.html"));

    assertTrue(forms.contains("iv-planning-page"));
    assertTrue(forms.contains("long-term-form-header"));
    assertTrue(forms.contains("iv-card-section-header"));
    assertTrue(forms.contains("UiPresentation).moneyInput(asset.value)"));
    assertTrue(
        forms.contains("asset.interestRate}")
            || forms.contains("percentageInput(asset.interestRate)"));
    assertTrue(forms.contains("name=\"annualRatePercent\""));
    assertTrue(forms.contains("name=\"interestRate\""));
    assertTrue(
        cashForm.contains(
            "th:value=\"${asset?.interestRate == null ? '0.0' : asset.interestRate}\""));
    assertFalse(cashForm.contains("percentageInput(asset.interestRate)"));
    assertTrue(forms.contains("<span class=\"input-group-text\">%</span>"));
    assertTrue(forms.contains("Save property"));
    assertTrue(forms.contains("Save bond"));
    assertTrue(forms.contains("Save cash reserve"));
    assertTrue(forms.contains("Save personal asset"));
    assertTrue(forms.contains("Cancel"));
    assertTrue(forms.contains("name=\"taxBase\""));
    assertFalse(forms.contains("InterestTreatment"));
    assertFalse(forms.contains("PAY_OUT"));
    assertFalse(forms.contains("CAPITALIZE"));
    assertFalse(forms.contains("redemptionValue"));
    assertFalse(forms.contains("name=\"interestTreatment\""));
    assertFalse(forms.contains("name=\"taxRate\""));
    assertFalse(forms.contains("th:value=\"${asset?.interestRate}\""));
    assertFalse(forms.contains("th:value=\"${asset?.currentAnnualRate}\""));
  }
}
