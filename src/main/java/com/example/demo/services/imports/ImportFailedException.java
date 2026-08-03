package com.example.demo.services.imports;

/**
 * Thrown by {@link ImportOrchestratorService} when a parser fails. The matching FAILED {@code
 * imports} row plus its {@code import_row_error} are already persisted via {@link
 * ImportBatchAuditWriter}, so the controller can surface the message verbatim.
 */
public class ImportFailedException extends RuntimeException {

  public ImportFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
