package com.smartbox.investory.integrations.infrastructure.integration.market;

import com.smartbox.investory.integrations.infrastructure.integration.IntegrationPlugin;
import com.smartbox.investory.integrations.infrastructure.integration.PluginConfig;
import com.smartbox.investory.investment.port.market.MarketQuote;
import java.util.List;
import java.util.Map;

public interface MarketDataPlugin extends IntegrationPlugin {
  Map<String, MarketQuote> fetchQuotes(List<String> symbols, PluginConfig config);

  default String externalSymbol(String canonicalSymbol, String ticker) {
    return ticker;
  }
}
