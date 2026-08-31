package com.smartbox.investory.integrations.fx;

import com.smartbox.investory.integrations.infrastructure.integration.IntegrationType;
import com.smartbox.investory.integrations.infrastructure.integration.PluginConfig;
import com.smartbox.investory.integrations.infrastructure.integration.config.IntegrationConfigurationService;
import com.smartbox.investory.integrations.infrastructure.integration.fx.ExchangeRateHostFxDataPlugin;
import com.smartbox.investory.investment.port.fx.FxRateProvider;
import com.smartbox.investory.investment.port.fx.FxRateProviderException;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolves external FX configuration and adapts it to Investment's provider-neutral port. */
@Component
@RequiredArgsConstructor
public class ConfiguredFxRateProvider implements FxRateProvider {
  private final ExchangeRateHostFxDataPlugin plugin;
  private final IntegrationConfigurationService configuration;

  @Override
  public List<FxQuote> fetchRates(FxRequest request) {
    try {
      PluginConfig config =
          configuration.resolveForRuntime(
              IntegrationType.FX_DATA, ExchangeRateHostFxDataPlugin.ID, PluginConfig.empty());
      return plugin
          .fetchRates(
              new com.smartbox.investory.integrations.infrastructure.integration.fx.FxRequest(
                  request.base(), request.targets(), request.effectiveDate()),
              config)
          .stream()
          .map(
              quote ->
                  new FxRateProvider.FxQuote(
                      quote.base(),
                      quote.target(),
                      BigDecimal.valueOf(quote.rate()),
                      request.effectiveDate(),
                      quote.providerDate()))
          .toList();
    } catch (RuntimeException exception) {
      throw new FxRateProviderException(exception.getMessage(), exception);
    }
  }
}
