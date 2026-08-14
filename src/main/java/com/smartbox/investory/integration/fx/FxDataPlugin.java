package com.smartbox.investory.integration.fx;

import com.smartbox.investory.integration.IntegrationPlugin;
import com.smartbox.investory.integration.PluginConfig;
import java.util.List;

public interface FxDataPlugin extends IntegrationPlugin {
  List<FxQuote> fetchRates(FxRequest request, PluginConfig config);
}
