package com.smartbox.investory.ryczalt.integration.fx;

import com.smartbox.investory.integrations.fx.nbp.NbpClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Adapts the generic NBP client to the Ryczalt FX port. */
@Component
public class NbpFxRateAdapter implements FxRateSourcePort {
  public static final String PROVIDER = "NBP";

  private final NbpClient client;
  private final String baseUrl;

  public NbpFxRateAdapter(
      NbpClient client,
      @Value("${ryczalt.sources.fx-base-url:https://api.nbp.pl/api}") String baseUrl) {
    this.client = client;
    this.baseUrl = baseUrl;
  }

  @Override
  public FxRate fetch(String currency, LocalDate effectiveDate) {
    String code = Objects.requireNonNull(currency, "currency").toUpperCase();
    if ("PLN".equals(code)) {
      return new FxRate(code, effectiveDate, effectiveDate, BigDecimal.ONE, PROVIDER, "NBP-PLN");
    }
    NbpClient.NbpTable table =
        client.findTables(effectiveDate.minusDays(92), effectiveDate, baseUrl).stream()
            .filter(value -> value.getEffectiveDate() != null)
            .filter(value -> !value.getEffectiveDate().isAfter(effectiveDate))
            .max(Comparator.comparing(NbpClient.NbpTable::getEffectiveDate))
            .orElseThrow(
                () ->
                    new IllegalArgumentException("NBP has no rate on or before " + effectiveDate));
    BigDecimal rate =
        table.getRates().stream()
            .filter(value -> code.equalsIgnoreCase(value.getCode()))
            .map(NbpClient.NbpRate::getMid)
            .filter(value -> value != null && value.signum() > 0)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("NBP has no " + code + " rate"));
    return new FxRate(
        code,
        effectiveDate,
        table.getEffectiveDate(),
        rate,
        PROVIDER,
        "NBP-A-" + table.getEffectiveDate());
  }
}
