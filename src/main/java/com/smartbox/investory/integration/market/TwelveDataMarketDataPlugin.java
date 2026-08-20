package com.smartbox.investory.integration.market;

import com.smartbox.investory.integration.IntegrationType;
import com.smartbox.investory.integration.PluginConfig;
import com.smartbox.investory.integration.PluginDescriptor;
import com.smartbox.investory.integration.PluginFieldDescriptor;
import com.smartbox.investory.integration.ValidationResult;
import com.smartbox.investory.investment.accounting.model.StockQuote;
import com.smartbox.investory.investment.infrastructure.market.client.TwelveDataService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** TwelveData transport adapter. Market orchestration stays in {@code MarketService}. */
@Component
@RequiredArgsConstructor
public class TwelveDataMarketDataPlugin
    implements MarketDataPlugin, com.smartbox.investory.integration.TestableIntegrationPlugin {
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
  public Map<String, StockQuote> fetchQuotes(List<String> symbols, PluginConfig config) {
    String apiKey = config.value("apiKey").orElse("");
    return apiKey.isBlank()
        ? client.fetchStockQuotes(String.join(",", symbols))
        : client.fetchStockQuotes(String.join(",", symbols), apiKey);
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
  public com.smartbox.investory.integration.ConnectionTestResult testConnection(
      PluginConfig config) {
    try {
      fetchQuotes(List.of("SPY"), config);
      return new com.smartbox.investory.integration.ConnectionTestResult(
          true, true, "Connection succeeded");
    } catch (RuntimeException exception) {
      return new com.smartbox.investory.integration.ConnectionTestResult(
          true, false, "Connection test failed");
    }
  }
}
