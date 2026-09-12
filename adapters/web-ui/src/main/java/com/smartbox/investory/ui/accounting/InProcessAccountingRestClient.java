package com.smartbox.investory.ui.accounting;

import com.smartbox.investory.accounting.api.AccountingUserApi;
import java.time.YearMonth;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class InProcessAccountingRestClient implements AccountingRestClient {
  private final AccountingUserApi api;

  public InProcessAccountingRestClient(@Qualifier("accountingUserFacade") AccountingUserApi api) {
    this.api = api;
  }

  public List<MonthRef> months(long p) {
    return api.months(p);
  }

  public MonthOverview overview(long p, YearMonth m) {
    return api.overview(p, m);
  }

  public List<IssueView> issues(long p, YearMonth m) {
    return api.issues(p, m);
  }

  public List<DocumentView> documents(long p, YearMonth m) {
    return api.documents(p, m);
  }

  public List<BankTransactionView> bankTransactions(long p, YearMonth m) {
    return api.bankTransactions(p, m);
  }

  public List<PaymentView> payments(long p, YearMonth m) {
    return api.payments(p, m);
  }

  public FilingView filings(long p, YearMonth m) {
    return api.filings(p, m);
  }

  public List<ReconciliationView> reconciliation(long p, YearMonth m) {
    return api.reconciliation(p, m);
  }

  public CandidateView recognize(long p, String f, String c, byte[] b) {
    return api.recognize(p, f, c, b);
  }

  public void saveReviewed(long p, ReviewedDocument d) {
    api.saveReviewed(p, d);
  }

  public void importBank(long p, String f, String c, byte[] b, YearMonth m) {
    api.importBank(p, f, c, b, m);
  }

  public void confirm(long p, YearMonth m) {
    api.confirm(p, m);
  }

  public void file(long p, YearMonth m) {
    api.file(p, m);
  }

  public void settle(long p, YearMonth m) {
    api.settle(p, m);
  }

  public void lock(long p, YearMonth m) {
    api.lock(p, m);
  }

  public void reopen(long p, YearMonth m, String r) {
    api.reopen(p, m, r);
  }
}
