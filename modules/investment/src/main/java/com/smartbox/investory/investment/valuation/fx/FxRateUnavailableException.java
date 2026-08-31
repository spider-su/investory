package com.smartbox.investory.investment.valuation.fx;

import com.smartbox.investory.shared.currency.CurrencyConversionUnavailableException;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.time.LocalDate;

public class FxRateUnavailableException extends CurrencyConversionUnavailableException {

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
