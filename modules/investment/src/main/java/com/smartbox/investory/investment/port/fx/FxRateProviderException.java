package com.smartbox.investory.investment.port.fx;

/** Provider failure translated by an external FX adapter. */
public class FxRateProviderException extends RuntimeException {
  public FxRateProviderException(String message, Throwable cause) {
    super(message, cause);
  }
}
