package com.smartbox.investory.accounting.application;

import com.smartbox.investory.accounting.api.AccountingUserApi;
import com.smartbox.investory.ryczalt.application.RyczaltAccountingApi;
import com.smartbox.investory.ryczalt.application.bank.RyczaltBankApi;
import com.smartbox.investory.ryczalt.application.ksef.RyczaltKsefApi;
import com.smartbox.investory.ryczalt.application.ksef.RyczaltKsefSyncResult;
import com.smartbox.investory.ryczalt.integration.ksef.KsefSyncMode;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** App-owned compatibility boundary while old accounting routes are still public. */
@Service("legacyAccountingApiBridge")
public class LegacyAccountingApiBridge implements AccountingUserApi {
  private final AccountingUserApi legacy;
  private final RyczaltAccountingApi ryczalt;
  private final RyczaltBankApi ryczaltBank;
  private final RyczaltKsefApi ryczaltKsef;

  public LegacyAccountingApiBridge(
      @Qualifier("accountingUserFacade") AccountingUserApi legacy,
      RyczaltAccountingApi ryczalt,
      RyczaltBankApi ryczaltBank,
      RyczaltKsefApi ryczaltKsef) {
    this.legacy = legacy;
    this.ryczalt = ryczalt;
    this.ryczaltBank = ryczaltBank;
    this.ryczaltKsef = ryczaltKsef;
  }

  private boolean nativePeriod(long profileId, YearMonth month) {
    return ryczalt.hasPeriod(profileId, month);
  }

  @Override
  public List<MonthRef> months(long profileId) {
    var legacyMonths = legacy.months(profileId);
    var nativeMonths = ryczalt.periods(profileId);
    Map<YearMonth, MonthRef> merged = new LinkedHashMap<>();
    legacyMonths.forEach(month -> merged.put(month.month(), month));
    nativeMonths.forEach(
        period ->
            merged.put(
                period.month(),
                new MonthRef(
                    period.month(),
                    period.month().getMonth() + " " + period.month().getYear(),
                    period.status().name(),
                    period.status().name())));
    return merged.values().stream().sorted(Comparator.comparing(MonthRef::month)).toList();
  }

