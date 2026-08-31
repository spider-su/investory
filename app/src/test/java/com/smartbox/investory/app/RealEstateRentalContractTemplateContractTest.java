package com.smartbox.investory.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Real Estate Rental Contract Template Contract")
class RealEstateRentalContractTemplateContractTest {
  private static final Path TEMPLATE =
      Path.of("../adapters/web-ui/src/main/resources/templates/real-estate-detail.html");
  private static final Path CSS =
      Path.of("../adapters/web-ui/src/main/resources/static/css/asset-detail.css");

  @DisplayName("contract Management Is Accordion First And Complete")
  @Test
  void contractManagementIsAccordionFirstAndComplete() throws Exception {
    String html = Files.readString(TEMPLATE);
    String script =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/static/js/real-estate-detail.js"));

    assertThat(html + script)
        .contains(
            "iv-property-hero__metrics",
            "Monthly net income",
            "Income yield",
            "id=\"rental-contracts\"",
            "data-show-add-contract",
            "<details class=\"iv-rental-contract\"",
            "th:open=\"${contractStat.first}\"",
            "Effective end",
            "Tenant not specified",
            "data-contract-edit",
            "Save changes",
            "Terminate early",
            "Confirm early termination",
            "data-delete-contract",
            "Delete this contract permanently? Its terms will be removed and historical calculations may change.",
            "Copy latest contract",
            "End the current contract the day before this contract starts",
            "Use property default",
            "Paid by landlord",
            "Paid by tenant",
            "tenantName",
            "tenantEmail",
            "tenantPhone")
        .doesNotContain("iv-contract__end-form", ">End<", "Monthly income</div>");

    assertThat(occurrences(html, "<details class=\"card mb-4 iv-property-settings\"")).isOne();
    assertThat(occurrences(html, "<details class=\"card mb-4 iv-property-advanced\"")).isOne();
    assertThat(html.indexOf("<h2 class=\"card-title mb-1\">Rental contracts</h2>"))
        .isLessThan(html.indexOf("<span class=\"card-title mb-0\">Property settings</span>"));
    assertThat(html.indexOf("<span class=\"card-title mb-0\">Property settings</span>"))
        .isLessThan(html.indexOf("<span class=\"card-title mb-0\">Advanced planning</span>"));
  }

  @DisplayName("page Local Script And Responsive Css Keep Accordion Accessible And Scoped")
  @Test
  void pageLocalScriptAndResponsiveCssKeepAccordionAccessibleAndScoped() throws Exception {
    String html = Files.readString(TEMPLATE);
    String css = Files.readString(CSS);
    String script =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/static/js/real-estate-detail.js"));

    assertThat(html + script)
        .contains(
            "addEventListener('toggle'",
            "other.open = false",
            "data-cancel-edit",
            "data-cancel-terminate",
            "data-cancel-add-contract");
    assertThat(css)
        .contains(
            ".iv-property-page .iv-rental-contract__summary",
            ".iv-property-page .iv-contract-edit-groups",
            ".iv-property-page .iv-rental-contract__summary:focus-visible",
            "@media (max-width: 767px)",
            ".iv-property-page { overflow-x: hidden; }");
  }

  @DisplayName("contract Form Fields Are Safe When Form Model Is Missing")
  @Test
  void contractFormFieldsAreSafeWhenFormModelIsMissing() throws Exception {
    String html = Files.readString(TEMPLATE);

    assertThat(html)
        .contains(
            "th:value=\"${form?.tenantName}\"",
            "th:value=\"${form?.tenantEmail}\"",
            "th:value=\"${form?.tenantPhone}\"",
            "form?.rentFrequency?.name()",
            "form?.rentalTaxOwnership == null")
        .doesNotContain(
            "${form.tenantName}",
            "${form.tenantEmail}",
            "${form.tenantPhone}",
            "${form.rentFrequency.name()}",
            "${form.rentalTaxOwnership");
  }

  private static int occurrences(String value, String token) {
    int count = 0;
    for (int index = 0; (index = value.indexOf(token, index)) >= 0; index += token.length())
      count++;
    return count;
  }
}
