package com.smartbox.investory.accounting.staging;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountingStagingPromotionService {
  private final AccountingStagingRepository repository;
  private final AccountingStagingReconciliationService reconciliation;

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
      repository.promoted("bank_transaction", row.id(), repository.promoteBank(row));
      bank++;
    }
    return new PromotionResult(invoices, bank);
  }

  public record PromotionResult(int invoices, int bankTransactions) {}
}
