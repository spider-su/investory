package com.smartbox.investory.integrations.fx.spi;

import com.smartbox.investory.integrations.management.model.PluginConfig;
import com.smartbox.investory.integrations.management.spi.IntegrationPlugin;
import com.smartbox.investory.investment.port.fx.FxRateProvider.FxQuote;
import com.smartbox.investory.investment.port.fx.FxRateProvider.FxRequest;
import java.util.List;

public interface FxDataPlugin extends IntegrationPlugin {
  List<FxQuote> fetchRates(FxRequest request, PluginConfig config);
}
