package com.smartbox.investory.ryczalt.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.ryczalt.integration.fx.FxRate;
import com.smartbox.investory.ryczalt.integration.fx.RyczaltFxRateService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RyczaltInvoicePlnNormalizerTest {
  private final RyczaltFxRateService fxRates = mock(RyczaltFxRateService.class);
  private final RyczaltInvoicePlnNormalizer normalizer = new RyczaltInvoicePlnNormalizer(fxRates);

  @Test
  void normalizesPlnAndAppliesVatRatioToPlnVat() {
    var normalized =
        normalizer.normalize(
            "PLN",
            LocalDate.of(2026, 2, 10),
            new BigDecimal("1000"),
            new BigDecimal("230"),
            new BigDecimal("0.50"));

    assertEquals(0, normalized.bookedNetPln().compareTo(new BigDecimal("1000")));
    assertEquals(0, normalized.bookedVatPln().compareTo(new BigDecimal("230")));
    assertEquals(0, normalized.deductibleVatPln().compareTo(new BigDecimal("115")));
    assertNull(normalized.fxRate());

    var fullyDeductible =
        normalizer.normalize(
            "PLN",
            LocalDate.of(2026, 2, 10),
            new BigDecimal("1000"),
            new BigDecimal("230"),
            BigDecimal.ONE);
    assertEquals(0, fullyDeductible.deductibleVatPln().compareTo(new BigDecimal("230")));
  }

  @Test
  void normalizesForeignVatBeforeApplyingVatRatio() {
    var date = LocalDate.of(2026, 2, 10);
    when(fxRates.rateFor("EUR", date))
        .thenReturn(
            new FxRate(
                "EUR", date, date, new BigDecimal("4.3260869565"), "TEST", "EUR-2026-02-10"));

    var normalized =
        normalizer.normalize(
            "EUR", date, new BigDecimal("1000"), new BigDecimal("230"), new BigDecimal("0.50"));

    assertEquals(0, normalized.bookedNetPln().compareTo(new BigDecimal("4326.0870")));
    assertEquals(0, normalized.bookedVatPln().compareTo(new BigDecimal("995.0000")));
    assertEquals(0, normalized.deductibleVatPln().compareTo(new BigDecimal("497.5000")));
    assertEquals("TEST", normalized.fxProvider());
  }
}
