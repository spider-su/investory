package com.smartbox.investory.integrations.fx.nbp;

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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NbpFxDataPlugin
    implements FxDataPlugin,
        com.smartbox.investory.integrations.management.spi.TestableIntegrationPlugin {
  public static final String ID = "nbp";
  private static final String BASE_URL = "baseUrl";
  private static final int NBP_MAX_RANGE_DAYS = 93;

  private final NbpClient client;
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
        "Narodowy Bank Polski (NBP)",
        IntegrationType.FX_DATA,
        List.of(
            new PluginFieldDescriptor(
                BASE_URL,
                PluginFieldType.URL,
                false,
                NbpClient.DEFAULT_BASE_URL,
                List.of(),
                "API URL",
                "NBP API base URL",
                null,
                null,
                null)),
        List.of("refresh-rates"));
  }

  @Override
  public ValidationResult validate(PluginConfig config) {
    return ValidationResult.success();
  }

  @Override
  public List<FxQuote> fetchRates(FxRequest request, PluginConfig config) {
    if (request.base() != CurrencyType.USD) {
      throw new IllegalArgumentException("NBP FX provider requires USD base");
    }
    NbpClient.NbpTable table =
        findLatestTable(
            request.effectiveDate(), config.value(BASE_URL).orElse(NbpClient.DEFAULT_BASE_URL));
    BigDecimal usdPln = rate(table, "USD");
    BigDecimal eurPln = rate(table, "EUR");
    BigDecimal usdEur = usdPln.divide(eurPln, 18, RoundingMode.HALF_UP);
    return request.targets().stream()
        .map(
            target ->
                new FxQuote(
                    request.base(),
                    target,
                    target == CurrencyType.USD
                        ? BigDecimal.ONE
                        : target == CurrencyType.PLN ? usdPln : usdEur,
                    request.effectiveDate(),
                    table.getEffectiveDate()))
        .toList();
  }

  private NbpClient.NbpTable findLatestTable(LocalDate effectiveDate, String baseUrl) {
    LocalDate end = effectiveDate;
    for (int attempt = 0; attempt < 100; attempt++) {
      LocalDate start = end.minusDays(NBP_MAX_RANGE_DAYS - 1L);
      NbpClient.NbpTable found =
          client.findTables(start, end, baseUrl).stream()
              .filter(
                  table ->
                      table.getEffectiveDate() != null
                          && !table.getEffectiveDate().isAfter(effectiveDate))
              .max(Comparator.comparing(NbpClient.NbpTable::getEffectiveDate))
              .orElse(null);
      if (found != null) return found;
      end = start.minusDays(1);
    }
    throw new IllegalArgumentException("NBP has no published rate on or before " + effectiveDate);
  }

  private BigDecimal rate(NbpClient.NbpTable table, String code) {
    if (table.getRates() == null) {
      throw new IllegalArgumentException("empty NBP response");
    }
    return table.getRates().stream()
        .filter(rate -> code.equalsIgnoreCase(rate.getCode()))
        .map(NbpClient.NbpRate::getMid)
        .filter(value -> value != null && value.signum() > 0)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("missing NBP " + code + " rate"));
  }

  @Override
  public ConnectionTestResult testConnection(PluginConfig config) {
    try {
      fetchRates(
          new FxRequest(
              CurrencyType.USD,
              List.of(CurrencyType.EUR, CurrencyType.PLN),
              applicationTime.today()),
          config);
      return new ConnectionTestResult(true, true, "Connection succeeded");
    } catch (RuntimeException exception) {
      return new ConnectionTestResult(true, false, "Connection test failed");
    }
  }
}
