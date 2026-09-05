package com.smartbox.investory.integrations.fx.exchangeratehost;

import static com.smartbox.investory.integrations.FixedTestTime.TIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.smartbox.investory.integrations.management.model.PluginConfig;
import com.smartbox.investory.investment.port.fx.FxRateProvider.FxQuote;
import com.smartbox.investory.investment.port.fx.FxRateProvider.FxRequest;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Exchange Rate Host Fx Data Plugin")
class ExchangeRateHostFxDataPluginTest {
  @Mock private ExchangeRateClient client;

  @DisplayName("descriptor And Validation Expose Typed Secret Contract")
  @Test
  void descriptorAndValidationExposeTypedSecretContract() {
    ExchangeRateHostFxDataPlugin plugin = new ExchangeRateHostFxDataPlugin(client, TIME);

    assertEquals("exchangerate-host", plugin.id());
    assertEquals("apiKey", plugin.descriptor().configuration().getFirst().key());
    assertFalse(plugin.validate(new PluginConfig(Map.of())).valid());
    assertTrue(plugin.validate(PluginConfig.of("apiKey", "secret")).valid());
  }

  @DisplayName("translates Provider Response To Neutral Quotes")
  @Test
  void translatesProviderResponseToNeutralQuotes() {
    ExchangeRateClient.ExchangeRateResponse response =
        new ExchangeRateClient.ExchangeRateResponse();
    response.setDate(LocalDate.of(2026, 8, 11));
    response.setQuotes(Map.of("USDEUR", 0.9, "USDPLN", 4.0));
    when(client.getLatestRates("USD", "EUR,PLN", "secret")).thenReturn(response);
    ExchangeRateHostFxDataPlugin plugin = new ExchangeRateHostFxDataPlugin(client, TIME);

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
    assertEquals(new BigDecimal("0.9"), quotes.get(1).rate());
    assertEquals(BigDecimal.ONE, quotes.getFirst().rate());
    assertEquals(LocalDate.of(2026, 8, 11), quotes.getFirst().providerDate());
  }
}
