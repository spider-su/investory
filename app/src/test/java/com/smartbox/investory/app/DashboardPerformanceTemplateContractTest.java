package com.smartbox.investory.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Dashboard Performance Template Contract")
class DashboardPerformanceTemplateContractTest {
  private static final String TEMPLATE =
      "../adapters/web-ui/src/main/resources/templates/dashboard.html";

  @DisplayName("allocation Uses Distinct Etf And Equity Palette Tokens")
  @Test
  void allocationUsesDistinctEtfAndEquityPaletteTokens() throws Exception {
    String css = CssTestSupport.readComposedStylesheet();

    assertTrue(css.contains("--iv-asset-etf:"));
    assertTrue(css.contains(".iv-structure-segment--etf { background: var(--iv-asset-etf); }"));
    assertTrue(
        css.contains(".iv-structure-segment--equity { background: var(--iv-asset-equity); }"));
    assertFalse(css.contains(".iv-structure-segment--etf { background: var(--iv-asset-equity); }"));
  }

  @DisplayName("currency Popover Is Compact Right Aligned And Above Header Navigation")
  @Test
  void currencyPopoverIsCompactRightAlignedAndAboveHeaderNavigation() throws Exception {
    String css = CssTestSupport.readComposedStylesheet();

    assertTrue(
        css.contains(".iv-topbar__meta .iv-topbar-fx-popover > .iv-topbar-fx-popover__panel"));
    assertTrue(css.contains("width: min(340px, calc(100vw - 32px));"));
    assertTrue(css.contains(".iv-topbar:has(.iv-topbar-fx-popover[open]) { z-index: 1400; }"));
    assertTrue(
        css.contains(
            ".iv-topbar__meta:has(.iv-topbar-fx-popover[open]) { position: relative; z-index: 1500; }"));
  }

  @DisplayName("action Popovers Stay Above The Header Navigation Rail")
  @Test
  void actionPopoversStayAboveTheHeaderNavigationRail() throws Exception {
    String css = CssTestSupport.readComposedStylesheet();

    assertTrue(css.contains(".iv-topbar-actions > .iv-hover-context:hover"));
    assertTrue(css.contains(".iv-topbar-actions > .iv-hover-context:focus-within"));
    assertTrue(css.contains("z-index: 1100;"));
  }

  @DisplayName("application Header Lets The Navigation Rail Own Its Vertical Spacing")
  @Test
  void applicationHeaderLetsTheNavigationRailOwnItsVerticalSpacing() throws Exception {
    String css = CssTestSupport.readComposedStylesheet();

    assertTrue(css.contains(".iv-topbar.iv-app-header-shell"));
    assertTrue(css.contains("padding-bottom: 0;"));
    assertTrue(css.contains(".iv-topbar__secondary {"));
    assertTrue(css.contains("align-items: center;"));
  }

