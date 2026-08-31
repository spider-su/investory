package com.smartbox.investory.app;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LongTermAssetsTemplateContractTest {
  @Test
  void realEstateGroupUsesPublicViewProperties() throws Exception {
    String html =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/long-term-assets.html"));

    assertAll(
        () -> assertTrue(html.contains("group.realEstatePlanning.netMonthlyIncome")),
        () -> assertTrue(html.contains("group.realEstatePlanning.totalPaymentMonthly")),
        () -> assertTrue(html.contains("group.realEstatePlanning.monthlyTax")),
        () -> assertTrue(html.contains("group.realEstatePlanning.netYield")),
        () -> assertTrue(html.contains("asset.realEstatePlanning.taxBase")),
        () -> assertTrue(html.contains("asset.realEstatePlanning.incomeYield")),
        () -> assertFalse(html.contains("group.netMonthlyIncome()")),
        () -> assertFalse(html.contains("group.totalPaymentMonthly()")),
        () -> assertFalse(html.contains("group.shareDisplay(")),
        () -> assertFalse(html.contains("group.shareWidth(")),
        () -> assertFalse(html.contains("monthlyRentTax")));
  }
}
