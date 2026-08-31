package com.smartbox.investory.investment.imports;

/**
 * Thrown by {@link ImportOrchestratorService} when a parser fails. The matching FAILED {@code
 * imports} audit record is already persisted via {@link ImportBatchAuditWriter}, so the controller
 * can surface the message verbatim.
 */
public class ImportFailedException extends RuntimeException {

  public ImportFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
