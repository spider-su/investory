package com.smartbox.investory.integrations.fx;

import com.smartbox.investory.integrations.fx.exchangeratehost.ExchangeRateHostFxDataPlugin;
import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.application.IntegrationConfigurationService;
import com.smartbox.investory.integrations.management.model.PluginConfig;
import com.smartbox.investory.investment.port.fx.FxRateProvider;
import com.smartbox.investory.investment.port.fx.FxRateProviderException;
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
      return plugin.fetchRates(request, config);
    } catch (RuntimeException exception) {
      throw new FxRateProviderException(exception.getMessage(), exception);
    }
  }
}
