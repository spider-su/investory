package com.smartbox.investory.integration.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.smartbox.investory.clients.currency.ExchangeRateClient;
import com.smartbox.investory.infrastructure.CurrencyType;
import com.smartbox.investory.integration.PluginConfig;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExchangeRateHostFxDataPluginTest {
  @Mock private ExchangeRateClient client;

  @Test
  void descriptorAndValidationExposeTypedSecretContract() {
    ExchangeRateHostFxDataPlugin plugin = new ExchangeRateHostFxDataPlugin(client);

    assertEquals("exchangerate-host", plugin.id());
    assertEquals("apiKey", plugin.descriptor().configuration().getFirst().key());
    assertFalse(plugin.validate(new PluginConfig(Map.of())).valid());
    assertTrue(plugin.validate(PluginConfig.of("apiKey", "secret")).valid());
  }

  @Test
  void translatesProviderResponseToNeutralQuotes() {
    ExchangeRateClient.ExchangeRateResponse response =
        new ExchangeRateClient.ExchangeRateResponse();
    response.setDate(LocalDate.of(2026, 8, 11));
    response.setQuotes(Map.of("USDEUR", 0.9, "USDPLN", 4.0));
    when(client.getLatestRates("USD", "EUR,PLN", "secret")).thenReturn(response);
    ExchangeRateHostFxDataPlugin plugin = new ExchangeRateHostFxDataPlugin(client);

    List<FxQuote> quotes =
        plugin.fetchRates(
            new FxRequest(
                CurrencyType.USD,
                List.of(CurrencyType.USD, CurrencyType.EUR, CurrencyType.PLN),
                LocalDate.of(2026, 8, 11)),
            PluginConfig.of("apiKey", "secret"));

    assertEquals(
        List.of(CurrencyType.USD, CurrencyType.EUR, CurrencyType.PLN),
        quotes.stream().map(FxQuote::target).toList());
    assertEquals(0.9, quotes.get(1).rate());
    assertEquals(1.0, quotes.getFirst().rate());
    assertEquals(LocalDate.of(2026, 8, 11), quotes.getFirst().providerDate());
  }
}
