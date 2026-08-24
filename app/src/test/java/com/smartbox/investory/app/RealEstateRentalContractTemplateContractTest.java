package com.smartbox.investory.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RealEstateRentalContractTemplateContractTest {
  private static final Path TEMPLATE =
      Path.of("../adapters/web-ui/src/main/resources/templates/real-estate-detail.html");
  private static final Path CSS =
      Path.of("../adapters/web-ui/src/main/resources/static/css/asset-detail.css");

  @Test
  void contractManagementIsAccordionFirstAndComplete() throws Exception {
    String html = Files.readString(TEMPLATE);

    assertThat(html)
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

  @Test
  void pageLocalScriptAndResponsiveCssKeepAccordionAccessibleAndScoped() throws Exception {
    String html = Files.readString(TEMPLATE);
    String css = Files.readString(CSS);

    assertThat(html)
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

  private static int occurrences(String value, String token) {
    int count = 0;
    for (int index = 0; (index = value.indexOf(token, index)) >= 0; index += token.length())
      count++;
    return count;
  }
}
