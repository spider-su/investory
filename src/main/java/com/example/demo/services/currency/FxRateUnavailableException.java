package com.example.demo.services.currency;

import com.example.demo.infrastructure.CurrencyType;
import java.time.LocalDate;

public class FxRateUnavailableException extends IllegalStateException {

  public FxRateUnavailableException(
      CurrencyType sourceCurrency,
      CurrencyType targetCurrency,
      LocalDate valuationDate,
      String conversionStatus) {
    super(
        "FX rate unavailable: source="
            + sourceCurrency
            + ", target="
            + targetCurrency
            + ", valuationDate="
            + valuationDate
            + ", status="
            + conversionStatus);
  }
}
