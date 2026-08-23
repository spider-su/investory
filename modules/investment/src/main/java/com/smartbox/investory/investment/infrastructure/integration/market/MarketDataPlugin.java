package com.smartbox.investory.investment.infrastructure.integration.market;

import com.smartbox.investory.investment.accounting.model.StockQuote;
import com.smartbox.investory.investment.infrastructure.integration.IntegrationPlugin;
import com.smartbox.investory.investment.infrastructure.integration.PluginConfig;
import java.util.List;
import java.util.Map;

public interface MarketDataPlugin extends IntegrationPlugin {
  Map<String, StockQuote> fetchQuotes(List<String> symbols, PluginConfig config);

  default String externalSymbol(String canonicalSymbol, String ticker) {
    return ticker;
  }
}
