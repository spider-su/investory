package com.smartbox.investory.rag;

import com.smartbox.investory.profile.api.contract.ProfileResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
    this.client = builder.clone().baseUrl(url).requestFactory(factory)
        .defaultHeader("Authorization", "Bearer " + token).build();
  }

  @Override
  public RagAnswer ask(String question, ProfileResponse snapshot) {
    // Explicit allowlist of top-level calculated facts; do not export
    // allocations, accounts, transactions, users, or personal asset details.
    Map<String, Object> facts = Map.of(
        "currency", snapshot.currency().name(),
        "marketPortfolioValue", snapshot.marketPortfolioValue(),
        "longTermAssetValue", snapshot.longTermAssetValue(),
        "totalNetWorth", snapshot.totalNetWorth(),
        "liquidAssets", snapshot.liquidAssets(),
        "illiquidAssets", snapshot.illiquidAssets());
    try {
      RagAnswer result = client.post().uri("/ask-with-facts")
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("question", question, "facts", facts))
          .retrieve().body(RagAnswer.class);
      if (result == null) throw new IllegalStateException("Empty RAG response");
      return result;
    } catch (RestClientException e) {
      // Deliberately do not expose upstream error body/URL or token.
      throw new RagUnavailableException();
    }
  }

  public static final class RagUnavailableException extends RuntimeException {}
}
