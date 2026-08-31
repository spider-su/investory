package com.smartbox.investory.investment.api.importing;

/** UI-facing import command boundary. */
public interface InvestmentImportApi {
  ImportResult importAuto(String fileName, byte[] content, String source, String sourceRef);

  ImportResult importForBroker(
      String broker, String fileName, byte[] content, String source, String sourceRef);

  record ImportResult(
      long batchId,
      String broker,
      String status,
      int rowsTotal,
      int rowsApplied,
      int rowsFailed,
      String message,
      boolean duplicate) {}

  class ImportFailure extends RuntimeException {
    public ImportFailure(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
