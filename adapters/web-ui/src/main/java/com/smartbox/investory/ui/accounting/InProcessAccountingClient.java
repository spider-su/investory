package com.smartbox.investory.ui.accounting;

import com.smartbox.investory.accounting.api.AccountingStagingApi;
import com.smartbox.investory.accounting.api.AccountingUserApi;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** In-process UI adapter. It preserves the application API without a loopback HTTP hop. */
@Component
public final class InProcessAccountingClient implements AccountingRestClient {
  private final AccountingUserApi user;
  private final AccountingStagingApi staging;

  public InProcessAccountingClient(
      @Qualifier("accountingUserFacade") AccountingUserApi user,
      @Qualifier("accountingStagingFacade") AccountingStagingApi staging) {
    this.user = user;
    this.staging = staging;
  }

  @Override
  public List<MonthRef> months(long p) {
    return user.months(p);
  }

  @Override
  public MonthOverview overview(long p, YearMonth m) {
    return user.overview(p, m);
  }

  @Override
  public List<IssueView> issues(long p, YearMonth m) {
    return user.issues(p, m);
  }

  @Override
  public List<DocumentView> documents(long p, YearMonth m) {
    return user.documents(p, m);
  }

  @Override
  public List<BankTransactionView> bankTransactions(long p, YearMonth m) {
    return user.bankTransactions(p, m);
  }

  @Override
  public List<PaymentView> payments(long p, YearMonth m) {
    return user.payments(p, m);
  }

  @Override
  public FilingView filings(long p, YearMonth m) {
    return user.filings(p, m);
  }

  @Override
  public List<ReconciliationView> reconciliation(long p, YearMonth m) {
    return user.reconciliation(p, m);
  }

  @Override
  public CandidateView recognize(long p, String f, String c, byte[] b) {
    return user.recognize(p, f, c, b);
  }

  @Override
  public CandidateView reviewSource(long p, String sourceReference) {
    return user.reviewSource(p, sourceReference);
  }

  @Override
  public void saveReviewed(long p, ReviewedDocument d) {
    user.saveReviewed(p, d);
  }

  @Override
  public void importBank(long p, String f, String c, byte[] b, YearMonth m) {
    user.importBank(p, f, c, b, m);
  }

  @Override
  public KsefSyncResult syncKsef(long p, YearMonth m) {
    return user.syncKsef(p, m);
  }

  @Override
  public KsefSyncResult syncKsefSeller(long p, YearMonth m) {
    return user.syncKsefSeller(p, m);
  }

  @Override
  public KsefSyncResult syncKsefThirdParty(long p, YearMonth m) {
    return user.syncKsefThirdParty(p, m);
  }

  @Override
  public FilingArtifactView generateJpk(long p, YearMonth m) {
    return user.generateJpk(p, m);
  }

  @Override
  public Optional<FilingArtifactView> filingArtifact(long p, YearMonth m) {
    return user.filingArtifact(p, m);
  }

  @Override
  public void recordConfirmation(long p, ConfirmationInput c) {
    user.recordConfirmation(p, c);
  }

  @Override
  public void confirm(long p, YearMonth m) {
    user.confirm(p, m);
  }

  @Override
  public void file(long p, YearMonth m) {
    user.file(p, m);
  }

  @Override
  public void settle(long p, YearMonth m) {
    user.settle(p, m);
  }

  @Override
  public void lock(long p, YearMonth m) {
    user.lock(p, m);
  }

  @Override
  public void reopen(long p, YearMonth m, String r) {
    user.reopen(p, m, r);
  }

  @Override
  public AccountingStagingApi.Summary summary(long p, YearMonth m) {
    return staging.summary(p, m);
  }

  @Override
  public List<Row> rows(long p, YearMonth m) {
    return staging.rows(p, m);
  }

  @Override
  public AccountingStagingApi.Summary reconcile(long p, YearMonth m) {
    return staging.reconcile(p, m);
  }

  @Override
  public Promotion promote(long p, YearMonth m) {
    return staging.promote(p, m);
  }

  @Override
  public byte[] downloadJpk(long p, YearMonth m) {
    return user.filingArtifact(p, m)
        .orElseThrow(() -> new IllegalStateException("JPK artifact was not persisted"))
        .content();
  }
}
