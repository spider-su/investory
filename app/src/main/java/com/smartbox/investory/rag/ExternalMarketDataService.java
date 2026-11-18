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

  public InvestoryRagClient.MarketData lookupHolding(String tool, String symbol) {
    validate(tool, symbol);
    var listing = AlphaVantageQuotes.holding(symbol);
    if (listing == null) throw new PythonRagClient.MarketDataUnavailableException();
    return lookup(tool, listing.providerSymbol());
  }

  public InvestoryRagClient.MarketStatus quoteStatus(String symbol, boolean holding) {
    validate("GLOBAL_QUOTE", symbol);
    var listing = AlphaVantageQuotes.holding(symbol);
    if (holding && listing == null)
      return new InvestoryRagClient.MarketStatus(
          null, "UNMAPPED", "MISS", null, null, null, enabled ? remaining() : 0, limit);
    String providerSymbol = AlphaVantageQuotes.providerSymbol(symbol);
    var now = Instant.now();
    if (!enabled)
      return new InvestoryRagClient.MarketStatus(
          providerSymbol, "DISABLED", "MISS", null, null, null, 0, limit);
    var entry = store.find(profile, "GLOBAL_QUOTE", providerSymbol);
    Instant symbolCooldown =
        entry == null || entry.blockedUntil() == null || !entry.blockedUntil().isAfter(now)
            ? null
            : entry.blockedUntil();
    boolean usable =
        entry != null && entry.data() != null && validCachedQuote(entry, providerSymbol);
    String coverage = symbolCooldown != null ? entry.reason() : usable ? "AVAILABLE" : "UNKNOWN";
    String cacheStatus = usable ? (entry.expiresAt().isAfter(now) ? "CACHED" : "EXPIRED") : "MISS";
    return new InvestoryRagClient.MarketStatus(
        providerSymbol,
        coverage,
        cacheStatus,
        symbolCooldown,
        store.providerCooldown(profile, now),
        store.budgetAvailableAt(profile, limit, now),
        store.remaining(profile, limit, now),
        limit);
  }

  private static boolean validCachedQuote(MarketDataStore.Entry entry, String symbol) {
    try {
      AlphaVantageQuotes.parse(entry.data(), symbol);
      return true;
    } catch (PythonRagClient.RagUnavailableException malformed) {
      return false;
    }
  }

  private static void validate(String tool, String symbol) {
    if (tool == null
        || !TOOLS.contains(tool)
        || symbol == null
        || !symbol.matches("[A-Z0-9][A-Z0-9.^-]{0,19}"))
      throw new IllegalArgumentException("Invalid public market request");
  }

  public InvestoryRagClient.MarketData lookup(String tool, String symbol) {
    validate(tool, symbol);
    symbol = AlphaVantageQuotes.providerSymbol(symbol);
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
    if (cached != null
        && cached.data() != null
        && cached.expiresAt().isAfter(now)
        && (!"GLOBAL_QUOTE".equals(tool) || validCachedQuote(cached, symbol)))
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
        if ("GLOBAL_QUOTE".equals(tool)) AlphaVantageQuotes.parse(raw.data(), symbol);
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
        && (!"GLOBAL_QUOTE".equals(tool) || validCachedQuote(entry, symbol))
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
    var quote = "GLOBAL_QUOTE".equals(tool) ? AlphaVantageQuotes.parse(entry.data(), symbol) : null;
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
            quote == null ? null : quote.tradingDate(),
            remaining(),
            warnings),
        quote);
  }
}
