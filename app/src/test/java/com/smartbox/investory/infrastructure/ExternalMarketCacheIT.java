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
    var jdbc = new JdbcTemplate(source);
    jdbc.update("DELETE FROM investory.external_market_cache");
    jdbc.update("DELETE FROM investory.external_market_attempt");
    jdbc.update("DELETE FROM investory.external_market_provider_state");
    store = new MarketDataStore(jdbc, new DataSourceTransactionManager(source));
  }

  private ExternalMarketDataService gateway(InvestoryRagClient client, boolean stale) {
    return new ExternalMarketDataService(store, client, "test", 20, true, stale);
  }

  private InvestoryRagClient.MarketData quote() {
    return new InvestoryRagClient.MarketData(
        "Alpha Vantage",
        "GLOBAL_QUOTE",
        "MSFT",
        "https://www.alphavantage.co/",
        "symbol,price\nMSFT,100");
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
}
