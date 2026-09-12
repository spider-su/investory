package com.smartbox.investory.accounting;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountingBankImportService {
  private final AccountingSourceEvidenceService sourceEvidence;
  private final AccountingBankTransactionIngestionService ingestion;
  private final AccountingBankFileParser parser = new AccountingBankFileParser();

  public Result importFile(String filename, String contentType, byte[] payload, LocalDate period) {
    long sourceId = sourceEvidence.receiveBank(filename, contentType, payload, period);
    if (sourceEvidence.status(sourceId) == AccountingSourceStatus.IMPORTED) {
      return new Result(sourceId, 0, 0, 0);
    }
    try {
      var rows = parser.parse(payload);
      sourceEvidence.status(sourceId, AccountingSourceStatus.PARSED, null);
      int imported = 0;
      int reviewRequired = 0;
      for (int index = 0; index < rows.size(); index++) {
        var result = ingestion.ingest(rows.get(index), sourceId, sourceId + ":" + (index + 1));
        if (result.inserted()) imported++;
        if (result.reviewRequired()) reviewRequired++;
      }
      sourceEvidence.status(
          sourceId,
          reviewRequired == 0
              ? AccountingSourceStatus.IMPORTED
              : AccountingSourceStatus.REVIEW_REQUIRED,
          reviewRequired == 0
              ? null
              : reviewRequired + " bank row(s) require classification review");
      return new Result(sourceId, rows.size(), imported, reviewRequired);
    } catch (RuntimeException exception) {
      sourceEvidence.status(sourceId, AccountingSourceStatus.FAILED, exception.getMessage());
      throw exception;
    }
  }

  public record Result(
      long sourceId, int processedRows, int importedRows, int reviewRequiredRows) {}
}
