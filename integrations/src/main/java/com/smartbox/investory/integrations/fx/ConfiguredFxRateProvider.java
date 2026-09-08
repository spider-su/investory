package com.smartbox.investory.integrations.fx;

import com.smartbox.investory.integrations.fx.nbp.NbpFxDataPlugin;
import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.application.IntegrationConfigurationService;
import com.smartbox.investory.integrations.management.model.PluginConfig;
import com.smartbox.investory.investment.port.fx.FxRateProvider;
import com.smartbox.investory.investment.port.fx.FxRateProviderException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolves the default NBP adapter for Investment's provider-neutral port. */
@Component
@RequiredArgsConstructor
public class ConfiguredFxRateProvider implements FxRateProvider {
  private final NbpFxDataPlugin plugin;
  private final IntegrationConfigurationService configuration;

  @Override
  public List<FxQuote> fetchRates(FxRequest request) {
    try {
      PluginConfig config =
          configuration.resolveForRuntime(
              IntegrationType.FX_DATA, NbpFxDataPlugin.ID, PluginConfig.empty());
      return plugin.fetchRates(request, config);
    } catch (RuntimeException exception) {
      throw new FxRateProviderException(exception.getMessage(), exception);
    }
  }
}
