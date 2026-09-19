package com.smartbox.investory.ryczalt.integration.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.integrations.fx.nbp.NbpClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class NbpFxRateAdapterTest {
  @Test
  void mapsLatestPublishedRateWithoutFloatingPointConversion() {
    NbpClient.NbpRate rate = new NbpClient.NbpRate();
    rate.setCode("EUR");
    rate.setMid(new BigDecimal("4.2718"));
    NbpClient.NbpTable table = new NbpClient.NbpTable();
    table.setEffectiveDate(LocalDate.of(2026, 9, 18));
    table.setRates(List.of(rate));

    NbpFxRateAdapter adapter =
        new NbpFxRateAdapter(new StubNbpClient(table), "https://example.test/api");

    FxRate result = adapter.fetch("eur", LocalDate.of(2026, 9, 21));

    assertEquals("EUR", result.currency());
    assertEquals(LocalDate.of(2026, 9, 18), result.effectiveDate());
    assertEquals(new BigDecimal("4.2718"), result.rate());
    assertEquals("NBP-A-2026-09-18", result.providerReference());
  }

  private static final class StubNbpClient extends NbpClient {
    private final NbpTable table;

    private StubNbpClient(NbpTable table) {
      super(new ObjectMapper());
      this.table = table;
    }

    @Override
    public List<NbpTable> findTables(LocalDate from, LocalDate to, String baseUrl) {
      return List.of(table);
    }
  }
}
