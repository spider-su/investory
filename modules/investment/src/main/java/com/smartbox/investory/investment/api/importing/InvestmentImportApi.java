package com.smartbox.investory.investment.api.importing;

/** UI-facing import command boundary. */
public interface InvestmentImportApi {
  ImportResult importAuto(String fileName, byte[] content, ImportSource source, String sourceRef);

  ImportResult importForBroker(
      ImportBroker broker, String fileName, byte[] content, ImportSource source, String sourceRef);

  record ImportResult(
      long batchId,
      String broker,
      ImportStatus status,
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
