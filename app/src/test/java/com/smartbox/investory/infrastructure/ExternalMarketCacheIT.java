package com.smartbox.investory.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.smartbox.investory.rag.*;
import com.smartbox.investory.testsupport.WorkerDatabase;
import java.time.Instant;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ExternalMarketCacheIT {
  private static final WorkerDatabase DATABASE =
      MigrationTestDatabase.open("external_market_cache");
  private static DriverManagerDataSource source;
  private MarketDataStore store;
  private io.micrometer.core.instrument.simple.SimpleMeterRegistry meters;

  @BeforeAll
  static void migrate() {
    MigrationTestDatabase.migrate(DATABASE);
    source =
        new DriverManagerDataSource(DATABASE.jdbcUrl(), DATABASE.username(), DATABASE.password());
  }

  @AfterAll
  static void close() {
    DATABASE.close();
  }

  @BeforeEach
  void prepare() {
    meters = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
    var jdbc = new JdbcTemplate(source);
    jdbc.update("DELETE FROM investory.external_market_cache");
    jdbc.update("DELETE FROM investory.external_market_attempt");
    jdbc.update("DELETE FROM investory.external_market_provider_state");
    store = new MarketDataStore(jdbc, new DataSourceTransactionManager(source));
  }

  private ExternalMarketDataService gateway(InvestoryRagClient client, boolean stale) {
    return new ExternalMarketDataService(
        store, new AssistantMetrics(meters), client, "test", 20, true, stale);
  }

  private InvestoryRagClient.MarketData quote() {
    return new InvestoryRagClient.MarketData(
        "Alpha Vantage",
        "GLOBAL_QUOTE",
        "MSFT",
        "https://www.alphavantage.co/",
        "symbol,price,latestDay\nMSFT,100,2026-11-13");
  }

  @Test
  void budgetIsAtomicAndSurvivesNewStoreInstance() throws Exception {
    var now = Instant.now();
    try (var executor = Executors.newFixedThreadPool(8)) {
      var tasks = new java.util.ArrayList<Future<Boolean>>();
      for (int i = 0; i < 40; i++) tasks.add(executor.submit(() -> store.reserve("test", 20, now)));
      int accepted = 0;
      for (var task : tasks) if (task.get()) accepted++;
      assertEquals(20, accepted);
    }
    var restarted =
        new MarketDataStore(new JdbcTemplate(source), new DataSourceTransactionManager(source));
    assertEquals(0, restarted.remaining("test", 20, now));
    assertFalse(restarted.reserve("test", 20, now));
    assertTrue(restarted.reserve("test", 20, now.plusSeconds(86401)));
  }

  @Test
  void identicalConcurrentRequestsCoalesceAndCachePersists() throws Exception {
    var client = mock(InvestoryRagClient.class);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    when(client.marketData("GLOBAL_QUOTE", "MSFT"))
        .thenAnswer(
            a -> {
              entered.countDown();
              assertTrue(release.await(10, TimeUnit.SECONDS));
              return quote();
            });
    var service = gateway(client, false);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> service.lookup("GLOBAL_QUOTE", "MSFT"));
      assertTrue(entered.await(10, TimeUnit.SECONDS));
      var second = executor.submit(() -> service.lookup("GLOBAL_QUOTE", "MSFT"));
      assertThrows(
          ExternalMarketDataService.BusyException.class,
          () -> service.lookup("GLOBAL_QUOTE", "AAPL"));
      release.countDown();
      assertEquals("FRESH", first.get().cache().status());
      assertEquals(quote().data(), second.get().data());
    }
    assertEquals("CACHED", gateway(client, false).lookup("GLOBAL_QUOTE", "MSFT").cache().status());
    assertEquals(19, service.remaining());
    assertEquals(
        1, meters.find("investory.market.events").tag("event", "ATTEMPT").counter().count());
    assertTrue(
        meters.find("investory.market.events").tag("event", "CACHE_HIT").counter().count() >= 1);
    assertEquals(
        1,
        meters
            .find("investory.market.provider")
            .tag("outcome", "PROVIDER_SUCCESS")
            .timer()
            .count());
    verify(client, times(1)).marketData("GLOBAL_QUOTE", "MSFT");
  }

  @Test
  void entitlementAndProviderQuotaNeverRetryOrCacheErrorsAsData() {
    var client = mock(InvestoryRagClient.class);
    var service = gateway(client, false);
    when(client.marketData("COMPANY_OVERVIEW", "MSFT"))
        .thenThrow(new PythonRagClient.MarketDataNotEntitledException());
    for (int i = 0; i < 2; i++)
      assertThrows(
          PythonRagClient.MarketDataNotEntitledException.class,
          () -> service.lookup("COMPANY_OVERVIEW", "MSFT"));
    assertNull(store.find("test", "COMPANY_OVERVIEW", "MSFT").data());
    verify(client, times(1)).marketData("COMPANY_OVERVIEW", "MSFT");
    when(client.marketData("GLOBAL_QUOTE", "MSFT"))
        .thenThrow(new PythonRagClient.MarketProviderRateLimitedException());
    assertThrows(
        ExternalMarketDataService.BudgetExceededException.class,
        () -> service.lookup("GLOBAL_QUOTE", "MSFT"));
    assertThrows(
        ExternalMarketDataService.BudgetExceededException.class,
        () -> service.lookup("GLOBAL_QUOTE", "AAPL"));
    verify(client, never()).marketData("GLOBAL_QUOTE", "AAPL");
    assertEquals(18, service.remaining());
  }

  @Test
  void expiredDataRequiresOptInAndIsExplicitlyStale() {
    var now = Instant.now();
    store.save(
        "test",
        "GLOBAL_QUOTE",
        "MSFT",
        quote().data(),
        now.minusSeconds(90000),
        now.minusSeconds(3600));
    store.blockProvider("test", now.plusSeconds(3600));
    var client = mock(InvestoryRagClient.class);
    assertThrows(
        ExternalMarketDataService.BudgetExceededException.class,
        () -> gateway(client, false).lookup("GLOBAL_QUOTE", "MSFT"));
    var result = gateway(client, true).lookup("GLOBAL_QUOTE", "MSFT");
    assertEquals("STALE", result.cache().status());
    assertFalse(result.cache().warnings().isEmpty());
    verifyNoInteractions(client);
  }

  @Test
  void quoteDatesComeFromProviderAndTransportCooldownDoesNotBurnBudget() {
    var client = mock(InvestoryRagClient.class);
    var service = gateway(client, false);
    when(client.marketData("GLOBAL_QUOTE", "MSFT"))
        .thenReturn(
            new InvestoryRagClient.MarketData(
                "Alpha Vantage",
                "GLOBAL_QUOTE",
                "MSFT",
                "https://www.alphavantage.co/",
                "symbol,price,latestDay\nMSFT,100,2026-11-13\n"));
    assertEquals(
        java.time.LocalDate.of(2026, 11, 13),
        service.lookup("GLOBAL_QUOTE", "MSFT").cache().asOf());
    when(client.marketData("GLOBAL_QUOTE", "AAPL"))
        .thenThrow(new PythonRagClient.RagUnavailableException());
    for (int n = 0; n < 2; n++)
      assertThrows(
          PythonRagClient.RagUnavailableException.class,
          () -> service.lookup("GLOBAL_QUOTE", "AAPL"));
    verify(client, times(1)).marketData("GLOBAL_QUOTE", "AAPL");
    assertEquals(18, service.remaining());
  }

  @Test
  void absentProviderCoverageIsCachedWithoutInventingAQuote() {
    var client = mock(InvestoryRagClient.class);
    var service = gateway(client, false);
    when(client.marketData("GLOBAL_QUOTE", "UNLISTED"))
        .thenThrow(new PythonRagClient.MarketDataUnavailableException());
    for (int n = 0; n < 2; n++)
      assertThrows(
          PythonRagClient.MarketDataUnavailableException.class,
          () -> service.lookup("GLOBAL_QUOTE", "UNLISTED"));
    verify(client, times(1)).marketData("GLOBAL_QUOTE", "UNLISTED");
    assertEquals("NO_DATA", store.find("test", "GLOBAL_QUOTE", "UNLISTED").reason());
    assertNull(store.find("test", "GLOBAL_QUOTE", "UNLISTED").data());
    assertEquals(19, service.remaining());
  }

  @Test
  void verifiedListingSharesExactProviderCacheAndUnmappedHoldingSkipsQuota() {
    var client = mock(InvestoryRagClient.class);
    when(client.marketData("GLOBAL_QUOTE", "MSFT")).thenReturn(quote());
    var gateway = gateway(client, false);
    var result = gateway.lookupHolding("GLOBAL_QUOTE", "MSFT.US");
    assertEquals(
        new InvestoryRagClient.Quote("100", "USD", java.time.LocalDate.of(2026, 11, 13)),
        result.quote());
    assertEquals("CACHED", gateway.lookup("GLOBAL_QUOTE", "MSFT").cache().status());
    assertEquals("CACHED", gateway.quoteStatus("MSFT.US", true).cacheStatus());
    assertThrows(
        PythonRagClient.MarketDataUnavailableException.class,
        () -> gateway.lookupHolding("GLOBAL_QUOTE", "XYZ.LON"));
    assertEquals("UNMAPPED", gateway.quoteStatus("XYZ.LON", true).coverage());
    assertEquals(19, gateway.remaining());
    verify(client, times(1)).marketData("GLOBAL_QUOTE", "MSFT");
    verifyNoMoreInteractions(client);
  }

  @Test
  void statusReadsDurableCooldownAndBudgetWithoutCallingProvider() {
    var client = mock(InvestoryRagClient.class);
    var gateway = gateway(client, false);
    var now = Instant.now();
    store.block("test", "GLOBAL_QUOTE", "MSFT", now.plusSeconds(600), "NO_DATA");
    for (int i = 0; i < 20; i++) assertTrue(store.reserve("test", 20, now));
    store.blockProvider("test", now.plusSeconds(1200));
    var status = gateway.quoteStatus("MSFT.US", true);
    assertEquals("NO_DATA", status.coverage());
    assertNotNull(status.symbolCooldownUntil());
    assertNotNull(status.providerCooldownUntil());
    assertNotNull(status.budgetAvailableAt());
    assertEquals(0, status.remainingBudget());
    verifyNoInteractions(client);
  }

  @Test
  void mismatchedProviderListingCannotPopulateCache() {
    var client = mock(InvestoryRagClient.class);
    when(client.marketData("GLOBAL_QUOTE", "AAPL")).thenReturn(quote());
    var gateway = gateway(client, false);
    assertThrows(
        PythonRagClient.RagUnavailableException.class,
        () -> gateway.lookup("GLOBAL_QUOTE", "AAPL"));
    assertNull(store.find("test", "GLOBAL_QUOTE", "AAPL").data());
    assertEquals("PROVIDER", gateway.quoteStatus("AAPL", false).coverage());
    assertEquals(19, gateway.remaining());
  }

  @Test
  void legacyJsonQuoteCanBeReadWithoutGuessingCurrencyOrSpendingQuota() {
    var client = mock(InvestoryRagClient.class);
    var now = Instant.now();
    store.save(
        "test",
        "GLOBAL_QUOTE",
        "ACME.LON",
        """
        {"Global Quote":{"01. symbol":"ACME.LON","05. price":"12.3400",
        "07. latest trading day":"2026-11-13"}}
        """,
        now,
        now.plusSeconds(600));
    var service = gateway(client, false);
    var result = service.lookup("GLOBAL_QUOTE", "ACME.LON");
    assertEquals("12.3400", result.quote().price());
    assertNull(result.quote().currency());
    assertEquals(java.time.LocalDate.of(2026, 11, 13), result.quote().tradingDate());
    assertEquals("CACHED", result.cache().status());
    assertEquals(20, service.remaining());
    verifyNoInteractions(client);
  }
}
