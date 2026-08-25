package com.smartbox.investory.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DashboardPerformanceTemplateContractTest {
  private static final String TEMPLATE =
      "../adapters/web-ui/src/main/resources/templates/dashboard.html";

  @Test
  void dashboardUsesOneUnifiedPerformanceBoardWithModes() throws Exception {
    String html = Files.readString(Path.of(TEMPLATE));
    String headerControls =
        Files.readString(
            Path.of(
                "../adapters/web-ui/src/main/resources/templates/dashboard/fragments/header-controls.html"));
    assertTrue(html.contains("id=\"portfolio-performance\""));
    assertFalse(html.contains("<h3 class=\"iv-card__title\">Performance Board"));
    assertTrue(html.contains("id=\"performance-board-chart\""));
    assertTrue(html.contains("data-metric=\"return\""));
    assertTrue(html.contains("data-metric=\"pl\""));
    assertTrue(html.contains("data-style=\"line\""));
    assertTrue(html.contains("data-style=\"bars\""));
    assertTrue(html.contains("js-performance-board-account"));
    assertTrue(html.contains("Accounts: All (0)"));
    assertTrue(html.contains("id=\"performance-board-account-selector\""));
    assertTrue(html.contains(">P/L</button>"));
    assertTrue(html.contains("aria-label=\"Aggregation\""));
    assertTrue(html.contains("S&amp;P 500</span>"));
    assertFalse(html.contains("Compare with S&amp;P 500"));
    assertFalse(html.contains("Line is cumulative; bars show the selected period."));
    assertTrue(html.contains("performance-board-show-spy"));
    assertEquals(1, occurrencesOf(html, "const performanceBoardEl ="));
    assertEquals(1, occurrencesOf(html, "let performanceBoardChart ="));
    assertFalse(html.contains("performanceBoardCumulativeReturn"));
    assertFalse(html.contains("performanceBoardPeriodReturn"));
    assertFalse(html.contains("performanceBoardRebasedReturn"));
    assertTrue(html.contains("const selectedDashboardPeriod ="));
    assertTrue(html.contains("period: selectedDashboardPeriod"));
    assertTrue(html.contains("performance-scope-aggregation"));
    assertTrue(html.contains("const percentValue ="));
    assertFalse(html.contains("toFixed(1)"));
    assertTrue(html.contains("Portfolio data"));
    assertTrue(html.contains("id=\"refresh-prices-btn\""));
    assertTrue(html.contains("Base currency: USD"));
    assertTrue(html.contains("th:text=\"${'Base: ' + stats.baseCurrency}\">Base: USD</span>"));
    assertTrue(headerControls.contains("stats.formatBase(account.baseNetDeposit)"));
    assertTrue(
        headerControls.contains("stats.formatMoney(account.netDeposit, account.localCurrency)"));
    assertTrue(headerControls.contains("class=\"iv-account-name\""));
    assertTrue(headerControls.contains("class=\"iv-account-id\""));
    assertTrue(headerControls.contains("data-sort-key=\"pl\""));
    assertTrue(headerControls.contains("Net deposit</button>"));
    assertTrue(headerControls.contains("Balance</button>"));
    assertTrue(headerControls.contains("Profit</button>"));
    assertTrue(headerControls.contains("Cash</button>"));
    assertTrue(html.contains("Rates updated:"));
    assertTrue(html.contains("Last import"));
    assertTrue(html.contains("Changes since export"));
    assertFalse(html.contains("portfolio changes since export"));
    assertFalse(html.contains("yahoo.changes"));
    assertTrue(html.contains("Latest transaction:"));
    assertTrue(headerControls.contains("Valuation status"));
    assertTrue(html.contains("Asset allocation"));
    assertTrue(html.contains("Portfolio structure"));
    int kpiStripStart = html.indexOf("<div class=\"iv-benchmark iv-performance-metrics\">");
    int performanceControlsStart =
        html.indexOf("<div class=\"iv-card__controls iv-performance-toolbar\"");
    assertTrue(kpiStripStart >= 0 && performanceControlsStart > kpiStripStart);
    String kpiStrip = html.substring(kpiStripStart, performanceControlsStart);
    assertTrue(kpiStrip.contains("Current drawdown"));
    assertTrue(kpiStrip.contains("Max drawdown"));
    assertTrue(kpiStrip.contains(">TWR</span>"));
    assertTrue(kpiStrip.contains(">XIRR</span>"));
    assertTrue(kpiStrip.contains(">Benchmark</span>"));
    assertTrue(kpiStrip.contains(">Excess Return</span>"));
    assertFalse(kpiStrip.contains(">P/L</span>"));
    assertFalse(kpiStrip.contains("Realized P/L"));
    assertFalse(kpiStrip.contains(">Dividends</span>"));
    assertFalse(kpiStrip.contains(">Interest</span>"));
    assertFalse(kpiStrip.contains("Unexplained residual"));
    assertFalse(kpiStrip.contains("Best period"));
    assertFalse(kpiStrip.contains("Worst period"));
    assertTrue(html.contains("Largest holding"));
    assertTrue(html.contains("Concentration"));
    assertTrue(html.contains("Account currency"));
    assertTrue(html.contains("iv-portfolio-structure__grid"));
    assertTrue(html.contains("iv-structure-bar"));
    assertFalse(html.contains("RISK &amp; EXPOSURE"));
    assertFalse(html.contains("Portfolio characteristics"));
    assertFalse(html.contains("Base-currency account exposure"));
    assertFalse(html.contains("Income since inception"));
    assertFalse(html.contains("Exposure data unavailable"));
    assertTrue(html.contains("modal-reconciliation-link"));
    assertTrue(html.contains("Net external contributions: deposits less withdrawals."));
    assertTrue(html.contains("Investment result"));
    assertTrue(html.contains("selectedPeriod.label() + ' investment result'"));
    assertTrue(
        html.contains(
            "Cash-flow-neutral profit and return for the selected period, after portfolio adjustments."));
    assertTrue(html.contains("Return since KPI start"));
    assertTrue(html.contains("Annualized return"));
    assertTrue(html.contains("kpiStartDate"));
    int profitStart = html.indexOf("id=\"investment-gain\"");
    int balanceStart = html.indexOf("id=\"balance-cash\"");
    int balanceEnd =
        html.indexOf("class=\"iv-topbar__secondary iv-app-header-shell__secondary\"", balanceStart);
    assertTrue(profitStart >= 0 && balanceStart > profitStart);
    assertTrue(html.substring(profitStart, balanceStart).contains("Annualized return"));
    assertTrue(balanceEnd > balanceStart);
    assertFalse(html.substring(balanceStart, balanceEnd).contains("Annualized return"));
    assertTrue(html.contains("Portfolio value"));
    assertTrue(html.contains("id=\"cash-flows\" class=\"iv-topbar-metric iv-metric-context\""));
    assertTrue(html.contains("id=\"balance-cash\" class=\"iv-topbar-metric iv-metric-context\""));
    assertTrue(
        Files.readString(
                Path.of(
                    "../adapters/web-ui/src/main/resources/static/js/dashboard-accessibility.js"))
            .contains(".iv-metric-context:focus"));
    assertTrue(
        Files.readString(
                Path.of(
                    "../adapters/web-ui/src/main/resources/static/js/dashboard-accessibility.js"))
            .contains("document.querySelectorAll('details[open]')"));
    assertFalse(html.contains("Cumulative P/L by account"));
    assertFalse(html.contains("Profit and loss by selected period"));
    assertFalse(html.contains("js-monthly-account\""));
    assertFalse(html.contains("js-account-value-account\""));
    assertFalse(html.contains("js-benchmark-account\""));
    assertTrue(html.contains("const requestedAccountsLoaded ="));
    assertTrue(html.contains("[...selectedIds].every(id => loadedIds.has(id))"));
  }

  private static int occurrencesOf(String text, String fragment) {
    return text.split(java.util.regex.Pattern.quote(fragment), -1).length - 1;
  }
}
