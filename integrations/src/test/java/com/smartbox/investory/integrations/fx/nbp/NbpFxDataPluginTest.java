package com.smartbox.investory.integrations.fx.nbp;

import static com.smartbox.investory.integrations.FixedTestTime.TIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.smartbox.investory.integrations.management.model.PluginConfig;
import com.smartbox.investory.investment.port.fx.FxRateProvider.FxHistoryQuote;
import com.smartbox.investory.investment.port.fx.FxRateProvider.FxQuote;
import com.smartbox.investory.investment.port.fx.FxRateProvider.FxRequest;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NbpFxDataPluginTest {
  @Mock private NbpClient client;

  @Test
  void derivesUsdQuotesWithExactDecimalArithmeticAndProviderDate() {
    when(client.findTables(
            LocalDate.of(2026, 5, 12), LocalDate.of(2026, 8, 12), NbpClient.DEFAULT_BASE_URL))
        .thenReturn(List.of(table(LocalDate.of(2026, 8, 11), "4.123456789", "4.567890123")));

    List<FxQuote> quotes =
        plugin().fetchRates(request(LocalDate.of(2026, 8, 12)), PluginConfig.empty());

    assertEquals(new BigDecimal("4.123456789"), quotes.get(1).rate());
    assertEquals(
        new BigDecimal("4.123456789")
            .divide(new BigDecimal("4.567890123"), 18, java.math.RoundingMode.HALF_UP),
        quotes.getFirst().rate());
    assertEquals(LocalDate.of(2026, 8, 11), quotes.getFirst().providerDate());
    assertEquals(LocalDate.of(2026, 8, 12), quotes.getFirst().effectiveDate());
  }

  @Test
  void usesLatestPublicationBeforeRequestedDateAndNeverFutureRate() {
    LocalDate requested = LocalDate.of(2026, 8, 16);
    when(client.findTables(LocalDate.of(2026, 5, 16), requested, NbpClient.DEFAULT_BASE_URL))
        .thenReturn(
            List.of(
                table(LocalDate.of(2026, 8, 14), "4.0", "4.5"),
                table(LocalDate.of(2026, 8, 17), "9.0", "9.0")));

    FxQuote quote = plugin().fetchRates(request(requested), PluginConfig.empty()).getFirst();
    assertEquals(
        new BigDecimal("4.0").divide(new BigDecimal("4.5"), 18, java.math.RoundingMode.HALF_UP),
        quote.rate());
    assertEquals(LocalDate.of(2026, 8, 14), quote.providerDate());
  }

  @Test
  void rejectsMissingRateAndReportsFailedConnection() {
    when(client.findTables(TIME.today().minusDays(92), TIME.today(), NbpClient.DEFAULT_BASE_URL))
        .thenReturn(List.of(table(TIME.today(), "4.0", null)));
    NbpFxDataPlugin plugin = plugin();

    assertThrows(
        IllegalArgumentException.class,
        () ->
            plugin.fetchRates(
                new FxRequest(CurrencyType.USD, List.of(CurrencyType.EUR), TIME.today()),
                PluginConfig.empty()));
    assertFalse(plugin.testConnection(PluginConfig.empty()).success());
  }

  @Test
  void validatesWithoutAnApiSecret() {
    assertEquals("nbp", plugin().id());
    assertEquals(
        "https://api.nbp.pl/api", plugin().descriptor().configuration().getFirst().defaultValue());
    assertEquals(true, plugin().validate(PluginConfig.empty()).valid());
  }

  @Test
  void fetchesAllPublishedTablesForAHistoryRange() {
    LocalDate from = LocalDate.of(2026, 8, 1);
    LocalDate to = LocalDate.of(2026, 8, 5);
    when(client.findTables(from, to, NbpClient.DEFAULT_BASE_URL))
        .thenReturn(List.of(table(LocalDate.of(2026, 8, 1), "4.0", "4.5")));

    List<FxHistoryQuote> quotes = plugin().fetchHistory(from, to, PluginConfig.empty());

    assertEquals(2, quotes.size());
    assertEquals(LocalDate.of(2026, 8, 1), quotes.getFirst().valuationDate());
    assertEquals(LocalDate.of(2026, 8, 1), quotes.getFirst().providerDate());
  }

  private NbpFxDataPlugin plugin() {
    return new NbpFxDataPlugin(client, TIME);
  }

  private FxRequest request(LocalDate date) {
    return new FxRequest(CurrencyType.USD, List.of(CurrencyType.EUR, CurrencyType.PLN), date);
  }

  private static NbpClient.NbpTable table(LocalDate date, String usd, String eur) {
    NbpClient.NbpTable table = new NbpClient.NbpTable();
    table.setEffectiveDate(date);
    table.setRates(List.of(rate("USD", usd), rate("EUR", eur)));
    return table;
  }

  private static NbpClient.NbpRate rate(String code, String value) {
    NbpClient.NbpRate rate = new NbpClient.NbpRate();
    rate.setCode(code);
    rate.setMid(value == null ? null : new BigDecimal(value));
    return rate;
  }
}
