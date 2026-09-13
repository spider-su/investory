package com.smartbox.investory.accounting.staging;

import com.smartbox.investory.accounting.AccountingBankTransactionIngestionService;
import com.smartbox.investory.accounting.AccountingSourceEvidenceService;
import com.smartbox.investory.accounting.AccountingSourceStatus;
import com.smartbox.investory.integrations.bank.BankDataProvider;
import com.smartbox.investory.integrations.bank.ExternalBankTransaction;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountingStagingPromotionService {
  private final AccountingStagingRepository repository;
  private final AccountingStagingReconciliationService reconciliation;
  private final AccountingSourceEvidenceService sources;
  private final AccountingBankTransactionIngestionService bankIngestion;

  @Transactional
  public PromotionResult promoteNew(long profileId, LocalDate taxPeriod) {
    reconciliation.reconcile(profileId, taxPeriod);
    int invoices = 0;
    int bank = 0;
    for (StagedInvoice row : repository.invoices(profileId, taxPeriod)) {
      if (row.status() != StagingReconciliationStatus.NEW) continue;
      repository.promoted("invoice", row.id(), repository.promoteInvoice(row));
      invoices++;
    }
    for (StagedBankTransaction row : repository.bankTransactions(profileId, taxPeriod)) {
      if (row.status() != StagingReconciliationStatus.NEW) continue;
      bankIngestion.ingest(
          new ExternalBankTransaction(
              BankDataProvider.valueOf(row.provider()),
              row.externalAccountId(),
              row.externalTransactionId(),
              row.bookingDate(),
              row.valueDate(),
              row.taxPeriod(),
              row.amount(),
              row.currency(),
              row.counterpartyName(),
              row.counterpartyAccount(),
              row.remittanceInformation(),
              row.remittanceInformation(),
              row.sourcePayloadHash()),
          row.sourceId());
      repository.promoted("bank_transaction", row.id(), repository.promoteBank(row));
      bank++;
    }
    updateSourceStatuses(profileId, taxPeriod);
    return new PromotionResult(invoices, bank);
  }

  private void updateSourceStatuses(long profileId, LocalDate taxPeriod) {
    Map<Long, Boolean> sourcesComplete = new HashMap<>();
    repository
        .invoices(profileId, taxPeriod)
        .forEach(
            row ->
                sourcesComplete.merge(
                    row.sourceId(),
                    row.status() == StagingReconciliationStatus.PROMOTED,
                    Boolean::logicalAnd));
    repository
        .bankTransactions(profileId, taxPeriod)
        .forEach(
            row ->
                sourcesComplete.merge(
                    row.sourceId(),
                    row.status() == StagingReconciliationStatus.PROMOTED,
                    Boolean::logicalAnd));
    sourcesComplete.forEach(
        (sourceId, complete) ->
            sources.status(
                sourceId,
                complete ? AccountingSourceStatus.IMPORTED : AccountingSourceStatus.REVIEW_REQUIRED,
                complete ? null : "Source has staging rows requiring reconciliation"));
  }

  public record PromotionResult(int invoices, int bankTransactions) {}
}
