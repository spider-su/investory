package com.smartbox.investory.integrations.market.twelvedata;

import com.smartbox.investory.integrations.management.api.model.ConnectionTestResult;
import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.api.model.PluginFieldDescriptor;
import com.smartbox.investory.integrations.management.api.model.PluginFieldType;
import com.smartbox.investory.integrations.management.model.PluginConfig;
import com.smartbox.investory.integrations.management.model.PluginDescriptor;
import com.smartbox.investory.integrations.management.model.ValidationResult;
import com.smartbox.investory.integrations.market.spi.MarketDataPlugin;
import com.smartbox.investory.investment.port.market.MarketQuote;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** TwelveData transport adapter. Market orchestration stays in {@code MarketDataService}. */
@Component
@RequiredArgsConstructor
public class TwelveDataMarketDataPlugin
    implements MarketDataPlugin,
        com.smartbox.investory.integrations.management.spi.TestableIntegrationPlugin {
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
        List.of(
            PluginFieldDescriptor.requiredSecret("apiKey"),
            new PluginFieldDescriptor(
                "baseUrl",
                PluginFieldType.URL,
                false,
                "https://api.twelvedata.com",
                List.of(),
                "API URL",
                "Twelve Data API base URL",
                null,
                null,
                null)),
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
    return config.value("baseUrl").isPresent()
        ? client.fetchMarketQuotes(
            String.join(",", symbols), apiKey, config.value("baseUrl").orElseThrow())
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
  public ConnectionTestResult testConnection(PluginConfig config) {
    try {
      fetchQuotes(List.of("SPY"), config);
      return new ConnectionTestResult(true, true, "Connection succeeded");
    } catch (RuntimeException exception) {
      return new ConnectionTestResult(true, false, "Connection test failed");
    }
  }
}