  @Override
  public MonthOverview overview(long profileId, YearMonth month) {
    if (!nativePeriod(profileId, month)) return legacy.overview(profileId, month);
    var period = ryczalt.period(profileId, month);
    var invoices = ryczalt.invoices(profileId, month);
    var transactions = ryczalt.transactions(profileId, month);
    var obligations = ryczalt.obligations(profileId, month);
    var issues = ryczalt.issues(profileId, month).stream().map(this::issue).toList();
    var documents = invoices.stream().map(this::document).toList();
    var bank = transactions.stream().map(this::bankTransaction).toList();
    var payments = obligations.stream().map(this::payment).toList();
    var summary =
        new Summary(
            period.revenue(),
            period.vatAmount(),
            period.ryczaltAmount(),
            period.zusAmount(),
            invoices.size(),
            transactions.size(),
            period.totalObligations());
    var paymentSummary =
        new PaymentSummary(
            obligations.size(),
            (int) obligations.stream().filter(o -> o.outstandingAmount().signum() > 0).count(),
            obligations.stream()
                .map(o -> o.outstandingAmount())
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add),
            payments);
    var documentSummary =
        new DocumentSummary(
            (int) invoices.stream().filter(i -> i.direction().name().equals("INCOME")).count(),
            (int) invoices.stream().filter(i -> !i.direction().name().equals("INCOME")).count(),
            invoices.size(),
            0,
            0);
    return new MonthOverview(
        period.month(),
        period.periodStatus().name(),
        period.periodStatus().name(),
        null,
        null,
        summary,
        issues,
        new SourceSummary(0, 0, 0, 0),
        null,
        documentSummary,
        new BankSummary(bank.size(), 0, "NATIVE"),
        paymentSummary,
        null,
        null,
        period.allowedActions().stream().map(Enum::name).toList(),
        null);
  }

  @Override
  public List<IssueView> issues(long p, YearMonth m) {
    return nativePeriod(p, m)
        ? ryczalt.issues(p, m).stream().map(this::issue).toList()
        : legacy.issues(p, m);
  }

  @Override
  public List<DocumentView> documents(long p, YearMonth m) {
    return nativePeriod(p, m)
        ? ryczalt.invoices(p, m).stream().map(this::document).toList()
        : legacy.documents(p, m);
  }

  @Override
  public List<BankTransactionView> bankTransactions(long p, YearMonth m) {
    return nativePeriod(p, m)
        ? ryczalt.transactions(p, m).stream().map(this::bankTransaction).toList()
        : legacy.bankTransactions(p, m);
  }

  @Override
  public List<PaymentView> payments(long p, YearMonth m) {
    return nativePeriod(p, m)
        ? ryczalt.obligations(p, m).stream().map(this::payment).toList()
        : legacy.payments(p, m);
  }

  @Override
  public List<PaymentHistoryView> paymentHistory(long p, YearMonth f, YearMonth t, String type) {
    var nativePeriods =
        ryczalt.periods(p).stream()
            .map(period -> period.month())
            .filter(month -> !month.isBefore(f) && !month.isAfter(t))
            .collect(java.util.stream.Collectors.toSet());
    var merged = new LinkedHashMap<PaymentHistoryKey, PaymentHistoryView>();
    legacy.paymentHistory(p, f, t, type).stream()
        .filter(result -> !nativePeriods.contains(result.period()))
        .forEach(
            result -> merged.put(new PaymentHistoryKey(result.period(), result.type()), result));
    ryczalt.paymentHistory(p, f, t, type).stream()
        .map(this::paymentHistory)
        .forEach(
            result -> merged.put(new PaymentHistoryKey(result.period(), result.type()), result));
    return merged.values().stream()
        .sorted(
            Comparator.comparing(PaymentHistoryView::period)
                .thenComparing(PaymentHistoryView::type))
        .toList();
  }

  private IssueView issue(
      com.smartbox.investory.ryczalt.application.query.RyczaltIssueReadModel source) {
    return new IssueView(
        source.code(), source.severity().name(), source.code(), source.context(), null);
  }

  private DocumentView document(
      com.smartbox.investory.ryczalt.application.query.RyczaltInvoiceReadModel source) {
    return new DocumentView(
        source.id(),
        source.reference(),
        source.direction().name(),
        source.issueDate(),
        source.grossAmount(),
        source.currency().name(),
        "NATIVE",
        null,
        null,
        null,
        source.issueDate(),
        null,
        null,
        "RYCZALT",
        "Ryczalt");
  }

  private BankTransactionView bankTransaction(
      com.smartbox.investory.ryczalt.application.query.RyczaltTransactionReadModel source) {
    return new BankTransactionView(
        source.id(),
        source.bookingDate(),
        source.reference(),
        source.amount(),
        source.currency().name(),
        "NATIVE");
  }

  private PaymentView payment(
      com.smartbox.investory.ryczalt.application.query.RyczaltObligationReadModel source) {
    return new PaymentView(
        source.type().name(),
        source.expectedAmount(),
        source.expectedAmount(),
        source.paidAmount(),
        source.outstandingAmount(),
        source.dueDate(),
        null,
        null,
        source.status().name());
  }

  private PaymentHistoryView paymentHistory(
      com.smartbox.investory.ryczalt.application.query.RyczaltPaymentHistoryReadModel source) {
    return new PaymentHistoryView(
        source.type().name(),
        source.period(),
        source.expectedAmount(),
        source.paidAmount(),
        source.outstandingAmount(),
        source.dueDate(),
        source.paymentDate(),
        source.status().name());
  }

  private record PaymentHistoryKey(YearMonth period, String type) {}

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
  public void updateCounterpartyAlias(long p, long id, String a) {
    legacy.updateCounterpartyAlias(p, id, a);
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
  public CandidateView reviewSource(long p, String s) {
    return legacy.reviewSource(p, s);
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
    // Native acquisition derives the accounting period from each source transaction; the legacy
    // target-month hint is intentionally ignored.
    ryczaltBank.importBank(p, b, f, c);
  }

  @Override
  public KsefSyncResult syncKsef(long p, YearMonth m) {
    return toLegacy(ryczaltKsef.sync(p, m, Set.of(KsefSyncMode.PURCHASES, KsefSyncMode.SALES)));
  }

  @Override
  public KsefSyncResult reimportKsef(long p, YearMonth m) {
    return toLegacy(ryczaltKsef.reimport(p, m));
  }

  @Override
  public KsefSyncResult syncKsefSeller(long p, YearMonth m) {
    return toLegacy(ryczaltKsef.sync(p, m, Set.of(KsefSyncMode.SALES)));
  }

  @Override
  public KsefSyncResult syncKsefThirdParty(long p, YearMonth m) {
    // Subject3 evidence is review-only with no canonical import; it stays legacy by design.
    return legacy.syncKsefThirdParty(p, m);
  }

  private static KsefSyncResult toLegacy(RyczaltKsefSyncResult result) {
    return new KsefSyncResult(
        "OK",
        result.received(),
        result.imported() + result.updated(),
        result.duplicates(),
        0,
        result.failed(),
        "Native Ryczalt KSeF acquisition");
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
    if (nativePeriod(p, m)) ryczalt.settle(p, m);
    else legacy.settle(p, m);
  }

  @Override
  public void lock(long p, YearMonth m) {
    if (nativePeriod(p, m)) ryczalt.freeze(p, m, "REST", "REST lock");
    else legacy.lock(p, m);
  }

  @Override
  public void reopen(long p, YearMonth m, String r) {
    if (nativePeriod(p, m)) ryczalt.reopen(p, m, "REST", r);
    else legacy.reopen(p, m, r);
  }
}
