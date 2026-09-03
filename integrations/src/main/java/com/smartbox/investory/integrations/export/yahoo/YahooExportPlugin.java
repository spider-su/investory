package com.smartbox.investory.integrations.export.yahoo;

import com.smartbox.investory.integrations.export.spi.ExportPlugin;
import com.smartbox.investory.integrations.management.api.model.ConnectionTestResult;
import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.api.model.PluginFieldDescriptor;
import com.smartbox.investory.integrations.management.api.model.PluginFieldType;
import com.smartbox.investory.integrations.management.model.PluginConfig;
import com.smartbox.investory.integrations.management.model.PluginDescriptor;
import com.smartbox.investory.integrations.management.model.ValidationResult;
import com.smartbox.investory.integrations.management.spi.TestableIntegrationPlugin;
import com.smartbox.investory.integrations.market.yahoo.YahooFinanceService;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class YahooExportPlugin implements ExportPlugin, TestableIntegrationPlugin {
  public static final String ID = "yahoo-finance";
  private static final String DEFAULT_BASE_URL =
      "https://query1.finance.yahoo.com/v8/finance/chart/";
  private final YahooExportService service;
  private final YahooFinanceService marketDataService;

  @Override
  public String id() {
    return ID;
  }

  @Override
  public IntegrationType type() {
    return IntegrationType.EXPORT;
  }

  @Override
  public PluginDescriptor descriptor() {
    return new PluginDescriptor(
        ID,
        "Yahoo Finance",
        type(),
        List.of(
            new PluginFieldDescriptor(
                "portfolioId",
                PluginFieldType.INTEGER,
                true,
                null,
                List.of(),
                "Portfolio ID",
                "Investory portfolio used for Yahoo CSV exports",
                null,
                null,
                null),
            new PluginFieldDescriptor(
                "baseUrl",
                PluginFieldType.URL,
                false,
                DEFAULT_BASE_URL,
                List.of(),
                "Yahoo Finance API URL",
                "Chart endpoint used for connection testing",
                null,
                null,
                null)),
        List.of());
  }

  @Override
  public ValidationResult validate(PluginConfig config) {
    return config.value("portfolioId").flatMap(YahooExportPlugin::positiveLong).isPresent()
        ? ValidationResult.success()
        : ValidationResult.invalid("portfolioId must be a positive number");
  }

  @Override
  public void export(String target, PluginConfig config) throws IOException {
    Long portfolioId =
        config.value("portfolioId").flatMap(YahooExportPlugin::positiveLong).orElseThrow();
    service.exportToYahooCsv(portfolioId, target);
  }

  @Override
  public ConnectionTestResult testConnection(PluginConfig config) {
    try {
      String baseUrl = config.value("baseUrl").orElse(DEFAULT_BASE_URL);
      return marketDataService.fetchLatestQuote("SPY", baseUrl).isPresent()
          ? new ConnectionTestResult(true, true, "Yahoo Finance connection succeeded")
          : new ConnectionTestResult(true, false, "Yahoo Finance returned no quote for SPY");
    } catch (RuntimeException exception) {
      return new ConnectionTestResult(true, false, "Yahoo Finance connection test failed");
    }
  }

  private static java.util.Optional<Long> positiveLong(String value) {
    try {
      long parsed = Long.parseLong(value);
      return parsed > 0 ? java.util.Optional.of(parsed) : java.util.Optional.empty();
    } catch (NumberFormatException ignored) {
      return java.util.Optional.empty();
    }
  }
}
