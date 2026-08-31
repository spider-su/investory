package com.smartbox.investory.app;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Bond Template Contract")
class BondTemplateContractTest {
  @DisplayName("bond Create Form Has One Value Field")
  @Test
  void bondCreateFormHasOneValueField() throws Exception {
    String html =
        Files.readString(Path.of("../adapters/web-ui/src/main/resources/templates/bond-form.html"));
    assertAll(
        () -> assertTrue(html.contains(">Value</label>")),
        () -> assertTrue(html.contains("name=\"value\"")),
        () -> assertFalse(html.contains("Invested value")),
        () -> assertFalse(html.contains("Current value")));
  }

  @DisplayName("bond Edit Form Has One Value Field And Hides Legacy Metrics")
  @Test
  void bondEditFormHasOneValueFieldAndHidesLegacyMetrics() throws Exception {
    String html =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/bond-detail.html"));
    assertAll(
        () -> assertTrue(html.contains(">Current value</label>")),
        () -> assertTrue(html.contains("name=\"value\"")),
        () -> assertFalse(html.contains(">Invested value</label>")),
        () -> assertFalse(html.contains(">P/L<")),
        () -> assertFalse(html.contains(">P/L %<")),
        () -> assertTrue(html.contains("summary.bondPlanning.value")),
        () -> assertTrue(html.contains("summary.bondPlanning.annualRate")),
        () -> assertTrue(html.contains("summary.bondPlanning.maturityDate")));
  }
}
