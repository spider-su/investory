package com.smartbox.investory.ui.accounting;

import com.smartbox.investory.accounting.api.AccountingStagingApi;
import com.smartbox.investory.accounting.api.AccountingUserApi;
import com.smartbox.investory.ryczalt.application.RyczaltInvoiceRecognitionService;
import com.smartbox.investory.ryczalt.application.query.RyczaltPeriodNotFoundException;
import com.smartbox.investory.ryczalt.web.InvoiceResponse;
import com.smartbox.investory.ryczalt.web.RyczaltAccountingRestController;
import com.smartbox.investory.ryczalt.web.RyczaltCounterpartyRestController;
import com.smartbox.investory.ryczalt.web.RyczaltInvoiceRecognitionRestController;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Simulated REST client: Web calls native Ryczalt controllers without a loopback HTTP hop. */
@Component
@Primary
public final class InProcessRyczaltControllerAccountingClient implements AccountingRestClient {
  private final RyczaltAccountingRestController accounting;
  private final RyczaltCounterpartyRestController counterparties;
  private final RyczaltInvoiceRecognitionRestController recognition;
  private final AccountingUserApi accountingReference;

  public InProcessRyczaltControllerAccountingClient(
      RyczaltAccountingRestController accounting,
      RyczaltCounterpartyRestController counterparties,
      RyczaltInvoiceRecognitionRestController recognition,
      @Qualifier("legacyAccountingApiBridge") AccountingUserApi accountingReference) {
    this.accounting = accounting;
    this.counterparties = counterparties;
    this.recognition = recognition;
    this.accountingReference = accountingReference;
  }

  @Override
  public List<MonthRef> months(long profileId) {
    return accounting.periods(profileId, auth()).stream()
        .map(
            value ->
                new MonthRef(
                    value.month(),
                    value.month().toString(),
                    value.status().name(),
                    value.status().name()))
        .toList();
  }

  @Override
  public MonthOverview overview(long profileId, YearMonth month) {
    try {
      var value = accounting.period(profileId, month, auth());
      var summary = value.summary();
      var settlement = value.settlement();
      return new MonthOverview(
          value.month(),
          value.status().name(),
          value.status().name(),
          value.allowedActions().stream().map(Enum::name).findFirst().orElse(null),
          null,
          new AccountingUserApi.Summary(
              decimal(summary.revenue()),
              decimal(summary.vat()),
              decimal(summary.ryczalt()),
              decimal(summary.zus()),
              value.documents().invoiceCount(),
              value.documents().transactionCount(),
              decimal(settlement.totalExpected())),
          issues(profileId, month),
          new SourceSummary(0, 0, value.completeness().blockingIssueCount(), 0),
          null,
          new DocumentSummary(
              0, 0, value.documents().invoiceCount(), value.completeness().blockingIssueCount(), 0),
          new BankSummary(value.documents().transactionCount(), 0, "NATIVE"),
          new PaymentSummary(
              settlement.expectedCount(),
              settlement.outstandingCount(),
              decimal(settlement.totalOutstanding()),
              payments(profileId, month)),
          unsupportedFilingSummary(),
          new ReconciliationSummary(
              value.reconciliation().rowCount(),
              value.reconciliation().settledCount(),
              value.reconciliation().mismatchCount(),
              value.reconciliation().missingEvidenceCount()),
          value.allowedActions().stream().map(Enum::name).toList(),
          reference(profileId, month));
    } catch (RyczaltPeriodNotFoundException missing) {
      return emptyOverview(month);
    }
  }

  private ReferenceSummary reference(long profileId, YearMonth month) {
    var value = accountingReference.overview(profileId, month).reference();
    return value == null
        ? new ReferenceSummary(false, null, null, null, null, null, null, null, 0, 0, null)
        : value;
  }

  @Override
  public List<IssueView> issues(long profileId, YearMonth month) {
    return accounting.issues(profileId, month, auth()).stream().map(this::issue).toList();
  }

  @Override
  public List<DocumentView> documents(long profileId, YearMonth month) {
    return accounting.invoices(profileId, month, auth()).stream().map(this::invoice).toList();
  }

  @Override
  public List<BankTransactionView> bankTransactions(long profileId, YearMonth month) {
    return accounting.transactions(profileId, month, auth()).stream()
        .map(
            value ->
                new BankTransactionView(
                    value.id(),
                    value.bookingDate(),
                    value.reference(),
                    decimal(value.amount()),
                    value.currency().name(),
                    "NATIVE"))
        .toList();
  }

  @Override
  public List<PaymentView> payments(long profileId, YearMonth month) {
    return accounting.obligations(profileId, month, auth()).stream()
        .map(
            value ->
                new PaymentView(
                    value.type().name(),
                    decimal(value.expectedAmount()),
                    decimal(value.expectedAmount()),
                    decimal(value.paidAmount()),
                    decimal(value.outstandingAmount()),
                    value.dueDate(),
                    null,
                    null,
                    value.status().name()))
        .toList();
  }

