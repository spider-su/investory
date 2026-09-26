package com.smartbox.investory.ryczalt.application;

import com.smartbox.investory.ryczalt.integration.fx.FxRate;
import com.smartbox.investory.ryczalt.integration.fx.RyczaltFxRateService;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.stereotype.Service;

/** Converts invoice money and VAT decisions to canonical PLN facts. */
@Service
public class RyczaltInvoicePlnNormalizer {
  private static final int PLN_SCALE = 4;

  private final RyczaltFxRateService fxRates;

  public RyczaltInvoicePlnNormalizer(RyczaltFxRateService fxRates) {
    this.fxRates = fxRates;
  }

  public Normalized normalize(
      String currency,
      LocalDate accountingDate,
      BigDecimal netAmount,
      BigDecimal vatAmount,
      BigDecimal vatDeductionRatio) {
    CurrencyType type = CurrencyType.valueOf(currency.toUpperCase(Locale.ROOT));
    FxRate fx = type == CurrencyType.PLN ? null : fxRates.rateFor(type.name(), accountingDate);
    BigDecimal rate = fx == null ? BigDecimal.ONE : fx.rate();
    BigDecimal bookedNet = pln(netAmount.multiply(rate));
    BigDecimal bookedVat = pln(vatAmount.multiply(rate));
    BigDecimal deductible =
        vatDeductionRatio == null ? null : pln(bookedVat.multiply(vatDeductionRatio));
    return new Normalized(
        bookedNet,
        bookedVat,
        deductible,
        fx == null ? null : fx.rate(),
        fx == null ? null : fx.effectiveDate(),
        fx == null ? null : fx.provider(),
        fx == null ? null : fx.providerReference());
  }

  private static BigDecimal pln(BigDecimal value) {
    return value.setScale(PLN_SCALE, RoundingMode.HALF_UP);
  }

  public record Normalized(
      BigDecimal bookedNetPln,
      BigDecimal bookedVatPln,
      BigDecimal deductibleVatPln,
      BigDecimal fxRate,
      java.time.LocalDate fxEffectiveDate,
      String fxProvider,
      String fxProviderReference) {}
}
