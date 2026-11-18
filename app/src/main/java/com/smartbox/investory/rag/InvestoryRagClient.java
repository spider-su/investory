package com.smartbox.investory.rag;

import com.smartbox.investory.profile.api.contract.ProfileResponse;
import java.util.List;

/** Outbound adapter contract: Java is responsible for authorization and financial truth. */
public interface InvestoryRagClient {
  RagAnswer ask(String question, ProfileResponse snapshot);

  /** Documentation-only question; no portfolio snapshot is forwarded. */
  RagAnswer askDocumentation(String question);

  /** Public ticker research only; portfolio or user details must never cross this boundary. */
  MarketData marketData(String tool, String symbol);

  /** Retrieval-only public documentation. Never forwards a financial snapshot. */
  Documentation searchDocumentation(String question, int limit);

  record Snippet(String source, String text, double score) {}

  record Documentation(List<Snippet> matches) {}

  record CacheMetadata(
      String status,
      java.time.Instant fetchedAt,
      java.time.Instant expiresAt,
      java.time.LocalDate asOf,
      int remainingBudget,
      List<String> warnings) {}

  record Quote(String price, String currency, java.time.LocalDate tradingDate) {}

  record MarketStatus(
      String providerSymbol,
      String coverage,
      String cacheStatus,
      java.time.Instant symbolCooldownUntil,
      java.time.Instant providerCooldownUntil,
      java.time.Instant budgetAvailableAt,
      int remainingBudget,
      int budgetLimit) {}

  record MarketData(
      String provider,
      String tool,
      String symbol,
      String source,
      String data,
      CacheMetadata cache,
      Quote quote) {
    public MarketData(
        String provider,
        String tool,
        String symbol,
        String source,
        String data,
        CacheMetadata cache) {
      this(provider, tool, symbol, source, data, cache, null);
    }

    public MarketData withoutRawData() {
      return new MarketData(provider, tool, symbol, source, null, cache, quote);
    }

    public MarketData(String provider, String tool, String symbol, String source, String data) {
      this(provider, tool, symbol, source, data, null);
    }
  }

  record RagAnswer(String answer, List<String> sources) {}
}
