package com.smartbox.investory.rag;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** Fixed metric dimensions. Observations carry no request content or identifiers. */
@Component
public class AssistantMetrics {
  private static final Set<String> ROUTES =
      Set.of(
          "JAVA_PROFILE",
          "JAVA_BENCHMARK",
          "JAVA_HOLDINGS",
          "JAVA_LONGTERM",
          "JAVA_RETIREMENT",
          "JAVA_PROFITABILITY",
          "JAVA_ASSET",
          "RAG_DOCUMENTATION",
          "RAG_PROFILE",
          "MARKET_MCP",
          "ABSTAINED");
  private final MeterRegistry registry;

  public AssistantMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  enum MarketEvent {
    CACHE_HIT,
    CACHE_MISS,
    STALE_HIT,
    ATTEMPT,
    BUDGET_EXHAUSTED,
    PROVIDER_COOLDOWN,
    SYMBOL_COOLDOWN,
    BUSY,
    UNMAPPED,
    DISABLED,
    PROVIDER_NO_DATA,
    PROVIDER_ENTITLEMENT,
    PROVIDER_RATE_LIMIT,
    PROVIDER_FAILURE,
    PROVIDER_SUCCESS
  }

  enum RequestOutcome {
    ANSWERED,
    ABSTAINED,
    INVALID,
    DENIED,
    LIMITED,
    ERROR
  }

  void routing(List<String> components) {
    for (String component : components) {
      registry
          .counter(
              "investory.assistant.routes",
              "route",
              ROUTES.contains(component) ? component : "OTHER")
          .increment();
    }
  }

  void request(RequestOutcome outcome, long started) {
    registry
        .timer("investory.assistant.request", "outcome", outcome.name())
        .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
  }

  void market(MarketEvent event) {
    registry.counter("investory.market.events", "event", event.name()).increment();
  }

  void provider(MarketEvent event, long started) {
    market(event);
    registry
        .timer("investory.market.provider", "outcome", event.name())
        .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
  }
}
