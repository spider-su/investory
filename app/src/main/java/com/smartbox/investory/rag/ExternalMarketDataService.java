package com.smartbox.investory.rag;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** One gateway for REST and own MCP. Cache hits never call the external provider. */
@Service
@ConditionalOnProperty(prefix = "investory.rag", name = "enabled", havingValue = "true")
public class ExternalMarketDataService {
  private final MarketDataStore store;
  private final InvestoryRagClient client;
  private final String profile;
  private final int limit;
  private final boolean enabled;
  private final boolean allowStale;
  private final Semaphore external = new Semaphore(1);
  private final ConcurrentHashMap<String, CompletableFuture<InvestoryRagClient.MarketData>>
      pending = new ConcurrentHashMap<>();
  private static final Set<String> TOOLS =
      Set.of(
          "GLOBAL_QUOTE", "COMPANY_OVERVIEW", "INCOME_STATEMENT", "EARNINGS", "EARNINGS_CALENDAR");

  public ExternalMarketDataService(
      MarketDataStore store,
      InvestoryRagClient client,
      @Value("${investory.rag.market.profile:alpha-vantage-free-v1}") String profile,
      @Value("${investory.rag.market.daily-budget:20}") int limit,
      @Value("${investory.rag.market.enabled:false}") boolean enabled,
      @Value("${investory.rag.market.allow-stale:false}") boolean allowStale) {
    if (limit < 1 || limit > 20 || !profile.matches("[a-zA-Z0-9_-]{1,100}"))
      throw new IllegalArgumentException("Invalid market policy");
    this.store = store;
    this.client = client;
    this.profile = profile;
    this.limit = limit;
    this.enabled = enabled;
    this.allowStale = allowStale;
  }

  public boolean enabled() {
    return enabled;
  }

  public int budget() {
    return limit;
  }

  public int remaining() {
    return store.remaining(profile, limit, Instant.now());
  }

  public static class BudgetExceededException extends RuntimeException {}

  public static class BusyException extends RuntimeException {}

  public InvestoryRagClient.MarketData lookup(String tool, String symbol) {
    if (!TOOLS.contains(tool) || symbol == null || !symbol.matches("[A-Z0-9][A-Z0-9.^-]{0,19}"))
      throw new IllegalArgumentException("Invalid public market request");
    if (!enabled) throw new PythonRagClient.MarketResearchUnavailableException();
    var key = tool + ":" + symbol;
    var promise = new CompletableFuture<InvestoryRagClient.MarketData>();
    var existing = pending.putIfAbsent(key, promise);
    if (existing != null) {
      try {
        return existing.join();
      } catch (CompletionException e) {
        throw (RuntimeException) e.getCause();
      }
    }
    try {
      var result = load(tool, symbol);
      promise.complete(result);
      return result;
    } catch (RuntimeException e) {
      promise.completeExceptionally(e);
      throw e;
    } finally {
      pending.remove(key, promise);
    }
  }

  private InvestoryRagClient.MarketData load(String tool, String symbol) {
    var now = Instant.now();
    var cached = store.find(profile, tool, symbol);
    if (cached != null && cached.data() != null && cached.expiresAt().isAfter(now))
      return result(tool, symbol, cached, "CACHED", List.of());
    if (cached != null && cached.blockedUntil() != null && cached.blockedUntil().isAfter(now)) {
      if ("NO_DATA".equals(cached.reason()))
        throw new PythonRagClient.MarketDataUnavailableException();
      if ("ENTITLEMENT".equals(cached.reason()))
        throw new PythonRagClient.MarketDataNotEntitledException();
      return staleOrThrow(tool, symbol, cached, new PythonRagClient.RagUnavailableException());
    }
    if (!external.tryAcquire()) throw new BusyException();
    try {
      if (!store.reserve(profile, limit, now))
        return staleOrThrow(tool, symbol, cached, new BudgetExceededException());
      try {
        var raw = client.marketData(tool, symbol);
        var expires = now.plusSeconds(86400);
        store.save(profile, tool, symbol, raw.data(), now, expires);
        return result(
            tool,
            symbol,
            new MarketDataStore.Entry(raw.data(), now, expires, null, null),
            "FRESH",
            List.of());
      } catch (PythonRagClient.MarketDataUnavailableException e) {
        store.block(profile, tool, symbol, now.plusSeconds(86400), "NO_DATA");
        throw e;
      } catch (PythonRagClient.MarketDataNotEntitledException e) {
        store.block(profile, tool, symbol, now.plusSeconds(86400), "ENTITLEMENT");
        throw e;
      } catch (PythonRagClient.MarketProviderRateLimitedException e) {
        store.blockProvider(profile, now.plusSeconds(86400));
        return staleOrThrow(tool, symbol, cached, new BudgetExceededException());
      } catch (PythonRagClient.RagUnavailableException e) {
        store.block(profile, tool, symbol, now.plusSeconds(300), "PROVIDER");
        return staleOrThrow(tool, symbol, cached, e);
      }
    } finally {
      external.release();
    }
  }

  private InvestoryRagClient.MarketData staleOrThrow(
      String tool, String symbol, MarketDataStore.Entry entry, RuntimeException error) {
    if (allowStale
        && entry != null
        && entry.data() != null
        && entry.fetchedAt().plusSeconds(3 * 86400).isAfter(Instant.now()))
      return result(
          tool,
          symbol,
          entry,
          "STALE",
          List.of(
              "Provider unavailable or budget exhausted; expired public data. Not suitable for trading."));
    throw error;
  }

  private InvestoryRagClient.MarketData result(
      String tool,
      String symbol,
      MarketDataStore.Entry entry,
      String status,
      List<String> warnings) {
    return new InvestoryRagClient.MarketData(
        "Alpha Vantage",
        tool,
        symbol,
        "https://www.alphavantage.co/",
        entry.data(),
        new InvestoryRagClient.CacheMetadata(
            status,
            entry.fetchedAt(),
            entry.expiresAt(),
            asOf(entry.data()),
            remaining(),
            warnings));
  }

  /** Only provider-reported trading dates qualify; fetch time is never a price date. */
  private static java.time.LocalDate asOf(String data) {
    try {
      if (data.stripLeading().startsWith("{")) {
        var quote = new tools.jackson.databind.ObjectMapper().readTree(data).path("Global Quote");
        var date = quote.path("07. latest trading day").asString();
        return java.time.LocalDate.parse(date);
      }
      var lines = data.strip().split("\\R");
      if (lines.length != 2) return null;
      var headers = lines[0].split(",", -1);
      var values = lines[1].split(",", -1);
      if (headers.length != values.length) return null;
      for (int i = 0; i < headers.length; i++) {
        if (headers[i].strip().replace("\"", "").equalsIgnoreCase("latestDay"))
          return java.time.LocalDate.parse(values[i].strip().replace("\"", ""));
      }
    } catch (RuntimeException ignored) {
      /* Unknown provider shape: date stays unknown. */
    }
    return null;
  }
}
