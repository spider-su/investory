package com.smartbox.investory.investment.infrastructure.integration.fx;

import com.smartbox.investory.investment.infrastructure.integration.IntegrationPlugin;
import com.smartbox.investory.investment.infrastructure.integration.PluginConfig;
import java.util.List;

public interface FxDataPlugin extends IntegrationPlugin {
  List<FxQuote> fetchRates(FxRequest request, PluginConfig config);
}
