package com.smartbox.investory.integrations.infrastructure.integration.market;

import com.smartbox.investory.integrations.infrastructure.integration.IntegrationType;
import com.smartbox.investory.integrations.infrastructure.integration.PluginConfig;
import com.smartbox.investory.integrations.infrastructure.integration.PluginDescriptor;
import com.smartbox.investory.integrations.infrastructure.integration.PluginFieldDescriptor;
import com.smartbox.investory.integrations.infrastructure.integration.ValidationResult;
import com.smartbox.investory.integrations.market.client.TwelveDataService;
import com.smartbox.investory.investment.port.market.MarketQuote;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** TwelveData transport adapter. Market orchestration stays in {@code MarketService}. */
@Component
@RequiredArgsConstructor
public class TwelveDataMarketDataPlugin
    implements MarketDataPlugin,
        com.smartbox.investory.integrations.infrastructure.integration.TestableIntegrationPlugin {
  public static final String ID = "twelvedata";
  private static final Map<String, String> SYMBOL_OVERRIDES =
      Map.of("REMX.UK", "REMX", "VHYD.UK", "VHYDl:CBOE");
  private final TwelveDataService client;

  @Override
  public String id() {
    return ID;
  }

  @Override
  public IntegrationType type() {
    return IntegrationType.MARKET_DATA;
  }

  @Override
  public PluginDescriptor descriptor() {
    return new PluginDescriptor(
        ID,
        "Twelve Data",
        type(),
        List.of(PluginFieldDescriptor.requiredSecret("apiKey")),
        List.of("refresh-prices"));
  }

  @Override
  public ValidationResult validate(PluginConfig config) {
    return config.value("apiKey").filter(value -> !value.isBlank()).isPresent()
        ? ValidationResult.success()
        : ValidationResult.invalid("Missing required configuration: apiKey");
  }

  @Override
  public Map<String, MarketQuote> fetchQuotes(List<String> symbols, PluginConfig config) {
    String apiKey = config.value("apiKey").orElse("");
    return apiKey.isBlank()
        ? client.fetchMarketQuotes(String.join(",", symbols))
        : client.fetchMarketQuotes(String.join(",", symbols), apiKey);
  }

  @Override
  public String externalSymbol(String canonicalSymbol, String ticker) {
    String override = SYMBOL_OVERRIDES.get(canonicalSymbol);
    if (override != null) return override;
    if (ticker == null || ticker.isBlank() || canonicalSymbol == null) return ticker;
    if (canonicalSymbol.endsWith(".PL")) return ticker + ":GPW";
    if (canonicalSymbol.endsWith(".DE")) return ticker + ":XETR";
    if (canonicalSymbol.endsWith(".UK")) return ticker + ":LSE";
    return ticker;
  }

  @Override
  public com.smartbox.investory.integrations.infrastructure.integration.ConnectionTestResult
      testConnection(PluginConfig config) {
    try {
      fetchQuotes(List.of("SPY"), config);
      return new com.smartbox.investory.integrations.infrastructure.integration
          .ConnectionTestResult(true, true, "Connection succeeded");
    } catch (RuntimeException exception) {
      return new com.smartbox.investory.integrations.infrastructure.integration
          .ConnectionTestResult(true, false, "Connection test failed");
    }
  }
}