  @DisplayName("compact Popovers Share Viewport Aware Placement And Mobile Layout")
  @Test
  void compactPopoversShareViewportAwarePlacementAndMobileLayout() throws Exception {
    String html = HtmlTestSupport.readTemplateWithFragments(Path.of(TEMPLATE));
    String css = CssTestSupport.readComposedStylesheet();
    String accessibility =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/static/js/dashboard-accessibility.js"));

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

  @DisplayName("portfolio Structure Popovers Size To Their Content")
  @Test
  void portfolioStructurePopoversSizeToTheirContent() throws Exception {
    String css = CssTestSupport.readComposedStylesheet();

    assertTrue(
        css.contains(".iv-portfolio-structure details.iv-compact-popover > .iv-structure-popover"));
    assertTrue(css.contains("width: fit-content;"));
    assertTrue(css.contains("min-width: 0;"));
  }

  @DisplayName("realized Details Contains Only Gainers And Losers And Uses Overview Anchor")
  @Test
  void realizedDetailsContainsOnlyGainersAndLosersAndUsesOverviewAnchor() throws Exception {
    String html = HtmlTestSupport.readTemplateWithFragments(Path.of(TEMPLATE));
    String css = CssTestSupport.readComposedStylesheet();
    int detailsStart = html.indexOf("iv-realized-details");
    int detailsEnd =
        html.indexOf("<div class=\"iv-kpi iv-kpi--popover iv-overview-card\">", detailsStart);
    String details = html.substring(detailsStart, detailsEnd);

    assertTrue(html.contains("id=\"investment-overview\""));
    assertTrue(details.contains("Top gainers"));
    assertTrue(details.contains("Top losers"));
    assertTrue(details.contains("iv-realized-attribution-table iv-sortable-grid"));
    assertTrue(details.contains("data-sort-realized=${symbol.closedProfit}"));
    assertFalse(details.contains("iv-grid iv-grid--split"));
    assertFalse(details.contains("<table class=\"iv-table\">"));
    assertFalse(details.contains("Investment result"));
    assertFalse(details.contains("What is driving results"));
    assertFalse(details.contains("Capital gains tax"));
    assertTrue(
        details.contains(
            "@{/portfolios/{portfolioId}/dashboard/assets/{symbol}(symbol=${symbol.symbol},portfolioId=${portfolioId})}"));
    assertTrue(details.contains("th:if=\"${symbol.symbol != 'Other'}\""));
    assertTrue(css.contains("#investment-overview .iv-realized-details { position: static; }"));
    assertTrue(css.contains("right: 0;"));
    assertTrue(css.contains("width: min(1100px, 100%);"));
    assertTrue(css.contains(".iv-realized-attribution-row"));
  }

  @DisplayName("profit Cards Do Not Expose Currency Breakdowns")
  @Test
  void profitCardsDoNotExposeCurrencyBreakdowns() throws Exception {
    String html = HtmlTestSupport.readTemplateWithFragments(Path.of(TEMPLATE));

    assertFalse(html.contains("By currency"));
    assertFalse(html.contains("realizedByCurrency"));
    assertFalse(html.contains("unrealizedByCurrency"));
  }

  @DisplayName("dashboard Wording Uses Base Currency And User Facing Action Names")
  @Test
  void dashboardWordingUsesBaseCurrencyAndUserFacingActionNames() throws Exception {
    String html = HtmlTestSupport.readTemplateWithFragments(Path.of(TEMPLATE));
    String actions =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/static/js/dashboard-actions.js"));
    String accountValueActions =
        Files.readString(
            Path.of(
                "../adapters/web-ui/src/main/resources/static/js/dashboard-benchmark-account-value.js"));

    assertTrue(html.contains("INVESTMENT RESULT"));
    assertTrue(html.contains("Update market data"));
    assertTrue(html.contains("Update exchange rates"));
    assertTrue(html.contains("Income breakdown"));
    assertTrue(html.contains("Cash interest"));
    assertTrue(html.contains("iv-position-popover__row--total"));
    assertTrue(html.contains("stats.formatBase(stats.incomeTotal)"));
    assertTrue(html.contains("Top dividend payers"));
    assertTrue(html.contains("Dividends"));
    assertTrue(html.contains("\"baseCurrency\": /*[[${stats.baseCurrency}]]*/ \"USD\""));
    assertFalse(html.contains("Dividends USD"));
    assertFalse(html.contains(" + ' $'"));
    assertTrue(
        actions.contains("Couldn\\u2019t import this statement. Check the file and try again."));
    assertTrue(actions.contains("Importing statement…"));
    assertTrue(actions.contains("Preparing export…"));
    assertTrue(actions.contains("Portfolio exported"));
    assertTrue(actions.contains("Couldn’t create the export. Try again."));
    assertTrue(html.contains("name=\"portfolioId\""));
    assertTrue(html.contains("data-portfolio-id=${portfolioId}"));
    assertTrue(html.contains("\"portfolioId\": /*[[${portfolioId}]]*/ null"));
    assertTrue(actions.contains("new FormData(uploadForm)"));
    assertTrue(actions.contains("exportUrl.searchParams.set('portfolioId', exportPortfolioId)"));
    assertTrue(accountValueActions.contains("daily-attribution?date="));
    assertTrue(accountValueActions.contains("&portfolioId=' + encodeURIComponent(portfolioId)"));
    assertTrue(actions.contains("Market data updated"));
    assertTrue(actions.contains("Couldn’t update market data."));
    assertTrue(actions.contains("Updating exchange rates…"));
    assertTrue(actions.contains("Exchange rates updated"));
    assertTrue(actions.contains("Couldn’t update exchange rates."));
  }

  @DisplayName("dashboard Uses One Unified Performance Board With Modes")
  @Test
  void dashboardUsesOneUnifiedPerformanceBoardWithModes() throws Exception {
    String charts =
        Files.readString(
                Path.of("../adapters/web-ui/src/main/resources/static/js/dashboard-charts.js"))
            + Files.readString(
                Path.of(
                    "../adapters/web-ui/src/main/resources/static/js/dashboard-performance-board.js"))
            + Files.readString(
                Path.of(
                    "../adapters/web-ui/src/main/resources/static/js/dashboard-benchmark-account-value.js"));
    String html = HtmlTestSupport.readTemplateWithFragments(Path.of(TEMPLATE)) + charts;
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
    assertTrue(html.contains("All accounts · 0"));
    assertTrue(html.contains("selected === 0 || selected === inputs.length"));
    assertTrue(html.contains("if (selectedIds.length > 0) params.set('accountIds'"));
    assertTrue(html.contains("if (selectedIds.length > 0) (view.accounts || [])"));
    assertTrue(charts.contains("const performanceBoardBenchmarkColor = '#16a34a';"));
    assertFalse(html.contains("performanceBoardAccountPalette = ['#4f46e5', '#16a34a'"));
    assertTrue(charts.contains("Number(account.id) === Number(series.accountId)"));
    assertTrue(charts.contains("performanceBoardAccountColor(series, index, view.accounts)"));
    assertTrue(
        charts.contains("const visibleSeries = performanceBoardVisibleSeries(view, selectedIds);"));
    assertTrue(charts.contains("skipNull: bars"));
    assertTrue(charts.contains("barPercentage: bars ? 1 : undefined"));
    assertTrue(html.contains("id=\"performance-board-account-selector\""));
    assertTrue(html.contains(">Profit/Loss</button>"));
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
    assertTrue(charts.contains("selectedDashboardPeriod"));
    assertTrue(charts.contains("period: selectedDashboardPeriod"));
    assertTrue(charts.contains("data.portfolioId"));
    assertTrue(html.contains("performance-scope-aggregation"));
    assertTrue(charts.contains("const percentValue ="));
    assertFalse(html.contains("toFixed(1)"));
    assertTrue(html.contains("new Intl.NumberFormat('en-US'"));
    assertFalse(html.contains("new Intl.NumberFormat('de-DE'"));
    assertTrue(html.contains("Market data"));
    assertTrue(html.contains("id=\"refresh-prices-btn\""));
    assertTrue(html.contains("Base currency: USD"));
    assertFalse(html.contains("Portfolio values are converted to"));
    assertFalse(html.contains("iv-topbar-fx-popover__total"));
    assertTrue(
        html.contains(
            "th:text=\"${'Base currency: ' + stats.baseCurrency}\">Base currency: USD</span>"));
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
    assertTrue(headerControls.contains("Profit/Loss</button>"));
    assertTrue(headerControls.contains("Return</button>"));
    assertTrue(headerControls.contains("Cash</button>"));
    assertTrue(html.contains("Rates updated:"));
    assertTrue(html.contains("Last import"));
    assertTrue(html.contains("Changes since export"));
    assertFalse(html.contains("portfolio changes since export"));
    assertFalse(html.contains("yahoo.changes"));
    assertTrue(html.contains("Latest transaction:"));
    assertTrue(headerControls.contains("Valuation quality"));
    assertTrue(headerControls.contains("Technical details"));
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
    assertTrue(kpiStrip.contains(">S&amp;P 500 return</span>"));
    assertTrue(kpiStrip.contains(">Excess return</span>"));
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
    assertTrue(
        html.contains(
            "th:href=\"@{/portfolios/{portfolioId}/dashboard/reconciliation(portfolioId=${portfolioId})}\""));
    assertFalse(html.contains("Net external contributions: deposits less withdrawals."));
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
    assertTrue(
        Files.readString(
                Path.of(
                    "../adapters/web-ui/src/main/resources/static/js/dashboard-accessibility.js"))
            .contains("data-disclosure-group"));
    assertTrue(html.contains("data-disclosure-group=\"dashboard-popovers\""));
    assertFalse(html.contains("Cumulative P/L by account"));
    assertFalse(html.contains("Profit and loss by selected period"));
    assertFalse(html.contains("js-monthly-account\""));
    assertFalse(html.contains("js-account-value-account\""));
    assertFalse(html.contains("js-benchmark-account\""));
    assertTrue(charts.contains("const requestedAccountsLoaded ="));
    assertTrue(charts.contains("[...selectedIds].every(id => loadedIds.has(id))"));
  }

  @DisplayName("monthly performance module has valid local element ownership")
  @Test
  void monthlyPerformanceModuleHasValidLocalElementOwnership() throws Exception {
    String javascript =
        Files.readString(
            Path.of(
                "../adapters/web-ui/src/main/resources/static/js/dashboard-monthly-performance.js"));

    assertFalse(javascript.contains("const monthlyPerformanceEl"));
    assertTrue(javascript.contains("'Accounts: ' + count + '\\nExternal flow: ' + cashflow"));
  }

  private static int occurrencesOf(String text, String fragment) {
    return text.split(java.util.regex.Pattern.quote(fragment), -1).length - 1;
  }
}
