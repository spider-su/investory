package com.smartbox.investory.investment.api;

/** UI-facing import command boundary. */
public interface InvestmentImportApi {
  Object importAuto(String fileName, byte[] content, String source, String sourceRef);

  Object importForBroker(
      String broker, String fileName, byte[] content, String source, String sourceRef);

  class ImportFailure extends RuntimeException {
    public ImportFailure(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
