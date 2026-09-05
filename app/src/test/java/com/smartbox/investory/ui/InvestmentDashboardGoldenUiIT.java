package com.smartbox.investory.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.Tracing;
import com.smartbox.investory.integrations.notifications.application.PersistentNotificationEventPublisher;
import com.smartbox.investory.investment.api.operations.InvestmentMaintenanceApi;
import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi.DashboardQuery;
import com.smartbox.investory.investment.api.reporting.InvestmentPerformanceApi;
import com.smartbox.investory.investment.api.reporting.InvestmentPerformanceApi.PerformanceBoardQuery;
import com.smartbox.investory.investment.api.reporting.PerformanceAggregation;
import com.smartbox.investory.investment.api.reporting.PerformanceMetric;
import com.smartbox.investory.investment.api.reporting.PerformanceStyle;
import com.smartbox.investory.investment.api.reporting.model.AccountBalance;
import com.smartbox.investory.investment.api.reporting.model.AssetAllocationView;
import com.smartbox.investory.investment.api.reporting.model.InstrumentPerformance;
import com.smartbox.investory.investment.api.reporting.model.OpenPositionValue;
import com.smartbox.investory.investment.api.reporting.model.OverviewView;
import com.smartbox.investory.investment.api.reporting.model.PerformanceSummary;
import com.smartbox.investory.investment.api.reporting.model.PerformanceView;
import com.smartbox.investory.investment.api.reporting.model.PortfolioStructureView;
import com.smartbox.investory.investment.api.reporting.model.ReturnMetric;
import com.smartbox.investory.investment.imports.ImportExecutionResult;
import com.smartbox.investory.investment.imports.ibkr.IbkrImportService;
import com.smartbox.investory.investment.imports.xtb.XtbImportService;
import com.smartbox.investory.investment.projection.PortfolioProjectionRefreshService;
import com.smartbox.investory.investment.projection.PortfolioProjectionService;
import com.smartbox.investory.investment.valuation.fx.CurrencyRateService;
import com.smartbox.investory.testsupport.FastDatabaseTest;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorDashboardFacts;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorTestData;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test-fast")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.flyway.enabled=false",
      "investory.time.fixed-instant=2025-12-31T12:00:00Z"
    })
