package com.smartbox.investory.integrations.infrastructure.integration.fx;

import com.smartbox.investory.integrations.infrastructure.integration.IntegrationPlugin;
import com.smartbox.investory.integrations.infrastructure.integration.PluginConfig;
import java.util.List;

public interface FxDataPlugin extends IntegrationPlugin {
  List<FxQuote> fetchRates(FxRequest request, PluginConfig config);
}
