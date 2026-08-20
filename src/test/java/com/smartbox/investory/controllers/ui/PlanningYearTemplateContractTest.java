package com.smartbox.investory.controllers.ui;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PlanningYearTemplateContractTest {
  @Test
  void historicalDraftSupportsOnlyApplicationApprovedEditsAndLifecycleActions() throws Exception {
    String html = Files.readString(Path.of("src/main/resources/templates/planning-year.html"));
    assertAll(
        () -> assertTrue(html.contains("editableMetrics.contains(entry.key)")),
        () -> assertTrue(html.contains("name=\"metric\"")),
        () -> assertTrue(html.contains("name=\"amount\"")),
        () -> assertTrue(html.contains("Close planning year")),
        () -> assertTrue(html.contains("Investory comparison")),
        () -> assertTrue(html.contains("planningReconciliation.metrics")),
        () -> assertTrue(html.contains(">Planned</th>")),
        () -> assertTrue(html.contains(">Actual</th>")),
        () -> assertTrue(html.contains(">Difference</th>")),
        () -> assertTrue(html.contains(">Source</th>")),
        () -> assertFalse(html.contains(">Derived</th>")),
        () -> assertFalse(html.contains(">Approved</th>")),
        () -> assertFalse(html.contains(">Effective</th>")),
        () -> assertFalse(html.contains(">Expected</th>")),
        () -> assertFalse(html.contains("Requires planning input")),
        () -> assertFalse(html.contains("PASSIVE_INCOME")),
        () -> assertTrue(html.contains("Reopen year")),
        () -> assertTrue(html.contains("planningCloseStatus.missingMetrics")),
        () -> assertTrue(html.contains("Required historical planning values are complete")),
        () -> assertTrue(html.contains("netWorthUnavailableWithMarketAssets")),
        () -> assertTrue(html.contains("planningHeader('simulation'")),
        () -> assertTrue(html.contains("fragments/app-footer :: buildFooter")),
        () -> assertTrue(html.contains("for=\"planning-display-currency\">Currency")),
        () -> assertTrue(html.contains("UiPresentation).planningMetric")),
        () ->
            assertFalse(
                html.contains(
                    "planningMetric(entry.key, entry.value.derivedValue, planningDisplayCurrency)")),
        () -> assertFalse(html.contains("UiPresentation).money(entry.value.derivedValue")));
  }
}
