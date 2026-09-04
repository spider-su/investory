package com.smartbox.investory.integrations.fx.exchangeratehost;

import com.smartbox.investory.integrations.fx.spi.FxDataPlugin;
import com.smartbox.investory.integrations.management.api.model.ConnectionTestResult;
import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.api.model.PluginFieldDescriptor;
import com.smartbox.investory.integrations.management.api.model.PluginFieldType;
import com.smartbox.investory.integrations.management.model.PluginConfig;
import com.smartbox.investory.integrations.management.model.PluginDescriptor;
import com.smartbox.investory.integrations.management.model.ValidationResult;
import com.smartbox.investory.investment.port.fx.FxRateProvider.FxQuote;
import com.smartbox.investory.investment.port.fx.FxRateProvider.FxRequest;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.time.ApplicationTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExchangeRateHostFxDataPlugin
    implements FxDataPlugin,
        com.smartbox.investory.integrations.management.spi.TestableIntegrationPlugin {
  public static final String ID = "exchangerate-host";
  private static final String API_KEY = "apiKey";

  private final ExchangeRateClient client;
  private final ApplicationTime applicationTime;

  @Override
  public String id() {
    return ID;
  }

  @Override
  public IntegrationType type() {
    return IntegrationType.FX_DATA;
  }

  @Override
  public PluginDescriptor descriptor() {
    return new PluginDescriptor(
        ID,
        "ExchangeRate.host",
        IntegrationType.FX_DATA,
        List.of(
            PluginFieldDescriptor.requiredSecret(API_KEY),
            new PluginFieldDescriptor(
                "baseUrl",
                PluginFieldType.URL,
                false,
                "https://api.exchangerate.host",
                List.of(),
                "API URL",
                "ExchangeRate.host API base URL",
                null,
                null,
                null)),
        List.of("refresh-rates"));
  }

  @Override
  public ValidationResult validate(PluginConfig config) {
    return config.value(API_KEY).isPresent()
        ? ValidationResult.success()
        : ValidationResult.invalid("Missing required configuration: " + API_KEY);
  }

  @Override
  public List<FxQuote> fetchRates(FxRequest request, PluginConfig config) {
    String currencies =
        request.targets().stream()
            .filter(target -> target != request.base())
            .map(CurrencyType::name)
            .reduce((left, right) -> left + "," + right)
            .orElse("");
    ExchangeRateClient.ExchangeRateResponse response =
        config.value("baseUrl").isPresent()
            ? client.getLatestRates(
                request.base().name(),
                currencies,
                config.value(API_KEY).orElse(""),
                config.value("baseUrl").orElseThrow())
            : client.getLatestRates(
                request.base().name(), currencies, config.value(API_KEY).orElse(""));
    if (response == null || response.getQuotes() == null || response.getQuotes().isEmpty()) {
      throw new IllegalArgumentException("empty exchangerate.host response");
    }
    List<FxQuote> quotes = new ArrayList<>();
    for (CurrencyType target : request.targets()) {
      if (target == request.base()) {
        quotes.add(
            new FxQuote(
                request.base(),
                target,
                BigDecimal.ONE,
                request.effectiveDate(),
                response.getDate()));
        continue;
      }
      String key = request.base().name() + target.name();
      Double rate = findQuote(response, key);
      if (rate == null || rate == 0.0) {
        throw new IllegalArgumentException("missing " + request.base() + " -> " + target + " rate");
      }
      quotes.add(
          new FxQuote(
              request.base(),
              target,
              BigDecimal.valueOf(rate),
              request.effectiveDate(),
              response.getDate()));
    }
    return quotes;
  }

  private Double findQuote(ExchangeRateClient.ExchangeRateResponse response, String key) {
    return response.getQuotes().entrySet().stream()
        .filter(entry -> entry.getKey().toUpperCase(Locale.ROOT).equals(key))
        .map(java.util.Map.Entry::getValue)
        .findFirst()
        .orElse(null);
  }

  @Override
  public ConnectionTestResult testConnection(PluginConfig config) {
    try {
      fetchRates(
          new FxRequest(CurrencyType.USD, List.of(CurrencyType.EUR), applicationTime.today()),
          config);
      return new ConnectionTestResult(true, true, "Connection succeeded");
    } catch (RuntimeException exception) {
      return new ConnectionTestResult(true, false, "Connection test failed");
    }
  }
}
