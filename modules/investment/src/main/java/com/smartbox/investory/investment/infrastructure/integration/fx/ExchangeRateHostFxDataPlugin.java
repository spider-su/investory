package com.smartbox.investory.investment.infrastructure.integration.fx;

import com.smartbox.investory.investment.infrastructure.fx.client.ExchangeRateClient;
import com.smartbox.investory.investment.infrastructure.integration.IntegrationType;
import com.smartbox.investory.investment.infrastructure.integration.PluginConfig;
import com.smartbox.investory.investment.infrastructure.integration.PluginDescriptor;
import com.smartbox.investory.investment.infrastructure.integration.PluginFieldDescriptor;
import com.smartbox.investory.investment.infrastructure.integration.ValidationResult;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExchangeRateHostFxDataPlugin
    implements FxDataPlugin,
        com.smartbox.investory.investment.infrastructure.integration.TestableIntegrationPlugin {
  public static final String ID = "exchangerate-host";
  private static final String API_KEY = "apiKey";

  private final ExchangeRateClient client;

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
        List.of(PluginFieldDescriptor.requiredSecret(API_KEY)),
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
        client.getLatestRates(request.base().name(), currencies, config.value(API_KEY).orElse(""));
    if (response == null || response.getQuotes() == null || response.getQuotes().isEmpty()) {
      throw new IllegalArgumentException("empty exchangerate.host response");
    }
    List<FxQuote> quotes = new ArrayList<>();
    for (CurrencyType target : request.targets()) {
      if (target == request.base()) {
        quotes.add(new FxQuote(request.base(), target, 1.0, response.getDate(), ID));
        continue;
      }
      String key = request.base().name() + target.name();
      Double rate = findQuote(response, key);
      if (rate == null || rate == 0.0) {
        throw new IllegalArgumentException("missing " + request.base() + " -> " + target + " rate");
      }
      quotes.add(new FxQuote(request.base(), target, rate, response.getDate(), ID));
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
  public com.smartbox.investory.investment.infrastructure.integration.ConnectionTestResult
      testConnection(PluginConfig config) {
    try {
      fetchRates(
          new FxRequest(CurrencyType.USD, List.of(CurrencyType.EUR), java.time.LocalDate.now()),
          config);
      return new com.smartbox.investory.investment.infrastructure.integration.ConnectionTestResult(
          true, true, "Connection succeeded");
    } catch (RuntimeException exception) {
      return new com.smartbox.investory.investment.infrastructure.integration.ConnectionTestResult(
          true, false, "Connection test failed");
    }
  }
}
