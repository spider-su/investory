package com.smartbox.investory.shared.currency;

/** Signals that a currency conversion cannot be produced for the requested date. */
public class CurrencyConversionUnavailableException extends IllegalStateException {
  public CurrencyConversionUnavailableException(String message) {
    super(message);
  }
}