  @Override
  public List<PaymentHistoryView> paymentHistory(
      long profileId, YearMonth from, YearMonth to, String type) {
    return accounting.paymentHistory(profileId, from, to, type, auth()).stream()
        .map(
            value ->
                new PaymentHistoryView(
                    value.type().name(),
                    value.period(),
                    decimal(value.expectedAmount()),
                    decimal(value.paidAmount()),
                    decimal(value.outstandingAmount()),
                    value.dueDate(),
                    value.paymentDate(),
                    value.status().name()))
        .toList();
  }

  @Override
  public FilingView filings(long profileId, YearMonth month) {
    return new FilingView(
        "NOT_SUPPORTED",
        "Not managed by Ryczalt",
        false,
        false,
        List.of(),
        null,
        null,
        null,
        null,
        null);
  }

  @Override
  public List<ReconciliationView> reconciliation(long profileId, YearMonth month) {
    var invoicePayments =
        accountingReference.reconciliation(profileId, month).stream()
            .filter(
                row -> "INVOICE_PAYMENT".equals(row.kind()) || "EXPENSE_PAYMENT".equals(row.kind()))
            .toList();
    var obligationPayments =
        accounting.obligations(profileId, month, auth()).stream()
            .map(
                value -> {
                  var outstanding = decimal(value.outstandingAmount());
                  return new ReconciliationView(
                      value.type().name(),
                      "OBLIGATION_PAYMENT",
                      decimal(value.expectedAmount()),
                      decimal(value.paidAmount()),
                      outstanding == null || outstanding.signum() != 0 ? "OPEN" : "MATCHED",
                      "Native Ryczalt obligation",
                      value.currency().name(),
                      null);
                })
            .toList();
    return java.util.stream.Stream.concat(invoicePayments.stream(), obligationPayments.stream())
        .toList();
  }

  @Override
  public List<CounterpartyView> counterparties(long profileId) {
    return counterparties.list(profileId, auth()).stream()
        .map(
            value ->
                new CounterpartyView(
                    value.id(),
                    value.taxIdentifier(),
                    value.country(),
                    value.legalName(),
                    value.alias(),
                    (int) value.invoiceCount()))
        .toList();
  }

  @Override
  public List<CounterpartyDocumentView> counterpartyDocuments(long profileId, long id) {
    return accounting.invoicesByCounterparty(profileId, null, id, auth()).stream()
        .map(
            value ->
                new CounterpartyDocumentView(
                    YearMonth.from(value.accountingDate()), invoice(value)))
        .toList();
  }

  @Override
  public void updateCounterpartyAlias(long profileId, long id, String alias) {
    counterparties.alias(
        profileId, id, new RyczaltCounterpartyRestController.AliasRequest(alias), auth());
  }

  @Override
  public CandidateView recognize(
      long profileId, String filename, String contentType, byte[] content) {
    try {
      var value =
          recognition.recognize(
              profileId, new ByteArrayMultipartFile(filename, contentType, content), auth());
      return candidate(value);
    } catch (Exception exception) {
      throw new IllegalStateException("Native invoice recognition failed", exception);
    }
  }

  @Override
  public CandidateView reviewSource(long p, String s) {
    throw unsupported("legacy source review; use candidateKey");
  }

  @Override
  public void saveReviewed(long p, ReviewedDocument d) {
    throw unsupported("legacy reviewed document save; use native candidate approval");
  }

  @Override
  public void importBank(long p, String f, String c, byte[] b, YearMonth m) {
    throw unsupported("bank import");
  }

  @Override
  public KsefSyncResult syncKsef(long p, YearMonth m) {
    throw unsupported("KSeF");
  }

  @Override
  public KsefSyncResult reimportKsef(long p, YearMonth m) {
    throw unsupported("KSeF");
  }

  @Override
  public KsefSyncResult syncKsefSeller(long p, YearMonth m) {
    throw unsupported("KSeF");
  }

  @Override
  public KsefSyncResult syncKsefThirdParty(long p, YearMonth m) {
    throw unsupported("KSeF");
  }

  @Override
  public FilingArtifactView generateJpk(long p, YearMonth m) {
    throw unsupported("filing");
  }

  @Override
  public Optional<FilingArtifactView> filingArtifact(long p, YearMonth m) {
    return Optional.empty();
  }

  @Override
  public void recordConfirmation(long p, ConfirmationInput c) {
    throw unsupported("filing");
  }

  @Override
  public void confirm(long p, YearMonth m) {
    throw unsupported("filing confirmation");
  }

  @Override
  public void file(long p, YearMonth m) {
    throw unsupported("filing");
  }

  @Override
  public void settle(long p, YearMonth m) {
    accounting.settle(p, m, auth());
  }

  @Override
  public void lock(long p, YearMonth m) {
    accounting.freeze(
        p, m, new RyczaltAccountingRestController.LifecycleRequest("Web freeze"), auth());
  }

