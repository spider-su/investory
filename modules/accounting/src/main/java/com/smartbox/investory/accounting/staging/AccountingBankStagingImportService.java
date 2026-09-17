package com.smartbox.investory.accounting.staging;

import com.smartbox.investory.accounting.AccountingSourceStatus;
import com.smartbox.investory.accounting.service.AccountingSourceEvidenceService;
import com.smartbox.investory.integrations.bank.BankDataProvider;
import com.smartbox.investory.integrations.bank.BankTransactionQuery;
import com.smartbox.investory.integrations.bank.CsvBankTransactionSource;
import com.smartbox.investory.integrations.bank.ExternalBankTransaction;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AccountingBankStagingImportService {
  private final AccountingSourceEvidenceService sources;
  private final AccountingStagingAcquisitionService staging;
  private final String externalAccountId;

  public AccountingBankStagingImportService(
      AccountingSourceEvidenceService sources,
      AccountingStagingAcquisitionService staging,
      @Value("${investory.accounting.bank.external-account-id:JDG_MAIN_ACCOUNT}")
          String externalAccountId) {
    this.sources = sources;
    this.staging = staging;
    this.externalAccountId = externalAccountId;
  }

  public Result stageFile(
      long profileId, String filename, String contentType, byte[] payload, LocalDate period) {
    long sourceId = sources.receiveBank(filename, contentType, payload, period);
    try {
      var rows =
          new CsvBankTransactionSource(payload, externalAccountId)
              .transactions(new BankTransactionQuery(externalAccountId, null, null, null))
              .transactions();
      int staged = 0;
      Set<LocalDate> periods = new LinkedHashSet<>();
      for (int index = 0; index < rows.size(); index++) {
        var row = rows.get(index);
        periods.add(row.bookingDate().withDayOfMonth(1));
        staging.stageBank(
            profileId,
            // The visible month is only navigation context. The bank contract makes the
            // booking date authoritative, so one uploaded statement can populate many months.
            row.bookingDate().withDayOfMonth(1),
            new ExternalBankTransaction(
                BankDataProvider.CSV,
                externalAccountId,
                // The row identity is content-derived by the provider adapter. Do not include
                // sourceId or row position: overlapping exports must deduplicate the same row.
                row.externalTransactionId(),
                row.bookingDate(),
                row.bookingDate(),
                row.relatedPeriod(),
                row.amount(),
                row.currency(),
                row.counterpartyName(),
                null,
                row.remittanceInformation(),
                row.rawReference(),
                null),
            sourceId,
            filename);
        staged++;
      }
      sources.status(sourceId, AccountingSourceStatus.PARSED, null);
      return new Result(sourceId, rows.size(), staged, Set.copyOf(periods));
    } catch (RuntimeException exception) {
      sources.status(sourceId, AccountingSourceStatus.FAILED, exception.getMessage());
      throw exception;
    }
  }

  public record Result(long sourceId, int processedRows, int stagedRows, Set<LocalDate> periods) {}
}