@Import(InvestmentDashboardGoldenUiIT.JacksonTestConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Investment Dashboard Golden UI")
class InvestmentDashboardGoldenUiIT extends FastDatabaseTest {

  private static final String GOLDEN_ROOT = "/reconciliation/golden/";
  private static final long PORTFOLIO_ID = HappyInvestorTestData.PORTFOLIO_ID;
  private static final String UPDATED_SYMBOL = "VWRA.UK";
  private static final BigDecimal FIXED_USD_PLN_RATE = new BigDecimal("4.00000000");
  private static final BigDecimal FIXED_PLN_USD_RATE = new BigDecimal("0.25000000");
  private static final Set<Long> GOLDEN_ACCOUNTS =
      Set.of(17959259L, 51499241L, 51993106L, 51551301L, 50290466L);
  private static final Path ARTIFACT_DIRECTORY = Path.of("target", "ui-test-results");

  @Value("${local.server.port}")
  private int port;

  @Autowired private JdbcTemplate jdbc;
  @Autowired private IbkrImportService ibkrImport;
  @Autowired private XtbImportService xtbImport;
  @Autowired private PortfolioProjectionService projections;
  @Autowired private PortfolioProjectionRefreshService projectionRefreshService;
  @Autowired private CurrencyRateService currencyRateService;
  @Autowired private InvestmentDashboardApi dashboardApi;
  @Autowired private InvestmentPerformanceApi performanceApi;
  @MockitoBean private InvestmentMaintenanceApi maintenance;
  @MockitoBean private PersistentNotificationEventPublisher notifications;

  private Playwright playwright;
  private Browser browser;

  @TestConfiguration(proxyBeanMethods = false)
  static class JacksonTestConfiguration {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }

  @BeforeAll
  void prepareGoldenPortfolio() throws Exception {
    timed("baseline preparation", this::importGoldenPortfolio);
    timed(
        "FX preload",
        () -> {
          jdbc.execute(
              "CREATE INDEX IF NOT EXISTS ix_exchange_rates_pair_date "
                  + "ON investory.exchange_rates (base, to_currency, rate_date DESC)");
          loadGoldenFx();
          extendGoldenFxThroughCashOperations();
        });
    jdbc.update("UPDATE investory.assets SET price_source = 'GOLDEN' WHERE price_source IS NULL");
    timed("account rebuild", () -> projections.recalculateAccounts(GOLDEN_ACCOUNTS));
    timed(
        "reporting refresh",
        () ->
            projectionRefreshService.refreshApplicationViews(
                PortfolioProjectionRefreshService.ApplicationRefreshScope.DASHBOARD));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM investory.app_v_current_open_position_rows "
                    + "WHERE portfolio_id = ? AND asset_symbol = ?",
                Integer.class,
                PORTFOLIO_ID,
                UPDATED_SYMBOL))
        .isPositive();

    doAnswer(
            invocation -> {
              writeFixedFxRates();
              projectionRefreshService.refreshApplicationViews(
                  PortfolioProjectionRefreshService.ApplicationRefreshScope.FX_UPDATE);
              return Map.of("updated", List.of("USD/PLN", "PLN/USD"), "failed", List.of());
            })
        .when(maintenance)
        .refreshCurrency();

    playwright = Playwright.create();
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
  }

  @AfterAll
  void closeResources() {
    if (browser != null) browser.close();
    if (playwright != null) playwright.close();
  }

  @DisplayName("golden Investment Dashboard Reflects Fixed Currency Updates")
  @Test
  void goldenInvestmentDashboardReflectsFixedCurrencyUpdates() throws Exception {
    timed(
        "browser assertions",
        () ->
            runScenario(
                page -> {
                  openDashboard(page);
                  OverviewView initial = assertDashboardSmoke(page);

                  page.locator(".iv-topbar-fx-popover > summary").click();
                  Response fxResponse =
                      page.waitForResponse(
                          response ->
                              response
                                  .url()
                                  .endsWith("/api/v1/investment/maintenance/refresh-currency"),
                          () ->
                              page.waitForNavigation(
                                  () -> page.locator("#refresh-currency-btn").click()));
                  assertThat(fxResponse.status()).isEqualTo(200);
                  assertFixedFxRatesInDatabase();
                  OverviewView afterFx = assertDashboardRefreshValues(page);
                  assertThat(
                          afterFx
                              .exchangeRates()
                              .get(com.smartbox.investory.shared.currency.CurrencyType.PLN))
                      .isEqualTo(FIXED_USD_PLN_RATE.doubleValue());
                  assertThat(afterFx.balance()).isNotEqualTo(initial.balance());
                }));
  }

  private void importGoldenPortfolio() throws Exception {
    try (InputStream input = resource("ibkr/U17959259.TRANSACTIONS.GOLDEN.csv")) {
      ImportExecutionResult result =
          ibkrImport.importStatement(input, "U17959259.TRANSACTIONS.GOLDEN.csv");
      assertThat(result.rowsTotal()).isEqualTo(19);
      assertThat(result.rowsApplied()).isEqualTo(19);
      assertThat(result.rowsFailed()).isZero();
    }
    try (InputStream input = goldenBinary("xtb/investory_xtb_golden.zip")) {
      ImportExecutionResult result = xtbImport.importZip(input, "investory_xtb_golden.zip");
      assertThat(result.rowsApplied()).isPositive();
      assertThat(result.rowsFailed()).isZero();
    }
  }

  private void timed(String phase, ThrowingAction action) throws Exception {
    long started = System.nanoTime();
    try {
      action.run();
    } finally {
      System.out.printf(
          "GOLDEN PHASE: %s durationMs=%d%n", phase, (System.nanoTime() - started) / 1_000_000);
    }
  }

  @FunctionalInterface
  private interface ThrowingAction {
    void run() throws Exception;
  }

  /**
   * Refresh checks only the values affected by maintenance; the initial page covers full rendering.
   */
  private OverviewView assertDashboardRefreshValues(Page page) {
    InvestmentDashboardApi.DashboardPageView dashboard =
        dashboardApi.loadDashboard(
            new DashboardQuery(List.of(), false, DashboardPeriod.MAX, PORTFOLIO_ID));
    OverviewView overview = (OverviewView) dashboard.overview();

    page.locator("#investment-overview").waitFor();
    assertThat(page.locator("body").textContent())
        .contains("Portfolio structure")
        .doesNotContain("Whitelabel Error Page", "Internal Server Error", "Exception:");
    assertThat(page.locator("#balance-cash .iv-topbar-metric__value").textContent())
        .isEqualTo(whole(overview.balance()));
    assertThat(cardValue(overviewCard(page, "Unrealized P/L")))
        .isEqualTo(overview.formatBase(overview.unrealizedProfit()));
    OpenPositionValue updated = position(overview, UPDATED_SYMBOL);
    assertThat(
            overviewCard(page, "Unrealized P/L")
                .locator(".iv-position-popover__row[data-sort-row]")
                .filter(new Locator.FilterOptions().setHasText(UPDATED_SYMBOL))
                .textContent())
        .contains(UPDATED_SYMBOL, decimalComma(updated.getMarketPrice(), 2));
    return overview;
  }

  private OverviewView assertDashboardSmoke(Page page) {
    InvestmentDashboardApi.DashboardPageView dashboard =
        dashboardApi.loadDashboard(
            new DashboardQuery(List.of(), false, DashboardPeriod.MAX, PORTFOLIO_ID));
    OverviewView overview = (OverviewView) dashboard.overview();

    assertThat(overview.baseCurrency().name())
        .isEqualTo(HappyInvestorDashboardFacts.REPORTING_CURRENCY);

    assertThat(page.title()).contains("Investory");
    assertThat(page.locator("body").textContent())
        .contains("Portfolio structure")
        .doesNotContain("Whitelabel Error Page", "Internal Server Error", "Exception:");
    assertThat(page.locator("#balance-cash .iv-topbar-metric__value").textContent())
        .isEqualTo(whole(overview.balance()));
    assertThat(cardValue(overviewCard(page, "Unrealized P/L")))
        .isEqualTo(overview.formatBase(overview.unrealizedProfit()));
    return overview;
  }

  private void assertTopbar(
      Page page, OverviewView overview, PerformanceSummary performanceSummary) {
    Locator invested = page.locator("#cash-flows");
    assertThat(invested.locator(".iv-topbar-metric__value").textContent())
        .isEqualTo(whole(overview.netDeposits()));
    assertThat(invested.textContent())
        .contains(
            overview.formatBase(overview.deposits()), overview.formatBase(overview.withdrawals()));

    Locator result = page.locator("#investment-gain");
    assertThat(result.locator(".iv-topbar-metric__value").textContent())
        .isEqualTo(whole(overview.totalProfit()));
    if (overview.gainPct() != null)
      assertThat(result.textContent()).contains(overview.formatSignedPercent(overview.gainPct()));
    assertReturnMetric(result, performanceSummary.kpiReturn(), performanceSummary, false);
    assertReturnMetric(result, performanceSummary.annualizedReturn(), performanceSummary, true);

    assertThat(page.locator("#balance-cash .iv-topbar-metric__value").textContent())
        .isEqualTo(whole(overview.balance()));
    Locator fx = page.locator(".iv-topbar-fx-popover");
    assertThat(fx.textContent()).contains("Base currency: " + overview.baseCurrency());
    overview
        .exchangeRates()
        .forEach(
            (currency, rate) -> {
              if (currency != overview.baseCurrency())
                assertThat(fx.textContent())
                    .contains(overview.baseCurrency() + " → " + currency, decimalComma(rate, 2));
            });
  }

  private void assertOverviewCards(Page page, OverviewView overview, PerformanceView performance) {
    Locator unrealized = overviewCard(page, "Unrealized P/L");
    assertThat(cardValue(unrealized)).isEqualTo(overview.formatBase(overview.unrealizedProfit()));
    for (OpenPositionValue position : overview.openPositionValues()) {
      Locator row =
          unrealized
              .locator(".iv-position-popover__row[data-sort-row]")
              .filter(new Locator.FilterOptions().setHasText(position.getSymbol()));
      assertThat(row.count()).isEqualTo(1);
      assertThat(row.textContent())
          .contains(
              position.getSymbol(),
              decimalComma(position.getMarketPrice(), 2),
              position.getMarketPriceCurrency().name(),
              overview.formatBase(position.getValue()),
              overview.formatBase(position.getUnrealized()),
              overview.formatPercent(position.getProfitLossPercent()),
              overview.formatPercent(position.getSharePercent()));
    }
    OpenPositionValue total = overview.openPositionValuesTotal();
    assertThat(unrealized.locator(".iv-position-popover__row--total").textContent())
        .contains(
            overview.formatBase(total.getValue()),
            overview.formatBase(total.getUnrealized()),
            overview.formatPercent(total.getProfitLossPercent()),
            overview.formatPercent(total.getSharePercent()));

    Locator realized = overviewCard(page, "Realized P/L");
    assertThat(cardValue(realized)).isEqualTo(overview.formatBase(overview.realizedProfit()));
    assertInstrumentRows(realized, performance.topGainers());
    assertInstrumentRows(realized, performance.topLosers());

    Locator dividends = overviewCard(page, "Dividends");
    assertThat(cardValue(dividends)).isEqualTo(overview.formatBase(overview.incomeTotal()));
    assertThat(dividends.textContent())
        .contains(
            overview.formatBase(overview.dividends()),
            overview.formatBase(overview.dividendTax()),
            overview.formatBase(overview.interest()));
    if (overview.incomeYieldPct() != null)
      assertThat(dividends.textContent())
          .contains(overview.formatPercent(overview.incomeYieldPct()));
    overview
        .dividendGainers()
        .forEach(
            payer ->
                assertThat(dividends.textContent())
                    .contains(payer.getSymbol(), overview.formatBase(payer.getDividends())));
  }

  private void assertPerformance(Page page, PerformanceSummary summary) {
    Locator metrics = page.locator(".iv-performance-metrics");
    assertThat(metrics.textContent())
        .contains(
            summary.formatPercent(summary.currentDrawdownPct()),
            summary.formatPercent(summary.maxDrawdownPct()));
    if (summary.timeWeightedReturn().status() == ReturnMetric.Status.AVAILABLE)
      assertThat(metrics.textContent())
          .contains(
              summary.formatSignedPercent(
                  summary.timeWeightedReturn().value().doubleValue() * 100));
    if (summary.moneyWeightedReturn().status() == ReturnMetric.Status.AVAILABLE)
      assertThat(metrics.textContent())
          .contains(
              summary.formatSignedPercent(
                  summary.moneyWeightedReturn().value().doubleValue() * 100));

    InvestmentPerformanceApi.PerformanceBoardView board =
        performanceApi.load(
            new PerformanceBoardQuery(
                null,
                PerformanceAggregation.MONTHLY,
                PerformanceMetric.RETURN,
                PerformanceStyle.LINE,
                null,
                1L));
    page.locator("#performance-board-return").waitFor();
    page.waitForFunction(
        "() => document.querySelector('#performance-board-return')?.textContent.trim() !== '—'");
    assertJsKpi(
        page,
        "#performance-board-return",
        board.kpis().portfolioReturn().doubleValue(),
        "%",
        false);
    assertJsKpi(
        page, "#performance-board-spy", board.kpis().benchmarkReturn().doubleValue(), "%", false);
    assertJsKpi(
        page, "#performance-board-excess", board.kpis().excessReturn().doubleValue(), " pp", false);
    assertThat(page.locator("#performance-board-account-label").textContent())
        .contains(String.valueOf(board.accounts().size()));
    board
        .accounts()
        .forEach(
            account ->
                assertThat(page.locator("#portfolio-performance").textContent())
                    .contains(account.name()));
  }

  private void assertPortfolioStructure(Page page, OverviewView overview) {
    PortfolioStructureView structure = overview.portfolioStructure();
    Locator section = page.locator(".iv-portfolio-structure");

    Locator cash = structureCard(section, "Cash");
    assertThat(cash.locator("summary").textContent())
        .contains(
            overview.formatBase(structure.cash()),
            overview.formatPercent(structure.cashWeightPct()));
    for (AccountBalance account : overview.accountBalances()) {
      Locator row =
          cash.locator(".iv-compact-popover__body > div")
              .filter(new Locator.FilterOptions().setHasText(account.getAccountName()));
      assertThat(row.count()).isEqualTo(1);
      assertThat(row.textContent())
          .contains(account.getAccountName(), overview.formatBase(account.getCash()));
    }

    PortfolioStructureView.Holding largest = structure.largestHolding();
    Locator largestCard = structureCard(section, "Largest holding");
    assertThat(largest).isNotNull();
    assertThat(largestCard.textContent())
        .contains(
            largest.symbol(),
            overview.formatBase(largest.value()),
            overview.formatPercent(largest.weightPct()),
            overview.formatBase(largest.unrealized()));

    Locator concentration = structureCard(section, "Concentration");
    assertThat(concentration.textContent())
        .contains(
            overview.formatPercent(structure.topFiveWeightPct()),
            overview.formatPercent(structure.topTenWeightPct()));
    structure
        .topHoldings()
        .forEach(
            holding ->
                assertThat(concentration.textContent())
                    .contains(holding.symbol(), overview.formatPercent(holding.weightPct())));

    Locator allocation = structureCard(section, "Asset allocation");
    AssetAllocationView assetAllocation = structure.assetAllocation();
    assetAllocation
        .buckets()
        .forEach(
            bucket ->
                assertThat(allocation.textContent())
                    .contains(
                        bucket.name(),
                        overview.formatBase(bucket.value()),
                        overview.formatPercent(bucket.weightPct())));

    Locator currencies = section.locator(".iv-structure-currency");
    structure
        .accountCurrencies()
        .forEach(
            bucket ->
                assertThat(currencies.textContent())
                    .contains(
                        bucket.currency().name(),
                        overview.formatBase(bucket.value()),
                        overview.formatPercent(bucket.weightPct())));
  }

  private void assertDatabaseMatchesPositions(OverviewView overview) {
    for (OpenPositionValue position : overview.openPositionValues()) {
      Map<String, Object> row =
          jdbc.queryForMap(
              "SELECT sum(volume) AS volume, min(market_price) AS market_price, "
                  + "sum(market_value_in_base_currency) AS market_value, "
                  + "sum(cost_basis_in_base_currency) AS cost_basis "
                  + "FROM investory.app_v_current_open_position_rows "
                  + "WHERE portfolio_id = ? AND asset_symbol = ?",
              PORTFOLIO_ID,
              position.getSymbol());
      assertClose(row.get("volume"), position.getVolume());
      assertClose(row.get("market_price"), position.getMarketPrice());
      assertClose(row.get("market_value"), position.getValue());
      assertClose(
          ((Number) row.get("market_value")).doubleValue()
              - ((Number) row.get("cost_basis")).doubleValue(),
          position.getUnrealized());
    }
  }

  private void assertInstrumentRows(Locator card, List<InstrumentPerformance> instruments) {
    for (InstrumentPerformance instrument : instruments) {
      Locator row =
          card.locator(".iv-realized-attribution-row[data-sort-row]")
              .filter(new Locator.FilterOptions().setHasText(instrument.getSymbol()));
      assertThat(row.count()).isEqualTo(1);
      assertThat(row.textContent())
          .contains(
              instrument.getSymbol(),
              whole(instrument.getTotal()),
              whole(instrument.getClosedProfit()),
              whole(instrument.getUnrealizedProfit()),
              whole(instrument.getDividends()),
              "-" + whole(instrument.getWithholdingTax()));
    }
  }

  private void assertReturnMetric(
      Locator result, ReturnMetric metric, PerformanceSummary summary, boolean annualized) {
    if (metric.status() == ReturnMetric.Status.AVAILABLE) {
      String expected = summary.formatSignedPercent(metric.value().doubleValue() * 100);
      assertThat(result.textContent()).contains(expected);
    } else {
      assertThat(result.textContent()).contains("Unavailable");
    }
  }

  private void assertJsKpi(Page page, String selector, Double value, String suffix, boolean money) {
    String actual = page.locator(selector).textContent().trim();
    if (value == null) {
      assertThat(actual).isEqualTo("—");
      return;
    }
    String formatted =
        money ? whole(value) : NumberFormat.getNumberInstance(Locale.US).format(roundOne(value));
    assertThat(actual).isEqualTo((value >= 0 ? "+" : "") + formatted + suffix);
  }

  private void assertFixedFxRatesInDatabase() {
    assertLatestRate("USD", "PLN", FIXED_USD_PLN_RATE);
    assertLatestRate("PLN", "USD", FIXED_PLN_USD_RATE);
  }

  private void assertLatestRate(String base, String target, BigDecimal expected) {
    assertThat(
            jdbc.queryForObject(
                "SELECT rate FROM investory.exchange_rates "
                    + "WHERE base = ? AND to_currency = ? AND rate_date <= current_date "
                    + "ORDER BY rate_date DESC, imported_at DESC, id DESC LIMIT 1",
                BigDecimal.class,
                base,
                target))
        .isEqualByComparingTo(expected);
  }

  private void writeFixedFxRates() {
    jdbc.update(
        "DELETE FROM investory.exchange_rates WHERE rate_date = current_date "
            + "AND ((base = 'USD' AND to_currency = 'PLN') "
            + "OR (base = 'PLN' AND to_currency = 'USD'))");
    insertFixedRate("USD", "PLN", FIXED_USD_PLN_RATE);
    insertFixedRate("PLN", "USD", FIXED_PLN_USD_RATE);
    currencyRateService.clearValuationResolutionCache();
  }

  private void insertFixedRate(String base, String target, BigDecimal rate) {
    jdbc.update(
        "INSERT INTO investory.exchange_rates("
            + "rate_date, base, to_currency, rate, source, method, source_reference) "
            + "VALUES (current_date, ?, ?, ?, 'TEST', 'MARKET_DAILY', ?)",
        base,
        target,
        rate,
        "GOLDEN-UI:" + base + ":" + target);
  }

  private void extendGoldenFxThroughCashOperations() {
    jdbc.update(
        """
        INSERT INTO investory.exchange_rates(
            rate_date, base, to_currency, rate, source, method, source_reference)
        SELECT day::date, latest.base, latest.to_currency, latest.rate,
               'TEST', 'MARKET_DAILY',
               'GOLDEN:cash-coverage:' || latest.base || ':' || latest.to_currency || ':' || day::date
        FROM (
            SELECT DISTINCT ON (base, to_currency)
                   base, to_currency, rate
            FROM investory.exchange_rates
            WHERE source = 'TEST'
            ORDER BY base, to_currency, rate_date DESC, id DESC
        ) latest
        CROSS JOIN generate_series(
            (SELECT MIN(date)::date FROM investory.cash_operations),
            (SELECT MAX(date)::date FROM investory.cash_operations),
            interval '1 day'
        ) day
        ON CONFLICT (rate_date, base, to_currency, source, method,
                     (COALESCE(source_reference, ''))) DO UPDATE
            SET rate = EXCLUDED.rate,
                source = EXCLUDED.source,
                method = EXCLUDED.method,
                source_reference = EXCLUDED.source_reference
        """);
    jdbc.update(
        """
        INSERT INTO investory.exchange_rates(
            rate_date, base, to_currency, rate, source, method, source_reference)
        SELECT day::date, 'EUR', 'PLN', eur_usd.rate * usd_pln.rate,
               'TEST', 'MARKET_DAILY',
               'GOLDEN:cash-coverage:EUR:PLN:' || day::date
        FROM generate_series(
            (SELECT MIN(date)::date FROM investory.cash_operations),
            (SELECT MAX(date)::date FROM investory.cash_operations),
            interval '1 day'
        ) day
        CROSS JOIN LATERAL (
            SELECT rate
            FROM investory.exchange_rates
            WHERE base = 'EUR' AND to_currency = 'USD' AND source = 'TEST'
            ORDER BY rate_date DESC, id DESC
            LIMIT 1
        ) eur_usd
        CROSS JOIN LATERAL (
            SELECT rate
            FROM investory.exchange_rates
            WHERE base = 'USD' AND to_currency = 'PLN' AND source = 'TEST'
            ORDER BY rate_date DESC, id DESC
            LIMIT 1
        ) usd_pln
        ON CONFLICT (rate_date, base, to_currency, source, method,
                     (COALESCE(source_reference, ''))) DO UPDATE
            SET rate = EXCLUDED.rate,
                source = EXCLUDED.source,
                method = EXCLUDED.method,
                source_reference = EXCLUDED.source_reference
        """);
    jdbc.update(
        """
        WITH bounds AS (
            SELECT MIN(date)::date AS first_date, MAX(date)::date AS last_date
            FROM investory.cash_operations
        ), legs AS (
            SELECT
                MAX(rate) FILTER (WHERE base = 'EUR' AND to_currency = 'USD') AS eur_usd,
                MAX(rate) FILTER (WHERE base = 'USD' AND to_currency = 'PLN') AS usd_pln
            FROM investory.exchange_rates
            WHERE source = 'TEST'
        ), pairs(base, to_currency, rate) AS (
            SELECT 'EUR', 'USD', eur_usd FROM legs
            UNION ALL SELECT 'USD', 'EUR', 1 / eur_usd FROM legs
            UNION ALL SELECT 'USD', 'PLN', usd_pln FROM legs
            UNION ALL SELECT 'PLN', 'USD', 1 / usd_pln FROM legs
            UNION ALL SELECT 'EUR', 'PLN', eur_usd * usd_pln FROM legs
            UNION ALL SELECT 'PLN', 'EUR', 1 / (eur_usd * usd_pln) FROM legs
        )
        INSERT INTO investory.exchange_rates(
            rate_date, base, to_currency, rate, source, method, source_reference)
        SELECT day::date, pairs.base, pairs.to_currency, pairs.rate,
               'TEST', 'MARKET_DAILY',
               'GOLDEN:cash-coverage:all:' || pairs.base || ':' || pairs.to_currency || ':' || day::date
        FROM bounds
        CROSS JOIN generate_series(bounds.first_date, bounds.last_date, interval '1 day') day
        CROSS JOIN pairs
        WHERE pairs.rate IS NOT NULL AND pairs.rate > 0
        ON CONFLICT (rate_date, base, to_currency, source, method,
                     (COALESCE(source_reference, ''))) DO UPDATE
            SET rate = EXCLUDED.rate,
                source = EXCLUDED.source,
                method = EXCLUDED.method,
                source_reference = EXCLUDED.source_reference
        """);
    currencyRateService.clearValuationResolutionCache();
  }

  private void loadGoldenFx() throws IOException {
    // The fast snapshot may already contain TEST rows. Replace them so this fixture is
    // deterministic and cannot inherit stale rates from an earlier snapshot.
    jdbc.update("DELETE FROM investory.exchange_rates WHERE source = 'TEST'");
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(
                resource("reference/exchange_rates.csv"), StandardCharsets.UTF_8))) {
      assertThat(reader.readLine()).isNotNull();
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) continue;
        String[] column = line.split(",", -1);
        LocalDate date = LocalDate.parse(column[0]);
        String base = column[1];
        String target = column[2];
        BigDecimal rate = new BigDecimal(column[3]);
        String reference = "GOLDEN:" + date + ":" + base + ":" + target;
        jdbc.update(
            "INSERT INTO investory.exchange_rates("
                + "rate_date, base, to_currency, rate, source, method, source_reference) "
                + "VALUES (?, ?, ?, ?, 'TEST', 'MARKET_DAILY', ?) "
                + "ON CONFLICT (rate_date, base, to_currency, source, method, "
                + "(COALESCE(source_reference, ''))) DO UPDATE SET rate = EXCLUDED.rate, "
                + "source = EXCLUDED.source, method = EXCLUDED.method, "
                + "source_reference = EXCLUDED.source_reference",
            date,
            base,
            target,
            rate,
            reference);
      }
    }
    jdbc.update(
        "INSERT INTO investory.exchange_rates("
            + "rate_date, base, to_currency, rate, source, method, source_reference) "
            + "SELECT current_date, latest.base, latest.to_currency, latest.rate, "
            + "'TEST', 'MARKET_DAILY', 'GOLDEN:current:' || latest.base || ':' || latest.to_currency "
            + "FROM (SELECT DISTINCT ON (base, to_currency) base, to_currency, rate "
            + "      FROM investory.exchange_rates "
            + "      ORDER BY base, to_currency, rate_date DESC, id DESC) latest "
            + "ON CONFLICT DO NOTHING");
    jdbc.update(
        "INSERT INTO investory.exchange_rates("
            + "rate_date, base, to_currency, rate, source, method, source_reference) "
            + "SELECT current_date - 1, latest.base, latest.to_currency, latest.rate, "
            + "'TEST', 'MARKET_DAILY', 'GOLDEN:previous:' || latest.base || ':' || latest.to_currency "
            + "FROM (SELECT DISTINCT ON (base, to_currency) base, to_currency, rate "
            + "      FROM investory.exchange_rates "
            + "      ORDER BY base, to_currency, rate_date DESC, id DESC) latest "
            + "ON CONFLICT DO NOTHING");
    jdbc.update(
        "INSERT INTO investory.exchange_rates("
            + "rate_date, base, to_currency, rate, source, method, source_reference) "
            + "SELECT current_date, 'EUR', 'PLN', eur_usd.rate * usd_pln.rate, "
            + "'TEST', 'MARKET_DAILY', 'GOLDEN:current:EUR:PLN' "
            + "FROM (SELECT rate FROM investory.exchange_rates WHERE base = 'EUR' "
            + "      AND to_currency = 'USD' ORDER BY rate_date DESC, id DESC LIMIT 1) eur_usd, "
            + "     (SELECT rate FROM investory.exchange_rates WHERE base = 'USD' "
            + "      AND to_currency = 'PLN' ORDER BY rate_date DESC, id DESC LIMIT 1) usd_pln "
            + "ON CONFLICT DO NOTHING");
    currencyRateService.clearValuationResolutionCache();
  }

  private void openDashboard(Page page) {
    Response response =
        page.navigate(baseUrl() + "/portfolios/" + PORTFOLIO_ID + "/dashboard?period=MAX");
    assertThat(response).isNotNull();
    assertThat(response.status()).isEqualTo(200);
  }

  private void runScenario(Scenario scenario) {
    var failures = new ArrayList<String>();
    try (BrowserContext context =
        browser.newContext(
            new Browser.NewContextOptions()
                .setHttpCredentials("admin", "change-me-admin")
                .setViewportSize(1440, 1000))) {
      context
          .tracing()
          .start(
              new Tracing.StartOptions().setScreenshots(true).setSnapshots(true).setSources(true));
      Page page = context.newPage();
      page.onPageError(error -> failures.add("page error: " + error));
      page.onConsoleMessage(
          message -> {
            if ("error".equals(message.type())) failures.add("console error: " + message.text());
          });
      page.onRequestFailed(
          request -> {
            if (request.url().startsWith(baseUrl()))
              failures.add("request failed: " + request.method() + " " + request.url());
          });
      try {
        scenario.run(page);
        assertThat(failures).isEmpty();
        context.tracing().stop();
      } catch (AssertionError | RuntimeException failure) {
        saveFailureArtifacts(page, context);
        throw failure;
      }
    }
  }

  private void saveFailureArtifacts(Page page, BrowserContext context) {
    try {
      Files.createDirectories(ARTIFACT_DIRECTORY);
      page.screenshot(
          new Page.ScreenshotOptions()
              .setPath(ARTIFACT_DIRECTORY.resolve("investment-dashboard-golden.png"))
              .setFullPage(true));
      Files.writeString(
          ARTIFACT_DIRECTORY.resolve("investment-dashboard-golden.html"), page.content());
      context
          .tracing()
          .stop(
              new Tracing.StopOptions()
                  .setPath(ARTIFACT_DIRECTORY.resolve("investment-dashboard-golden-trace.zip")));
    } catch (IOException | RuntimeException ignored) {
      // Preserve the original failure.
    }
  }

  private Locator overviewCard(Page page, String label) {
    return page.locator("#investment-overview .iv-overview-card")
        .filter(new Locator.FilterOptions().setHasText(label));
  }

  private String cardValue(Locator card) {
    return card.locator(":scope > .iv-kpi__value").textContent();
  }

  private Locator structureCard(Locator section, String label) {
    return section
        .locator(".iv-structure-card")
        .filter(new Locator.FilterOptions().setHasText(label));
  }

  private OpenPositionValue position(OverviewView overview, String symbol) {
    return overview.openPositionValues().stream()
        .filter(position -> symbol.equals(position.getSymbol()))
        .findFirst()
        .orElseThrow();
  }

  private void assertClose(Object expected, double actual) {
    assertThat(expected).isInstanceOf(Number.class);
    assertThat(actual).isCloseTo(((Number) expected).doubleValue(), within(0.01));
  }

  private void assertClose(double expected, double actual) {
    assertThat(actual).isCloseTo(expected, within(0.01));
  }

  private void assertClose(Object expected, BigDecimal actual) {
    assertThat(expected).isInstanceOf(Number.class);
    assertThat(actual.doubleValue()).isCloseTo(((Number) expected).doubleValue(), within(0.01));
  }

  private void assertClose(double expected, BigDecimal actual) {
    assertThat(actual.doubleValue()).isCloseTo(expected, within(0.01));
  }

  private static org.assertj.core.data.Offset<Double> within(double value) {
    return org.assertj.core.data.Offset.offset(value);
  }

  private String whole(double value) {
    NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
    formatter.setRoundingMode(RoundingMode.HALF_UP);
    formatter.setMinimumFractionDigits(0);
    formatter.setMaximumFractionDigits(0);
    return formatter.format(value);
  }

  private String whole(BigDecimal value) {
    return whole(value.doubleValue());
  }

  private String decimalComma(double value, int fractionDigits) {
    NumberFormat formatter = NumberFormat.getNumberInstance(Locale.GERMANY);
    formatter.setRoundingMode(RoundingMode.HALF_UP);
    formatter.setMinimumFractionDigits(fractionDigits);
    formatter.setMaximumFractionDigits(fractionDigits);
    return formatter.format(value);
  }

  private String decimalComma(BigDecimal value, int fractionDigits) {
    return decimalComma(value.doubleValue(), fractionDigits);
  }

  private double roundOne(double value) {
    return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + port;
  }

  private InputStream resource(String path) {
    InputStream input = getClass().getResourceAsStream(GOLDEN_ROOT + path);
    if (input == null) throw new IllegalStateException("Missing golden fixture: " + path);
    return input;
  }

  private InputStream goldenBinary(String path) throws IOException {
    Path moduleDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    Path checkout =
        moduleDirectory.getFileName().toString().equals("app")
            ? moduleDirectory.getParent()
            : moduleDirectory;
    Path fixture =
        checkout.resolve("test-support/src/main/resources/reconciliation/golden").resolve(path);
    if (!Files.isRegularFile(fixture))
      throw new IllegalStateException("Missing golden binary fixture: " + fixture);
    return Files.newInputStream(fixture);
  }

  @FunctionalInterface
  private interface Scenario {
    void run(Page page);
  }
}