  @Override
  public void reopen(long p, YearMonth m, String reason) {
    accounting.reopen(p, m, new RyczaltAccountingRestController.LifecycleRequest(reason), auth());
  }

  @Override
  public AccountingStagingApi.Summary summary(long p, YearMonth m) {
    return emptyStagingSummary();
  }

  @Override
  public List<Row> rows(long p, YearMonth m) {
    return List.of();
  }

  @Override
  public AccountingStagingApi.Summary reconcile(long p, YearMonth m) {
    return emptyStagingSummary();
  }

  @Override
  public Promotion promote(long p, YearMonth m) {
    throw unsupported("legacy staging");
  }

  @Override
  public AutoApprovalSettings autoApprovalSettings(long p) {
    throw unsupported("global auto-approval");
  }

  @Override
  public void updateAutoApprovalSettings(long p, AutoApprovalSettings s) {
    throw unsupported("global auto-approval");
  }

  @Override
  public byte[] downloadJpk(long p, YearMonth m) {
    throw unsupported("filing");
  }

  private DocumentView invoice(InvoiceResponse value) {
    return new DocumentView(
        value.id(),
        value.reference(),
        "INCOME".equals(value.direction()) ? "SALE" : "PURCHASE",
        value.accountingDate(),
        decimal(value.grossAmount()),
        value.currency().name(),
        value.approvalStatus().name(),
        value.classification(),
        value.counterparty() == null ? null : value.counterparty().legalName(),
        null,
        value.issueDate(),
        null,
        null,
        "NATIVE",
        null);
  }

  private IssueView issue(com.smartbox.investory.ryczalt.web.IssueResponse value) {
    var legacyKind =
        switch (value.kind()) {
          case SETTLEMENT -> AccountingUserApi.IssueKind.BLOCKED;
          default -> AccountingUserApi.IssueKind.valueOf(value.kind().name());
        };
    return new IssueView(
        value.id(),
        value.code(),
        value.severity().name(),
        legacyKind,
        value.title(),
        value.message(),
        value.sourceReference(),
        new Resolution.None(value.message()));
  }

  private CandidateView candidate(RyczaltInvoiceRecognitionService.CandidateView value) {
    return new CandidateView(
        value.sourceExternalId(),
        value.documentType(),
        value.issueDate(),
        value.saleDate(),
        value.dueDate(),
        value.reference(),
        null,
        null,
        null,
        null,
        value.classification(),
        value.currency(),
        decimal(value.netAmount()),
        decimal(value.vatAmount()),
        decimal(value.grossAmount()),
        null,
        value.approvalStatus().name());
  }

  private static FilingSummary unsupportedFilingSummary() {
    return new FilingSummary(
        "NOT_SUPPORTED", "Not managed by Ryczalt", false, List.of(), null, null, null, null, null);
  }

  private static MonthOverview emptyOverview(YearMonth month) {
    return new MonthOverview(
        month,
        "OPEN",
        "OPEN",
        null,
        null,
        new AccountingUserApi.Summary(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            0,
            0,
            BigDecimal.ZERO),
        List.of(),
        new SourceSummary(0, 0, 0, 0),
        null,
        new DocumentSummary(0, 0, 0, 0, 0),
        new BankSummary(0, 0, "NATIVE"),
        new PaymentSummary(0, 0, BigDecimal.ZERO),
        unsupportedFilingSummary(),
        new ReconciliationSummary(0, 0, 0, 0),
        List.of(),
        new ReferenceSummary(false, null, null, null, null, null, null, null, 0, 0, null));
  }

  private static AccountingStagingApi.Summary emptyStagingSummary() {
    return new AccountingStagingApi.Summary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
  }

  private static Authentication auth() {
    return SecurityContextHolder.getContext().getAuthentication();
  }

  private static RuntimeException unsupported(String feature) {
    return new UnsupportedOperationException(feature + " is not part of native Ryczalt accounting");
  }

  private static BigDecimal decimal(String value) {
    return value == null ? null : new BigDecimal(value);
  }

  private static final class ByteArrayMultipartFile implements MultipartFile {
    private final String filename;
    private final String contentType;
    private final byte[] content;

    private ByteArrayMultipartFile(String filename, String contentType, byte[] content) {
      this.filename = filename;
      this.contentType = contentType;
      this.content = content;
    }

    @Override
    public String getName() {
      return "file";
    }

    @Override
    public String getOriginalFilename() {
      return filename;
    }

    @Override
    public String getContentType() {
      return contentType;
    }

    @Override
    public boolean isEmpty() {
      return content.length == 0;
    }

    @Override
    public long getSize() {
      return content.length;
    }

    @Override
    public byte[] getBytes() {
      return content;
    }

    @Override
    public java.io.InputStream getInputStream() {
      return new java.io.ByteArrayInputStream(content);
    }

    @Override
    public void transferTo(java.io.File destination) throws java.io.IOException {
      java.nio.file.Files.write(destination.toPath(), content);
    }
  }
}
