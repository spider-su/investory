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
  private static final String STYLESHEET =
      "../adapters/web-ui/src/main/resources/static/css/main.css";

  @Test
  void allocationUsesDistinctEtfAndEquityPaletteTokens() throws Exception {
    String css = Files.readString(Path.of(STYLESHEET));

    assertTrue(css.contains("--iv-asset-etf:"));
    assertTrue(css.contains(".iv-structure-segment--etf { background: var(--iv-asset-etf); }"));
    assertTrue(
        css.contains(".iv-structure-segment--equity { background: var(--iv-asset-equity); }"));
    assertFalse(css.contains(".iv-structure-segment--etf { background: var(--iv-asset-equity); }"));
  }

  @Test
  void currencyPopoverIsCompactRightAlignedAndAboveHeaderNavigation() throws Exception {
    String css = Files.readString(Path.of(STYLESHEET));

    assertTrue(
        css.contains(
            ".iv-topbar__meta .iv-topbar-fx-popover > .iv-topbar-fx-popover__panel"));
    assertTrue(css.contains("width: min(340px, calc(100vw - 32px));"));
    assertTrue(css.contains(".iv-topbar:has(.iv-topbar-fx-popover[open]) { z-index: 1400; }"));
    assertTrue(
        css.contains(
            ".iv-topbar__meta:has(.iv-topbar-fx-popover[open]) { position: relative; z-index: 1500; }"));
  }

  @Test
  void applicationHeaderLetsTheNavigationRailOwnItsVerticalSpacing() throws Exception {
    String css = Files.readString(Path.of(STYLESHEET));

    assertTrue(css.contains(".iv-topbar.iv-app-header-shell"));
    assertTrue(css.contains("padding-bottom: 0;"));
    assertTrue(css.contains(".iv-topbar__secondary {"));
    assertTrue(css.contains("align-items: center;"));
  }

  @Test
  void compactPopoversShareViewportAwarePlacementAndMobileLayout() throws Exception {
    String html = Files.readString(Path.of(TEMPLATE));
    String css = Files.readString(Path.of(STYLESHEET));
    String accessibility =
        Files.readString(
            Path.of(
                "../adapters/web-ui/src/main/resources/static/js/dashboard-accessibility.js"));

    assertTrue(html.contains("iv-structure-card iv-compact-popover"));
    assertTrue(html.contains("iv-compact-popover__body"));
    assertTrue(css.contains("width: min(320px, calc(100vw - 32px));"));
    assertTrue(css.contains("max-height: min(320px, calc(100vh - 32px));"));
    assertTrue(
        css.contains(
            ".iv-portfolio-structure details.iv-compact-popover > .iv-compact-popover__panel"));
    assertTrue(css.contains("width: max-content;"));
    assertTrue(css.contains("grid-template-columns: minmax(0, max-content) max-content;"));
    assertTrue(css.contains(".iv-compact-popover__body > div { display: contents;"));
    assertTrue(css.contains("details.iv-compact-popover.placement-top"));
    assertTrue(css.contains("@media (max-width: 640px)"));
    assertTrue(accessibility.contains("function placeCompactPopover(details)"));
    assertTrue(accessibility.contains("spaceBelow < panelHeight && spaceAbove > spaceBelow"));
    assertTrue(accessibility.contains("window.addEventListener('scroll'"));
    assertTrue(accessibility.contains("const openDetails = Array.from"));
  }

  @Test
  void realizedDetailsContainsOnlyGainersAndLosersAndUsesOverviewAnchor() throws Exception {
    String html = Files.readString(Path.of(TEMPLATE));
    String css = Files.readString(Path.of(STYLESHEET));
    int detailsStart = html.indexOf("iv-realized-details");
    int detailsEnd =
        html.indexOf("<div class=\"iv-kpi iv-kpi--popover iv-overview-card\">", detailsStart);
    String details = html.substring(detailsStart, detailsEnd);

    assertTrue(html.contains("id=\"investment-overview\""));
    assertTrue(details.contains("Top gainers"));
    assertTrue(details.contains("Top losers"));
    assertTrue(details.contains("iv-realized-attribution-row"));
    assertFalse(details.contains("iv-grid iv-grid--split"));
    assertFalse(details.contains("<table class=\"iv-table\">"));
    assertFalse(details.contains("Investment result"));
    assertFalse(details.contains("What is driving results"));
    assertFalse(details.contains("Capital gains tax"));
    assertTrue(
        details.contains("@{/dashboard/assets/{symbol}(symbol=${symbol.symbol})}"));
    assertTrue(details.contains("th:if=\"${symbol.symbol != 'Other'}\""));
    assertTrue(css.contains("#investment-overview .iv-realized-details { position: static; }"));
    assertTrue(css.contains("right: 0;"));
    assertTrue(css.contains("width: min(1100px, 100%);"));
    assertTrue(css.contains(".iv-realized-attribution-row"));
  }

  @Test
  void profitCardsDoNotExposeCurrencyBreakdowns() throws Exception {
    String html = Files.readString(Path.of(TEMPLATE));

    assertFalse(html.contains("By currency"));
    assertFalse(html.contains("realizedByCurrency"));
    assertFalse(html.contains("unrealizedByCurrency"));
  }

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
    assertTrue(html.contains("selected === 0 || selected === inputs.length"));
    assertTrue(html.contains("if (selectedIds.length > 0) params.set('accountIds'"));
    assertTrue(html.contains("if (selectedIds.length > 0) (view.accounts || [])"));
    assertTrue(html.contains("const performanceBoardBenchmarkColor = '#16a34a';"));
    assertFalse(html.contains("performanceBoardAccountPalette = ['#4f46e5', '#16a34a'"));
    assertTrue(html.contains("Number(account.id) === Number(series.accountId)"));
    assertTrue(html.contains("performanceBoardAccountColor(series, index, view.accounts)"));
    assertTrue(html.contains("const visibleSeries = performanceBoardVisibleSeries(view, selectedIds);"));
    assertTrue(html.contains("skipNull: bars"));
    assertTrue(html.contains("barPercentage: bars ? 1 : undefined"));
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
    assertTrue(html.contains("new Intl.NumberFormat('en-US'"));
    assertFalse(html.contains("new Intl.NumberFormat('de-DE'"));
    assertTrue(html.contains("Portfolio data"));
    assertTrue(html.contains("id=\"refresh-prices-btn\""));
    assertTrue(html.contains("Base currency: USD"));
    assertFalse(html.contains("Portfolio values are converted to"));
    assertFalse(html.contains("iv-topbar-fx-popover__total"));
    assertTrue(html.contains("th:text=\"${'Base: ' + stats.baseCurrency}\">Base: USD</span>"));
    assertTrue(headerControls.contains("stats.formatBase(account.baseNetDeposit)"));
    assertTrue(
        headerControls.contains("stats.formatMoney(account.netDeposit, account.localCurrency)"));
    assertTrue(headerControls.contains("class=\"iv-account-name\""));
    assertTrue(headerControls.contains("class=\"iv-account-id\""));
    assertTrue(headerControls.contains("class=\"iv-account-metric\""));
    assertTrue(headerControls.contains("iv-account-metric iv-account-pl"));
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
    assertFalse(html.contains("selectedPeriod.label() + ' investment result'"));
    assertFalse(html.contains("Cash-flow-neutral profit and return for the selected period"));
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
    assertTrue(html.contains("id=\"balance-cash\" class=\"iv-topbar-metric\""));
    assertFalse(html.contains("Current portfolio value from Investory"));
    assertFalse(html.substring(balanceStart, balanceEnd).contains("iv-metric-context__panel"));
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
