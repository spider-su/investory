package com.smartbox.investory.accounting.application;

import com.smartbox.investory.accounting.api.AccountingStagingApi;
import com.smartbox.investory.accounting.infrastructure.persistence.AccountingStagingRepository;
import com.smartbox.investory.accounting.staging.AccountingStagingPromotionService;
import com.smartbox.investory.accounting.staging.AccountingStagingReconciliationService;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountingStagingFacade implements AccountingStagingApi {
  private final AccountingStagingRepository repository;
  private final AccountingStagingReconciliationService reconciliation;
  private final AccountingStagingPromotionService promotion;

  private void profile(long profileId) {
    if (profileId <= 0 || !repository.profileExists(profileId))
      throw new IllegalArgumentException("Unknown accounting profile");
  }

  @Override
  public Summary summary(long profileId, YearMonth month) {
    profile(profileId);
    return map(reconciliation.summary(profileId, month.atDay(1)));
  }

  @Override
  public java.util.List<Row> rows(long profileId, YearMonth month) {
    profile(profileId);
    var period = month.atDay(1);
    var result = new java.util.ArrayList<Row>();
    repository
        .invoices(profileId, period)
        .forEach(
            row ->
                result.add(
                    new Row(
                        "INVOICE",
                        row.id(),
                        row.reference(),
                        row.sourceReference(),
                        row.status().name(),
                        row.reasonCodes(),
                        row.canonicalId(),
                        row.grossAmount(),
                        row.currency(),
                        row.status().name().equals("PROMOTED"),
                        row.sourceType(),
                        row.documentKind(),
                        row.documentDate(),
                        row.counterpartyName(),
                        null,
                        row.counterpartyTaxIdentifier(),
                        row.counterpartyCountry())));
    repository
        .bankTransactions(profileId, period)
        .forEach(
            row ->
                result.add(
                    new Row(
                        "BANK",
                        row.id(),
                        row.externalTransactionId(),
                        row.sourceReference(),
                        row.status().name(),
                        row.reasonCodes(),
                        row.canonicalId(),
                        row.amount(),
                        row.currency(),
                        row.status().name().equals("PROMOTED"))));
    return result;
  }

  @Override
  public Summary reconcile(long profileId, YearMonth month) {
    profile(profileId);
    return map(reconciliation.reconcile(profileId, month.atDay(1)));
  }

  @Override
  public Promotion promote(long profileId, YearMonth month) {
    profile(profileId);
    var result = promotion.promoteNew(profileId, month.atDay(1));
    return new Promotion(result.invoices(), result.bankTransactions());
  }

  private Summary map(AccountingStagingReconciliationService.Summary s) {
    return new Summary(
        s.invoiceMatched(),
        s.invoiceNew(),
        s.invoiceMismatch(),
        s.invoiceAmbiguous(),
        s.bankMatched(),
        s.bankNew(),
        s.bankMismatch(),
        s.bankAmbiguous(),
        s.readyToPromote(),
        s.blockingCount());
  }
}
