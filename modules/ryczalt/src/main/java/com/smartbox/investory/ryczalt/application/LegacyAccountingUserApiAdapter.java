package com.smartbox.investory.ryczalt.application;

import com.smartbox.investory.accounting.api.AccountingUserApi;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodLifecycleService;
import com.smartbox.investory.ryczalt.settlement.SettlementService;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Temporary Stage 7 bridge. It delegates unsupported operations only through the legacy public API.
 * Native settlement is selected when the period exists in the Ryczalt schema.
 */
@Service("ryczaltUserApi")
public class LegacyAccountingUserApiAdapter implements RyczaltUserApi {
  private final AccountingUserApi legacy;
  private final RyczaltPeriodJpaRepository periods;
  private final SettlementService settlement;
  private final RyczaltPeriodLifecycleService lifecycle;

  public LegacyAccountingUserApiAdapter(
      @Qualifier("accountingUserFacade") AccountingUserApi legacy,
      RyczaltPeriodJpaRepository periods,
      SettlementService settlement,
      RyczaltPeriodLifecycleService lifecycle) {
    this.legacy = legacy;
    this.periods = periods;
    this.settlement = settlement;
    this.lifecycle = lifecycle;
  }

  private boolean nativePeriod(long profileId, YearMonth month) {
    return periods
        .findByProfileIdAndYearAndMonth(profileId, month.getYear(), month.getMonthValue())
        .isPresent();
  }

  @Override
  public List<MonthRef> months(long p) {
    return legacy.months(p);
  }

  @Override
  public MonthOverview overview(long p, YearMonth m) {
    return legacy.overview(p, m);
  }

  @Override
  public List<IssueView> issues(long p, YearMonth m) {
    return legacy.issues(p, m);
  }

  @Override
  public List<DocumentView> documents(long p, YearMonth m) {
    return legacy.documents(p, m);
  }

  @Override
  public List<BankTransactionView> bankTransactions(long p, YearMonth m) {
    return legacy.bankTransactions(p, m);
  }

  @Override
  public List<PaymentView> payments(long p, YearMonth m) {
    return legacy.payments(p, m);
  }

  @Override
  public List<PaymentHistoryView> paymentHistory(long p, YearMonth f, YearMonth t, String type) {
    return legacy.paymentHistory(p, f, t, type);
  }

  @Override
  public FilingView filings(long p, YearMonth m) {
    return legacy.filings(p, m);
  }

  @Override
  public List<ReconciliationView> reconciliation(long p, YearMonth m) {
    return legacy.reconciliation(p, m);
  }

  @Override
  public List<CounterpartyView> counterparties(long p) {
    return legacy.counterparties(p);
  }

  @Override
  public List<CounterpartyDocumentView> counterpartyDocuments(long p, long id) {
    return legacy.counterpartyDocuments(p, id);
  }

  @Override
  public void updateCounterpartyAlias(long p, long id, String alias) {
    legacy.updateCounterpartyAlias(p, id, alias);
  }

  @Override
  public AutoApprovalSettings autoApprovalSettings(long p) {
    return legacy.autoApprovalSettings(p);
  }

  @Override
  public void updateAutoApprovalSettings(long p, AutoApprovalSettings s) {
    legacy.updateAutoApprovalSettings(p, s);
  }

  @Override
  public CandidateView recognize(long p, String f, String c, byte[] b) {
    return legacy.recognize(p, f, c, b);
  }

  @Override
  public CandidateView reviewSource(long p, String source) {
    return legacy.reviewSource(p, source);
  }

  @Override
  public void saveReviewed(long p, ReviewedDocument d) {
    legacy.saveReviewed(p, d);
  }

  @Override
  public DocumentMutationResult saveReviewedResult(long p, ReviewedDocument d) {
    return legacy.saveReviewedResult(p, d);
  }

  @Override
  public DocumentMutationResult issueInvoice(long p, InvoiceIssueRequest r) {
    return legacy.issueInvoice(p, r);
  }

  @Override
  public DocumentMutationResult recordManualIncome(long p, ManualIncomeRequest r) {
    return legacy.recordManualIncome(p, r);
  }

  @Override
  public void importBank(long p, String f, String c, byte[] b, YearMonth m) {
    legacy.importBank(p, f, c, b, m);
  }

  @Override
  public KsefSyncResult syncKsef(long p, YearMonth m) {
    return legacy.syncKsef(p, m);
  }

  @Override
  public KsefSyncResult reimportKsef(long p, YearMonth m) {
    return legacy.reimportKsef(p, m);
  }

  @Override
  public KsefSyncResult syncKsefSeller(long p, YearMonth m) {
    return legacy.syncKsefSeller(p, m);
  }

  @Override
  public KsefSyncResult syncKsefThirdParty(long p, YearMonth m) {
    return legacy.syncKsefThirdParty(p, m);
  }

  @Override
  public FilingArtifactView generateJpk(long p, YearMonth m) {
    return legacy.generateJpk(p, m);
  }

  @Override
  public Optional<FilingArtifactView> filingArtifact(long p, YearMonth m) {
    return legacy.filingArtifact(p, m);
  }

  @Override
  public void recordConfirmation(long p, ConfirmationInput i) {
    legacy.recordConfirmation(p, i);
  }

  @Override
  public void confirm(long p, YearMonth m) {
    legacy.confirm(p, m);
  }

  @Override
  public void file(long p, YearMonth m) {
    legacy.file(p, m);
  }

  @Override
  public void settle(long p, YearMonth m) {
    if (nativePeriod(p, m)) settlement.settlePeriod(p, m);
    else legacy.settle(p, m);
  }

  @Override
  public void lock(long p, YearMonth m) {
    if (nativePeriod(p, m)) {
      lifecycle.freeze(p, m, "REST", "REST lock");
      return;
    }
    legacy.lock(p, m);
  }

  @Override
  public void reopen(long p, YearMonth m, String reason) {
    if (nativePeriod(p, m)) {
      lifecycle.reopen(p, m, "REST", reason);
      return;
    }
    legacy.reopen(p, m, reason);
  }
}
