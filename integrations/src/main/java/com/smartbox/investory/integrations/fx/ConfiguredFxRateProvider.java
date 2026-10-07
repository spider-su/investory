package com.smartbox.investory.integrations.fx;

import com.smartbox.investory.integrations.fx.nbp.NbpFxDataPlugin;
import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.application.IntegrationConfigurationService;
import com.smartbox.investory.integrations.management.model.PluginConfig;
import com.smartbox.investory.investment.port.fx.FxRateHistoryProvider;
import com.smartbox.investory.investment.port.fx.FxRateProvider;
import com.smartbox.investory.investment.port.fx.FxRateProviderException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolves the default NBP adapter for Investment's provider-neutral port. */
@Component
@RequiredArgsConstructor
public class ConfiguredFxRateProvider implements FxRateProvider, FxRateHistoryProvider {
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
      throw new FxRateProviderException(diagnosticMessage(exception), exception);
    }
  }

  @Override
  public List<FxHistoryQuote> fetchHistory(java.time.LocalDate from, java.time.LocalDate to) {
    try {
      PluginConfig config =
          configuration.resolveForRuntime(
              IntegrationType.FX_DATA, NbpFxDataPlugin.ID, PluginConfig.empty());
      return plugin.fetchHistory(from, to, config);
    } catch (RuntimeException exception) {
      throw new FxRateProviderException(diagnosticMessage(exception), exception);
    }
  }

  private String diagnosticMessage(RuntimeException exception) {
    Throwable rootCause = exception;
    while (rootCause.getCause() != null) rootCause = rootCause.getCause();
    String message = rootCause.getMessage();
    if (rootCause == exception || message == null || message.isBlank())
      return exception.getMessage();
    String detail = rootCause.getClass().getSimpleName() + ": " + message;
    if (detail.length() > 240) detail = detail.substring(0, 240);
    return exception.getMessage() + " (" + detail + ")";
  }
}
