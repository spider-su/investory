package com.smartbox.investory.accounting.service;

import com.smartbox.investory.accounting.*;
import com.smartbox.investory.accounting.infrastructure.persistence.*;
import com.smartbox.investory.integrations.bank.BankTransactionQuery;
import com.smartbox.investory.integrations.bank.CsvBankTransactionSource;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountingBankImportService {
  private final AccountingSourceEvidenceService sourceEvidence;
  private final AccountingBankTransactionIngestionService ingestion;
  private final String externalAccountId;

  @Autowired
  public AccountingBankImportService(
      AccountingSourceEvidenceService sourceEvidence,
      AccountingBankTransactionIngestionService ingestion) {
    this(sourceEvidence, ingestion, "JDG_MAIN_ACCOUNT");
  }

  public AccountingBankImportService(
      AccountingSourceEvidenceService sourceEvidence,
      AccountingBankTransactionIngestionService ingestion,
      @Value("${investory.accounting.bank.external-account-id:JDG_MAIN_ACCOUNT}")
          String externalAccountId) {
    this.sourceEvidence = sourceEvidence;
    this.ingestion = ingestion;
    this.externalAccountId = Objects.requireNonNull(externalAccountId);
  }

  @Transactional
  public Result importFile(
      long profileId, String filename, String contentType, byte[] payload, LocalDate period) {
    long sourceId = sourceEvidence.receiveBank(profileId, filename, contentType, payload, period);
    if (sourceEvidence.status(sourceId) == AccountingSourceStatus.IMPORTED) {
      return new Result(sourceId, 0, 0, 0);
    }
    try {
      var source = new CsvBankTransactionSource(payload, externalAccountId);
      var rows =
          source
              .transactions(new BankTransactionQuery(externalAccountId, null, null, null))
              .transactions();
      sourceEvidence.status(sourceId, AccountingSourceStatus.PARSED, null);
      int imported = 0;
      int reviewRequired = 0;
      for (var row : rows) {
        var result = ingestion.ingest(row, sourceId);
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
