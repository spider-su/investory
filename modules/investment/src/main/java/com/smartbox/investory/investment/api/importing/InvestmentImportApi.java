package com.smartbox.investory.investment.api.importing;

/** UI-facing import command boundary. */
public interface InvestmentImportApi {
  ImportResult importAuto(
      Long portfolioId, String fileName, byte[] content, ImportSource source, String sourceRef);

  default ImportResult importAuto(
      Long portfolioId,
      String fileName,
      byte[] content,
      ImportSource source,
      String sourceRef,
      boolean deferRefresh) {
    return importAuto(portfolioId, fileName, content, source, sourceRef);
  }

  ImportResult importForBroker(
      Long portfolioId,
      ImportBroker broker,
      String fileName,
      byte[] content,
      ImportSource source,
      String sourceRef);

  default ImportResult importForBroker(
      Long portfolioId,
      ImportBroker broker,
      String fileName,
      byte[] content,
      ImportSource source,
      String sourceRef,
      boolean deferRefresh) {
    return importForBroker(portfolioId, broker, fileName, content, source, sourceRef);
  }

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
