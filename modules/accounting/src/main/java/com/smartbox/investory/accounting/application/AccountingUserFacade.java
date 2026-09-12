package com.smartbox.investory.accounting.application;

import com.smartbox.investory.accounting.*;
import com.smartbox.investory.accounting.AccountingInvoiceRecognitionService.RecognizedInvoice;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.BankRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.ExpenseRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.InvoiceRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.ReconciliationRow;
import com.smartbox.investory.accounting.api.AccountingUserApi;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountingUserFacade implements AccountingUserApi {
  private final AccountingFactService facts;
  private final AccountingFilingService filing;
  private final AccountingPocRepository repository;
  private final AccountingSourceEvidenceService sources;
  private final AccountingInvoiceRecognitionService recognition;
  private final AccountingInvoiceIngestionService ingestion;
  private final AccountingBankImportService bankImport;

  private void profile(long profileId) {
    if (profileId != 1)
      throw new IllegalArgumentException("Unknown accounting profile: " + profileId);
  }

  private java.time.LocalDate date(YearMonth month) {
    return Objects.requireNonNull(month).atDay(1);
  }

  @Override
  public List<MonthRef> months(long profileId) {
    profile(profileId);
    return facts.availablePeriods().stream()
        .map(
            d -> {
              var state = repository.periodState(d);
              var status = state == null ? PeriodLifecycleStatus.OPEN : state.lifecycleStatus();
              return new MonthRef(
                  YearMonth.from(d),
                  d.getMonth() + " " + d.getYear(),
                  status.name(),
                  label(status));
            })
        .toList();
  }

  @Override
  public MonthOverview overview(long profileId, YearMonth month) {
    profile(profileId);
    var snapshot = facts.snapshot(date(month));
    var state = repository.periodState(date(month));
    var lifecycle = state == null ? PeriodLifecycleStatus.OPEN : state.lifecycleStatus();
    var outcomes = sources.outcomes(date(month));
    var filingIssues = filing.filing(date(month)).issues();
    int imported = (int) outcomes.stream().filter(o -> "IMPORTED".equals(o.status())).count();
    int review = (int) outcomes.stream().filter(o -> "REVIEW_REQUIRED".equals(o.status())).count();
    int failed = (int) outcomes.stream().filter(o -> "FAILED".equals(o.status())).count();
    var issues = issues(snapshot, outcomes, filingIssues);
    var payments = paymentInstructions(date(month));
    var filingResult = filing.filing(date(month));
    var reconciliations = snapshot.reconciliations();
    return new MonthOverview(
        month,
        lifecycle.name(),
        label(lifecycle),
        next(lifecycle, issues),
        nextLabel(lifecycle, issues),
        new Summary(
            snapshot.totalBookedRevenuePln(),
            snapshot.vat().calculatedVat(),
            snapshot.ryczalt().calculatedTax(),
            snapshot.zus().totalZus(),
            snapshot.invoices().size() + snapshot.expenses().size(),
            snapshot.bankTransactions().size()),
        issues,
        new SourceSummary(imported, review, failed),
        new DocumentSummary(snapshot.invoices().size() + snapshot.expenses().size(), review, failed),
        new BankSummary(
            snapshot.bankTransactions().size(),
            (int) snapshot.bankTransactions().stream()
                .filter(row -> !"MATCHED".equalsIgnoreCase(row.status()))
                .count(),
            snapshot.bankTransactions().isEmpty() ? "NO_IMPORT" : "IMPORTED"),
        new PaymentSummary(
            payments.size(),
            (int) payments.stream()
                .filter(payment -> !"PAID".equalsIgnoreCase(payment.status()))
                .count(),
            payments.stream()
                .filter(payment -> !"PAID".equalsIgnoreCase(payment.status()))
                .map(com.smartbox.investory.accounting.AccountingPaymentInstruction::amount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)),
        new FilingSummary(
            lifecycle.name(), label(lifecycle), filingResult.ready(), filingResult.issues()),
        new ReconciliationSummary(
            reconciliations.size(),
            (int) reconciliations.stream().filter(row -> "SETTLED".equalsIgnoreCase(row.status())).count(),
            (int) reconciliations.stream().filter(row -> "MISMATCH".equalsIgnoreCase(row.status())).count(),
            (int) reconciliations.stream()
                .filter(row -> row.explanation() != null && row.explanation().toLowerCase().contains("evidence"))
                .count()),
        allowedActions(lifecycle, issues));
  }

  private List<AccountingPaymentInstruction> paymentInstructions(java.time.LocalDate period) {
    try {
      return filing.paymentInstructions(period);
    } catch (RuntimeException ignored) {
      return List.of();
    }
  }

  private static List<String> allowedActions(PeriodLifecycleStatus lifecycle, List<IssueView> issues) {
    if (!issues.isEmpty()) return List.of();
    return switch (lifecycle) {
      case OPEN, SOURCES_INCOMPLETE, ISSUES, READY_FOR_REVIEW -> List.of("CONFIRM");
      case CONFIRMED -> List.of("FILE");
      case FILED, PAID -> List.of("SETTLE");
      case SETTLED -> List.of("LOCK");
      case LOCKED -> List.of("REOPEN");
    };
  }

  private List<IssueView> issues(
      AccountingMonthSnapshot snapshot,
      List<AccountingSourceEvidenceService.SourceOutcome> outcomes,
      List<String> filingIssues) {
    var result = new java.util.ArrayList<IssueView>();
    snapshot
        .issues()
        .forEach(
            i ->
                result.add(
                    new IssueView(
                        i.type(),
                        i.severity(),
                        title(i.type()),
                        i.message(),
                        i.sourceReference())));
    outcomes.stream()
        .filter(o -> !"IMPORTED".equals(o.status()))
        .forEach(
            o ->
                result.add(
                    new IssueView(
                        "SOURCE_" + o.status(),
                        "WARNING",
                        "Source requires attention",
                        o.error() == null ? "Source was not imported." : o.error(),
                        o.reference())));
    filingIssues.stream()
        .filter(issue -> !issue.startsWith("Month calculation is not confirmed"))
        .filter(issue -> result.stream().noneMatch(existing -> existing.message().equals(issue)))
        .forEach(
            issue ->
                result.add(
                    new IssueView(
                        "FILING_READINESS", "BLOCKING", "Filing readiness", issue, null)));
    return result;
  }

  @Override
  public List<IssueView> issues(long p, YearMonth m) {
    return overview(p, m).issues();
  }

  @Override
  public List<DocumentView> documents(long p, YearMonth m) {
    profile(p);
    var s = facts.snapshot(date(m));
    return java.util.stream.Stream.concat(
            s.invoices().stream().map(this::document), s.expenses().stream().map(this::document))
        .toList();
  }

  private DocumentView document(InvoiceRow r) {
    return new DocumentView(
        r.id(),
        r.reference(),
        "SALES",
        r.issueDate(),
        r.grossAmount(),
        r.currency(),
        "IMPORTED",
        r.ksefNumber());
  }

  private DocumentView document(ExpenseRow r) {
    return new DocumentView(
        r.id(),
        r.reference(),
        "PURCHASE",
        r.invoiceDate(),
        r.grossAmount(),
        r.currency(),
        "IMPORTED",
        r.ksefNumber());
  }

  @Override
  public List<BankTransactionView> bankTransactions(long p, YearMonth m) {
    profile(p);
    return facts.snapshot(date(m)).bankTransactions().stream().map(this::bank).toList();
  }

  private BankTransactionView bank(BankRow r) {
    return new BankTransactionView(
        r.id(), r.bookingDate(), r.reference(), r.amount(), r.currency(), "IMPORTED");
  }

  @Override
  public List<PaymentView> payments(long p, YearMonth m) {
    profile(p);
    return paymentInstructions(date(m)).stream()
          .map(
              x ->
                  new PaymentView(
                      x.obligationType(),
                      x.amount(),
                      x.dueDate(),
                      x.recipient(),
                      x.account(),
                      x.status()))
          .toList();
  }

  @Override
  public FilingView filings(long p, YearMonth m) {
    profile(p);
    var f = filing.filing(date(m));
    var state = repository.periodState(date(m));
    return new FilingView(
        state == null ? "OPEN" : state.lifecycleStatus().name(),
        state == null ? label(PeriodLifecycleStatus.OPEN) : label(state.lifecycleStatus()),
        f.confirmed(),
        f.ready(),
        f.issues());
  }

  @Override
  public List<ReconciliationView> reconciliation(long p, YearMonth m) {
    profile(p);
    return facts.snapshot(date(m)).reconciliations().stream().map(this::reconciliation).toList();
  }

  private ReconciliationView reconciliation(ReconciliationRow r) {
    return new ReconciliationView(
        r.reference(),
        r.kind(),
        r.expectedAmount(),
        r.matchedAmount(),
        r.status(),
        r.explanation());
  }

  @Override
  public CandidateView recognize(long p, String filename, String contentType, byte[] content) {
    profile(p);
    long id = sources.receiveUpload(filename, contentType, content);
    try {
      RecognizedInvoice r = recognition.recognize(filename, contentType, content);
      sources.status(id, AccountingSourceStatus.PARSED, null);
      return new CandidateView(
          "sha256:" + hash(content),
          r.documentType(),
          r.issueDate(),
          r.saleDate(),
          r.dueDate(),
          r.reference(),
          r.seller(),
          r.buyer(),
          r.category(),
          r.currency(),
          r.netAmount(),
          r.vatAmount(),
          r.grossAmount(),
          r.note(),
          "PARSED");
    } catch (RuntimeException e) {
      sources.status(id, AccountingSourceStatus.FAILED, e.getMessage());
      throw e;
    }
  }

  @Override
  public void saveReviewed(long p, ReviewedDocument d) {
    profile(p);
    try {
      ingestion.ingest(
          new AccountingInvoiceIngestionService.ReviewedInvoice(
              d.taxPeriod().atDay(1),
              d.documentType(),
              d.issueDate(),
              d.saleDate(),
              d.reference(),
              d.counterpartyAlias(),
              d.category(),
              d.currency(),
              d.netAmount(),
              d.vatAmount(),
              d.grossAmount(),
              d.vatDeductionRatio(),
              "REVIEWED",
              d.note(),
              d.sourceReference(),
              d.counterpartyTaxIdentifier(),
              null,
              null,
              null));
      sources
          .findId(AccountingSourceType.UPLOAD, d.sourceReference())
          .ifPresent(id -> sources.status(id, AccountingSourceStatus.IMPORTED, null));
    } catch (RuntimeException exception) {
      sources
          .findId(AccountingSourceType.UPLOAD, d.sourceReference())
          .ifPresent(
              id ->
                  sources.status(
                      id, AccountingSourceStatus.REVIEW_REQUIRED, exception.getMessage()));
      throw exception;
    }
  }

  @Override
  public void importBank(long p, String f, String c, byte[] b, YearMonth m) {
    profile(p);
    bankImport.importFile(f, c, b, date(m));
  }

  @Override
  public void confirm(long p, YearMonth m) {
    profile(p);
    filing.confirm(date(m));
  }

  @Override
  public void file(long p, YearMonth m) {
    profile(p);
    filing.markFiled(date(m));
  }

  @Override
  public void settle(long p, YearMonth m) {
    profile(p);
    filing.settle(date(m));
  }

  @Override
  public void lock(long p, YearMonth m) {
    profile(p);
    filing.lock(date(m));
  }

  @Override
  public void reopen(long p, YearMonth m, String reason) {
    profile(p);
    filing.reopen(date(m), reason);
  }

  private String hash(byte[] value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(value == null ? new byte[0] : value));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String label(PeriodLifecycleStatus s) {
    return switch (s) {
      case OPEN -> "Open";
      case SOURCES_INCOMPLETE -> "Sources incomplete";
      case READY_FOR_REVIEW -> "Ready for review";
      case ISSUES -> "Issues found";
      case CONFIRMED -> "Confirmed";
      case FILED -> "Filed";
      case PAID -> "Paid";
      case SETTLED -> "Settled";
      case LOCKED -> "Locked";
    };
  }

  private static String next(PeriodLifecycleStatus s, List<IssueView> i) {
    if (!i.isEmpty()) return "REVIEW";
    return switch (s) {
      case OPEN, SOURCES_INCOMPLETE, ISSUES, READY_FOR_REVIEW -> "CONFIRM";
      case CONFIRMED -> "FILE";
      case FILED -> "PAY";
      case PAID -> "SETTLE";
      case SETTLED -> "LOCK";
      case LOCKED -> "NONE";
    };
  }

  private static String nextLabel(PeriodLifecycleStatus s, List<IssueView> i) {
    return switch (next(s, i)) {
      case "REVIEW" -> "Review issues";
      case "CONFIRM" -> "Confirm month";
      case "FILE" -> "Record filing";
      case "PAY" -> "Record payments";
      case "SETTLE" -> "Settle month";
      case "LOCK" -> "Lock month";
      default -> "No action";
    };
  }

  private static String title(String code) {
    return code == null ? "Accounting issue" : code.replace('_', ' ');
  }
}
