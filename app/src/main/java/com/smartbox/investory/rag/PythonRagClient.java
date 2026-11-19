package com.smartbox.investory.rag;

import com.smartbox.investory.profile.api.contract.ProfileResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Narrow internal HTTP adapter. Never forwards browser credentials or user IDs. */
@Component
@ConditionalOnProperty(prefix = "investory.rag", name = "enabled", havingValue = "true")
public class PythonRagClient implements InvestoryRagClient {
  private final RestClient client;

  public PythonRagClient(
      RestClient.Builder builder,
      @Value("${investory.rag.url:http://127.0.0.1:8090}") String url,
      @Value("${investory.rag.service-token:}") String token) {
    if (token == null || token.isBlank()) {
      throw new IllegalStateException("RAG enabled but service token is missing");
    }
    // Explicit timeout avoids tying up Java request threads when Python/OpenAI is slow.
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(3));
    factory.setReadTimeout(Duration.ofSeconds(35));
    this.client =
        builder
            .clone()
            .baseUrl(url)
            .requestFactory(factory)
            .defaultHeader("Authorization", "Bearer " + token)
            .build();
  }

  @Override
  public RagAnswer ask(String question, ProfileResponse snapshot) {
    // Explicit allowlist of top-level calculated facts; do not export
    // allocations, accounts, transactions, users, or personal asset details.
    ProfileFacts facts =
        new ProfileFacts(
            snapshot.currency().name(),
            snapshot.marketPortfolioValue(),
            snapshot.longTermAssetValue(),
            snapshot.totalNetWorth(),
            snapshot.liquidAssets(),
            snapshot.illiquidAssets());
    return post("/ask-with-facts", Map.of("question", question, "facts", facts));
  }

  @Override
  public MarketData marketData(String tool, String symbol) {
    try {
      MarketData result =
          client
              .post()
              .uri("/market-data")
              .contentType(MediaType.APPLICATION_JSON)
              // Explicit allowlist: only public ticker symbols reach the external provider.
              .body(Map.of("tool", tool, "symbol", symbol))
              .retrieve()
              .body(MarketData.class);
      if (result == null
          || !"Alpha Vantage".equals(result.provider())
          || !tool.equals(result.tool())
          || !symbol.equals(result.symbol())
          || !"https://www.alphavantage.co/".equals(result.source())
          || result.data() == null
          || result.data().isBlank()
          || result.data().length() > 16384) {
        throw new RagUnavailableException();
      }
      return result;
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().value() == 422) throw new MarketDataUnavailableException();
      if (e.getStatusCode().value() == 429) throw new MarketProviderRateLimitedException();
      if (e.getStatusCode().value() == 424) throw new MarketDataNotEntitledException();
      if (e.getStatusCode().value() == 503) throw new MarketResearchUnavailableException();
      throw new RagUnavailableException();
    } catch (RestClientException e) {
      // Provider errors may contain sensitive headers; do not forward them.
      throw new RagUnavailableException();
    }
  }

  @Override
  public Documentation searchDocumentation(String question, int limit) {
    if (question == null
        || question.isBlank()
        || question.length() > 2000
        || limit < 1
        || limit > 8) {
      throw new IllegalArgumentException("Invalid documentation search");
    }
    try {
      var result =
          client
              .post()
              .uri("/search")
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("question", question, "limit", limit))
              .retrieve()
              .body(Documentation.class);
      if (result == null
          || result.matches() == null
          || result.matches().size() > limit
          || result.matches().stream()
              .anyMatch(
                  m ->
                      m == null
                          || m.source() == null
                          || m.source().isBlank()
                          || m.text() == null
                          || m.text().length() > 20000
                          || !Double.isFinite(m.score()))) {
        throw new RagUnavailableException();
      }
      return result;
    } catch (RestClientException e) {
      throw new RagUnavailableException();
    }
  }

  @Override
  public RoutingPlan planRouting(String question) {
    if (question == null || question.isBlank() || question.length() > 2000) {
      throw new IllegalArgumentException("Invalid routing question");
    }
    try {
      RoutingPlan plan = client.post().uri("/route")
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("question", question))
          .retrieve().body(RoutingPlan.class);
      if (plan == null || plan.tools() == null || plan.tools().size() > 5
          || plan.tools().stream().anyMatch(tool -> tool == null
              || !java.util.Set.of("PROFILE", "HOLDINGS", "SPY", "RETIREMENT", "DOCS", "MARKET")
                  .contains(tool))
          || (plan.symbol() != null
              && !plan.symbol().matches("[A-Z][A-Z0-9.^-]{0,19}"))) {
        throw new RagUnavailableException();
      }
      return plan;
    } catch (RestClientException e) {
      throw new RagUnavailableException();
    }
  }

  @Override
  public RagAnswer askDocumentation(String question) {
    return post("/ask", Map.of("question", question));
  }

  private RagAnswer post(String path, Map<String, ?> payload) {
    try {
      RagAnswer result =
          client
              .post()
              .uri(path)
              .contentType(MediaType.APPLICATION_JSON)
              .body(payload)
              .retrieve()
              .body(RagAnswer.class);
      if (result == null
          || result.answer() == null
          || result.answer().isBlank()
          || result.sources() == null
          || result.sources().stream().anyMatch(source -> source == null || source.isBlank())) {
        throw new RagUnavailableException();
      }
      return result;
    } catch (RestClientException e) {
      // Deliberately do not expose upstream error body/URL or token.
      throw new RagUnavailableException();
    }
  }

  record ProfileFacts(
      String currency,
      BigDecimal marketPortfolioValue,
      BigDecimal longTermAssetValue,
      BigDecimal totalNetWorth,
      BigDecimal liquidAssets,
      BigDecimal illiquidAssets) {}

  public static final class MarketDataUnavailableException extends RuntimeException {}

  public static final class MarketProviderRateLimitedException extends RuntimeException {}

  public static final class MarketDataNotEntitledException extends RuntimeException {}

  public static final class MarketResearchUnavailableException extends RuntimeException {}

  public static final class RagUnavailableException extends RuntimeException {}
}
