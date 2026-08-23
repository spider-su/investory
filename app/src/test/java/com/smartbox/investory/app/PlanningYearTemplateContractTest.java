package com.smartbox.investory.app;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PlanningYearTemplateContractTest {
  @Test
  void historicalDraftSupportsOnlyApplicationApprovedEditsAndLifecycleActions() throws Exception {
    String html =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/planning-year.html"));
    assertAll(
        () -> assertTrue(html.contains("editableMetrics.contains(entry.key)")),
        () -> assertTrue(html.contains("name=\"metric\"")),
        () -> assertTrue(html.contains("name=\"amount\"")),
        () -> assertTrue(html.contains("Year review")),
        () -> assertTrue(html.contains("Review status")),
        () -> assertTrue(html.contains("Plan vs actual")),
        () -> assertTrue(html.contains("Data sources")),
        () -> assertTrue(html.contains("Data reconciliation")),
        () -> assertTrue(html.contains("reviewMetrics.contains(entry.key)")),
        () -> assertTrue(html.contains("Not planned")),
        () -> assertTrue(html.contains("planningReconciliation.metrics")),
        () -> assertTrue(html.contains(">Plan</th>")),
        () -> assertTrue(html.contains(">Actual</th>")),
        () -> assertTrue(html.contains(">Variance</th>")),
        () -> assertTrue(html.contains(">Source</th>")),
        () -> assertTrue(html.contains("Finalize year review")),
        () -> assertTrue(html.contains("Reopen review")),
        () -> assertTrue(html.contains("Plan reference")),
        () -> assertFalse(html.contains("Planning year")),
        () -> assertFalse(html.contains("Completeness")),
        () -> assertFalse(html.contains("No baseline")),
        () -> assertFalse(html.contains("Baseline plan provenance")),
        () -> assertFalse(html.contains("Investory comparison")),
        () -> assertFalse(html.contains("Close planning year")),
        () -> assertFalse(html.contains("Reopen year")),
        () -> assertFalse(html.contains(">Planned</th>")),
        () -> assertFalse(html.contains(">Difference</th>")),
        () -> assertFalse(html.contains(">Derived</th>")),
        () -> assertFalse(html.contains(">Approved</th>")),
        () -> assertFalse(html.contains(">Effective</th>")),
        () -> assertFalse(html.contains(">Expected</th>")),
        () -> assertFalse(html.contains("Requires planning input")),
        () -> assertFalse(html.contains("PASSIVE_INCOME")),
        () -> assertTrue(html.contains("planningCloseStatus.missingMetrics")),
        () -> assertTrue(html.contains("Ready to finalize. Required review values are complete.")),
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
