package com.smartbox.investory.accounting.application;

import com.smartbox.investory.accounting.*;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.BankRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.ExpenseRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.InvoiceRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.ReconciliationRow;
import com.smartbox.investory.accounting.api.AccountingKsefSyncPort;
import com.smartbox.investory.accounting.api.AccountingUserApi;
import com.smartbox.investory.accounting.infrastructure.persistence.AccountingPocRepository;
import com.smartbox.investory.accounting.infrastructure.persistence.AccountingSourceRepository;
import com.smartbox.investory.accounting.service.AccountingDocumentExtractionService;
import com.smartbox.investory.accounting.service.AccountingFactService;
import com.smartbox.investory.accounting.service.AccountingFilingService;
import com.smartbox.investory.accounting.service.AccountingInvoiceIngestionService;
import com.smartbox.investory.accounting.service.AccountingInvoiceRecognitionService;
import com.smartbox.investory.accounting.service.AccountingInvoiceRecognitionService.RecognizedInvoice;
import com.smartbox.investory.accounting.service.AccountingSourceEvidenceService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

@Service
@RequiredArgsConstructor
public class AccountingUserFacade implements AccountingUserApi {
  private static final String REQUEST_SNAPSHOT_CACHE =
      AccountingUserFacade.class.getName() + ".snapshots";
  private final AccountingFactService facts;
  private final AccountingFilingService filing;
  private final AccountingPocRepository repository;
  private final AccountingSourceEvidenceService sources;
  private final AccountingInvoiceRecognitionService recognition;
  private final AccountingDocumentExtractionService extraction;
  private final com.smartbox.investory.accounting.staging.AccountingStagingAcquisitionService
      staging;
  private final com.smartbox.investory.accounting.staging.AccountingStagingReconciliationService
      stagingReconciliation;
  private final com.smartbox.investory.accounting.staging.AccountingBankStagingImportService
      bankImport;
  private final Optional<AccountingKsefSyncPort> ksef;
  @Autowired private AccountingInvoiceIngestionService invoiceIngestion;
  private final AccountingPeriodLifecycle periodLifecycle = new AccountingPeriodLifecycle();

  private void profile(long profileId) {
    if (profileId <= 0 || !repository.profileExists(profileId))
      throw new IllegalArgumentException("Unknown accounting profile");
  }

  private java.time.LocalDate date(YearMonth month) {
    return Objects.requireNonNull(month).atDay(1);
  }

  @Override
  public List<MonthRef> months(long profileId) {
    profile(profileId);
    var states = repository.periodStates(profileId);
    return facts.availablePeriods(profileId).stream()
        .map(
            d -> {
              var state = states.get(d);
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
  public List<AccountingUserApi.CounterpartyView> counterparties(long profileId) {
    profile(profileId);
    return repository.counterparties(profileId);
  }

  @Override
  public List<AccountingUserApi.CounterpartyDocumentView> counterpartyDocuments(
      long profileId, long counterpartyId) {
    profile(profileId);
    return repository.counterpartyDocuments(profileId, counterpartyId).stream()
        .map(
            row ->
                new AccountingUserApi.CounterpartyDocumentView(
                    YearMonth.from(row.taxPeriod()), document(row.document())))
        .toList();
  }

  @Override
  public void updateCounterpartyAlias(long profileId, long counterpartyId, String alias) {
    profile(profileId);
    if (alias != null && alias.trim().length() > 128) {
      throw new IllegalArgumentException("Alias must be at most 128 characters");
    }
    repository.updateCounterpartyAlias(profileId, counterpartyId, alias);
  }

  @Override
  public AccountingUserApi.AutoApprovalSettings autoApprovalSettings(long profileId) {
    profile(profileId);
    return repository.autoApprovalSettings(profileId);
  }

  @Override
  public void updateAutoApprovalSettings(
      long profileId, AccountingUserApi.AutoApprovalSettings settings) {
    profile(profileId);
    if (settings == null || settings.maxAmount() == null || settings.maxAmount().signum() < 0) {
      throw new IllegalArgumentException("Auto-approval maximum amount must be non-negative");
    }
    if (settings.trustedCategories() == null
        || settings.trustedCategories().stream().anyMatch(c -> c == null || c.isBlank())) {
      throw new IllegalArgumentException("Trusted categories must not be blank");
    }
    repository.updateAutoApprovalSettings(profileId, settings);
  }

  @Override
  public MonthOverview overview(long profileId, YearMonth month) {
    profile(profileId);
    facts.warmMonthFx(month);
    var snapshotCache = new java.util.HashMap<LocalDate, AccountingMonthSnapshot>();
    var snapshot = snapshot(profileId, date(month));
    snapshotCache.put(date(month), snapshot);
    var state = repository.periodState(profileId, date(month));
    var lifecycle = state == null ? PeriodLifecycleStatus.OPEN : state.lifecycleStatus();
    var outcomes = sources.outcomes(profileId, date(month));
    var filingResult = filing.filing(profileId, date(month), snapshot);
    var filingIssues = filingResult.issues();
    var stagingState = stagingReconciliation.summary(profileId, date(month));
    boolean acquired =
        !outcomes.isEmpty()
            || stagingState.readyToPromote() > 0
            || stagingState.blockingCount() > 0
            || !snapshot.invoices().isEmpty()
            || !snapshot.expenses().isEmpty()
            || !snapshot.bankTransactions().isEmpty();
    int imported = (int) outcomes.stream().filter(o -> "IMPORTED".equals(o.status())).count();
    int review = (int) outcomes.stream().filter(o -> "REVIEW_REQUIRED".equals(o.status())).count();
    int failed = (int) outcomes.stream().filter(o -> "FAILED".equals(o.status())).count();
    var issues =
        acquired ? issues(profileId, snapshot, outcomes, filingIssues) : List.<IssueView>of();
    boolean blockingIssues =
        issues.stream().anyMatch(issue -> issue.kind() != AccountingUserApi.IssueKind.INFO);
    List<AccountingPaymentInstruction> payments;
    try {
      payments = filing.paymentInstructions(filingResult);
    } catch (RuntimeException ignored) {
      payments = List.of();
    }
    var reconciliations = snapshot.reconciliations();
    int unmatched =
        (int)
            reconciliations.stream()
                .filter(row -> !"MATCHED".equalsIgnoreCase(row.status()))
                .count();
    var ryczalt = snapshot.ryczalt();
    var vat = snapshot.vat();
    return new MonthOverview(
        month,
        lifecycle.name(),
        label(lifecycle),
        periodLifecycle.nextAction(lifecycle, acquired, blockingIssues).name(),
        nextLabel(periodLifecycle.nextAction(lifecycle, acquired, blockingIssues)),
        new Summary(
            snapshot.totalBookedRevenuePln(),
            snapshot.vat().calculatedVat(),
            snapshot.ryczalt().calculatedTax(),
            snapshot.zus().totalZus(),
            snapshot.invoices().size() + snapshot.expenses().size(),
            snapshot.bankTransactions().size(),
            snapshot.totalCalculatedObligations()),
        issues,
        new SourceSummary(outcomes.size(), imported, review, failed),
        ksef.map(AccountingKsefSyncPort::providerStatus).orElse("NOT_CONFIGURED"),
        new DocumentSummary(
            snapshot.invoices().size(),
            snapshot.expenses().size(),
            snapshot.invoices().size() + snapshot.expenses().size(),
            review,
            failed),
        new BankSummary(
            snapshot.bankTransactions().size(),
            unmatched,
            snapshot.bankTransactions().isEmpty() ? "NO_IMPORT" : "IMPORTED"),
        new PaymentSummary(
            payments.size(),
            (int)
                payments.stream()
                    .filter(payment -> !"PAID".equalsIgnoreCase(payment.status()))
                    .count(),
            payments.stream()
                .filter(payment -> !"PAID".equalsIgnoreCase(payment.status()))
                .map(com.smartbox.investory.accounting.AccountingPaymentInstruction::amount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add),
            paymentViews(snapshot.reconciliations(), payments)),
        new FilingSummary(
            lifecycle.name(),
            label(lifecycle),
            filingResult.ready(),
            filingResult.issues(),
            repository
                .filingArtifact(profileId, date(month), "JPK_V7M")
                .map(a -> a.status().name())
                .orElse("MISSING"),
            repository
                .filingArtifact(profileId, date(month), "JPK_V7M")
                .map(a -> a.generatedAt().toString())
                .orElse(null),
            repository
                .authorityConfirmation(profileId, date(month), "JPK_UPO")
                .map(a -> a.status().name())
                .orElse("MISSING"),
            repository
                .authorityConfirmation(profileId, date(month), "JPK_UPO")
                .map(AuthorityConfirmation::externalReference)
                .orElse(null),
            repository
                .authorityConfirmation(profileId, date(month), "JPK_UPO")
                .map(a -> a.receivedAt().toString())
                .orElse(null)),
        new ReconciliationSummary(
            reconciliations.size(),
            (int)
                reconciliations.stream()
                    .filter(row -> "MATCHED".equalsIgnoreCase(row.status()))
                    .count(),
            (int)
                reconciliations.stream()
                    .filter(
                        row ->
                            "DIFF".equalsIgnoreCase(row.status())
                                || "UNMATCHED".equalsIgnoreCase(row.status()))
                    .count(),
            (int)
                reconciliations.stream()
                    .filter(
                        row ->
                            row.explanation() != null
                                && row.explanation().toLowerCase().contains("evidence"))
                    .count()),
        periodLifecycle.allowedActions(lifecycle, acquired, blockingIssues),
        repository
            .referenceMonth(profileId, date(month))
            .map(
                r ->
                    new ReferenceSummary(
                        true,
                        r.revenue(),
                        r.expenses(),
                        r.outputVat(),
                        r.deductibleInputVat(),
                        r.vatPayable(),
                        r.ryczalt(),
                        r.zus(),
                        r.documentCount(),
                        r.bankCount(),
                        r.filingStatus()))
            .orElse(
                new ReferenceSummary(false, null, null, null, null, null, null, null, 0, 0, null)),
        new MonthlyAudit(
            ryczalt.revenueBeforeDeductions(),
            ryczalt.socialContributionDeduction(),
            ryczalt.healthDeduction(),
            BigDecimal.ZERO,
            ryczalt.taxableBase(),
            cumulativeRyczaltTax(profileId, month, snapshotCache),
            ryczalt.calculatedTax(),
            vat.outputVatAfterSalesCorrection(),
            vat.deductibleInputVat(),
            vat.explicitVatAdjustments(),
            vat.calculatedVat()));
  }

  private BigDecimal cumulativeRyczaltTax(
      long profileId, YearMonth month, Map<LocalDate, AccountingMonthSnapshot> snapshotCache) {
    BigDecimal total = BigDecimal.ZERO;
    for (YearMonth cursor = YearMonth.of(month.getYear(), 1);
        !cursor.isAfter(month);
        cursor = cursor.plusMonths(1)) {
      total =
          total.add(
              snapshotCache
                  .computeIfAbsent(date(cursor), period -> snapshot(profileId, period))
                  .ryczalt()
                  .calculatedTax());
    }
    return total;
  }

  private List<PaymentView> paymentViews(
      List<ReconciliationRow> reconciliation, List<AccountingPaymentInstruction> instructions) {
    return instructions.stream()
        .map(
            x -> {
              var matched =
                  reconciliation.stream()
                      .filter(r -> "OBLIGATION_PAYMENT".equals(r.kind()))
                      .filter(r -> x.obligationType().equals(r.reference()))
                      .findFirst();
              var reference = matched.map(r -> r.expectedAmount()).orElse(null);
              var paid = matched.map(r -> r.matchedAmount()).orElse(x.matchedBankPayment());
              BigDecimal difference = reference == null ? null : paid.subtract(reference);
              return new PaymentView(
                  x.obligationType(),
                  x.amount(),
                  reference,
                  paid,
                  difference,
                  x.dueDate(),
                  x.recipient(),
                  x.account(),
                  matched.map(r -> r.status()).orElse(x.status()));
            })
        .toList();
  }

  private List<AccountingPaymentInstruction> paymentInstructions(
      long profileId, java.time.LocalDate period) {
    return paymentInstructions(filing.filing(profileId, period));
  }

  private List<AccountingPaymentInstruction> paymentInstructions(
      AccountingFilingService.FilingResult result) {
    try {
      return filing.paymentInstructions(result);
    } catch (RuntimeException ignored) {
      return List.of();
    }
  }

  private List<IssueView> issues(
      long profileId,
      AccountingMonthSnapshot snapshot,
      List<AccountingSourceEvidenceService.SourceOutcome> outcomes,
      List<String> filingIssues) {
    var result = new java.util.ArrayList<IssueView>();
    snapshot
        .issues()
        .forEach(
            i ->
                result.add(
                    issue(profileId, i.type(), i.severity(), i.message(), i.sourceReference())));
    outcomes.stream()
        .filter(o -> !"IMPORTED".equals(o.status()))
        .forEach(
            o ->
                result.add(
                    issue(
                        profileId,
                        "SOURCE_" + o.status(),
                        "WARNING",
                        o.error() == null ? "Source was not imported." : o.error(),
                        o.reference())));
    filingIssues.stream()
        .filter(issue -> !issue.startsWith("Month calculation is not confirmed"))
        .filter(issue -> result.stream().noneMatch(existing -> existing.message().equals(issue)))
        .forEach(
            filingIssue ->
                result.add(
                    issue(profileId, filingIssueCode(filingIssue), "BLOCKING", filingIssue, null)));
    return result;
  }

  private IssueView issue(
      long profileId, String code, String severity, String message, String sourceReference) {
    String normalized = code == null ? "ACCOUNTING_ISSUE" : code;
    var kind = issueKind(normalized, severity);
    return new IssueView(
        stableIssueId(normalized, sourceReference),
        normalized,
        severity,
        kind,
        title(normalized),
        message,
        sourceReference,
        resolution(profileId, kind));
  }

  static String filingIssueCode(String issue) {
    int separator = issue == null ? -1 : issue.indexOf(':');
    return separator < 0 ? "FILING_READINESS" : issue.substring(0, separator);
  }

  static AccountingUserApi.IssueKind issueKind(String code, String severity) {
    if (code.startsWith("MISSING_PAYMENT_CONFIGURATION")
        || code.startsWith("MISSING_TAXPAYER_CONFIGURATION")
        || code.startsWith("MISSING_EFFECTIVE_TAX_PROFILE")
        || code.startsWith("MISSING_ZUS_RULE_INPUT")) {
      return AccountingUserApi.IssueKind.SETUP;
    }
    if (code.startsWith("MISSING_VAT_CLASSIFICATION")
        || code.startsWith("MISSING_EXPLICIT_VAT_RATE")
        || code.startsWith("UNSUPPORTED_VAT_RATE")
        || code.startsWith("MISSING_COUNTERPARTY_IDENTIFIER")
        || code.startsWith("MISSING_JPK_EVIDENCE_CLASSIFICATION")
        || code.startsWith("SOURCE_REVIEW_REQUIRED")) {
      return AccountingUserApi.IssueKind.NEEDS_ANSWER;
    }
    if ("INFO".equalsIgnoreCase(severity)) return AccountingUserApi.IssueKind.INFO;
    return AccountingUserApi.IssueKind.BLOCKED;
  }

  private AccountingUserApi.Resolution resolution(
      long profileId, AccountingUserApi.IssueKind kind) {
    if (kind == AccountingUserApi.IssueKind.SETUP) {
      return new AccountingUserApi.Resolution.Setup(
          "/profiles/" + profileId + "/accounting", "Open accounting workspace");
    }
    return new AccountingUserApi.Resolution.None(
        kind == AccountingUserApi.IssueKind.NEEDS_ANSWER
            ? "Open the source document and save the missing review data."
            : "Resolve the reported data or system problem before continuing.");
  }

  private String stableIssueId(String code, String sourceReference) {
    return java.util
        .UUID
        .nameUUIDFromBytes(
            (code + "|" + (sourceReference == null ? "account" : sourceReference))
                .getBytes(java.nio.charset.StandardCharsets.UTF_8))
        .toString();
  }

  @Override
  public List<IssueView> issues(long p, YearMonth m) {
    return overview(p, m).issues();
  }

  @Override
  public List<DocumentView> documents(long p, YearMonth m) {
    profile(p);
    var canonical = repository.canonicalDocumentsForPeriod(p, date(m));
    if (!canonical.isEmpty()) {
      return canonical.stream().map(this::document).toList();
    }
    var s = snapshot(p, date(m));
    return java.util.stream.Stream.concat(
            s.invoices().stream().map(this::document), s.expenses().stream().map(this::document))
        .toList();
  }

  private DocumentView document(AccountingPocRepository.CanonicalDocumentRow row) {
    return new DocumentView(
        row.id(),
        row.reference(),
        row.direction(),
        row.documentDate(),
        row.grossAmount(),
        row.currency(),
        "IMPORTED",
        row.sourceReference(),
        row.counterparty(),
        row.category(),
        row.saleDate(),
        row.counterpartyTaxIdentifier(),
        row.counterpartyCountry(),
        row.sourceType(),
        row.sourceName(),
        sourceLabel(row.sourceType()),
        categoryLabel(row.category()),
        importStatus(row.sourceType()),
        null,
        null,
        row.documentKind(),
        row.correctsDocumentId(),
        row.correctsDocumentReference());
  }

  private DocumentView document(InvoiceRow r) {
    return new DocumentView(
        r.id(),
        r.reference(),
        "SALES",
        r.issueDate() == null ? r.saleDate() : r.issueDate(),
        r.grossAmount(),
        r.currency(),
        "IMPORTED",
        r.ksefNumber(),
        r.customerAlias(),
        null,
        r.saleDate(),
        r.counterpartyTaxIdentifier(),
        r.counterpartyCountry(),
        r.ksefNumber() == null ? null : "KSEF",
        null,
        sourceLabel(r.ksefNumber() == null ? null : "KSEF"),
        null,
        importStatus(r.ksefNumber() == null ? null : "KSEF"),
        null,
        null,
        "INVOICE",
        null,
        null);
  }

  private DocumentView document(ExpenseRow r) {
    return new DocumentView(
        -r.id(),
        r.reference(),
        "PURCHASE",
        r.invoiceDate(),
        r.grossAmount(),
        r.currency(),
        "IMPORTED",
        r.ksefNumber(),
        r.supplierAlias(),
        r.category(),
        null,
        r.counterpartyTaxIdentifier(),
        r.counterpartyCountry(),
        r.ksefNumber() == null ? null : "KSEF",
        null,
        sourceLabel(r.ksefNumber() == null ? null : "KSEF"),
        categoryLabel(r.category()),
        importStatus(r.ksefNumber() == null ? null : "KSEF"),
        null,
        null,
        "INVOICE",
        null,
        null);
  }

  /** Import status is known only when a retained source evidence record identifies the source. */
  private String importStatus(String sourceType) {
    return sourceType == null ? null : "IMPORTED";
  }

  private String sourceLabel(String sourceType) {
    return switch (sourceType == null ? "" : sourceType) {
      case "UPLOAD" -> "File import";
      case "KSEF" -> "KSeF";
      case "BANK" -> "Bank import";
      default -> null;
    };
  }

  private String categoryLabel(String category) {
    return switch (category == null ? "" : category) {
      case "VEHICLE_FUEL" -> "Fuel";
      case "ACCOUNTING_SERVICE" -> "Accounting service";
      case "BUSINESS_SERVICE", "SERVICE" -> "Service";
      case "EQUIPMENT" -> "Equipment";
      case "OTHER" -> "Other";
      default -> null;
    };
  }

  @Override
  public List<BankTransactionView> bankTransactions(long p, YearMonth m) {
    profile(p);
    return snapshot(p, date(m)).bankTransactions().stream().map(this::bank).toList();
  }

  private BankTransactionView bank(BankRow r) {
    return new BankTransactionView(
        r.id(), r.bookingDate(), r.reference(), r.amount(), r.currency(), "IMPORTED");
  }

  @Override
  public List<PaymentView> payments(long p, YearMonth m) {
    profile(p);
    var snapshot = snapshot(p, date(m));
    var result = filing.filing(p, date(m), snapshot);
    return paymentViews(snapshot.reconciliations(), paymentInstructions(result));
  }

  @Override
  public List<PaymentHistoryView> paymentHistory(
      long p, YearMonth from, YearMonth to, String obligationType) {
    profile(p);
    if (from == null || to == null || from.isAfter(to)) {
      throw new IllegalArgumentException("Payment history range is invalid");
    }
    String normalizedType =
        obligationType == null ? null : obligationType.trim().toUpperCase(Locale.ROOT);
    if (normalizedType != null && !Set.of("RYCZALT", "VAT", "ZUS").contains(normalizedType)) {
      throw new IllegalArgumentException("Unsupported obligation type");
    }
    var result = new java.util.ArrayList<PaymentHistoryView>();
    for (YearMonth cursor = from; !cursor.isAfter(to); cursor = cursor.plusMonths(1)) {
      repository.obligationsForPeriod(p, date(cursor)).stream()
          .filter(row -> normalizedType == null || normalizedType.equals(row.obligationType()))
          .forEach(
              row -> {
                BigDecimal expected = row.expectedAmount();
                BigDecimal paid = row.paidAmount();
                BigDecimal outstanding =
                    expected == null
                        ? null
                        : expected
                            .subtract(paid == null ? BigDecimal.ZERO : paid)
                            .max(BigDecimal.ZERO);
                result.add(
                    new PaymentHistoryView(
                        row.obligationType(),
                        YearMonth.from(row.taxPeriod()),
                        expected,
                        paid,
                        outstanding,
                        row.dueDate(),
                        row.paymentDate(),
                        row.status()));
              });
    }
    return List.copyOf(result);
  }

  @Override
  public FilingView filings(long p, YearMonth m) {
    profile(p);
    var f = filing.filing(p, date(m));
    var state = repository.periodState(p, date(m));
    var artifact = repository.filingArtifact(p, date(m), "JPK_V7M");
    var upo = repository.authorityConfirmation(p, date(m), "JPK_UPO");
    return new FilingView(
        state == null ? "OPEN" : state.lifecycleStatus().name(),
        state == null ? label(PeriodLifecycleStatus.OPEN) : label(state.lifecycleStatus()),
        f.confirmed(),
        f.ready(),
        f.issues(),
        artifact.map(a -> a.status().name()).orElse("MISSING"),
        artifact.map(a -> a.generatedAt().toString()).orElse(null),
        upo.map(a -> a.status().name()).orElse("MISSING"),
        upo.map(AuthorityConfirmation::externalReference).orElse(null),
        upo.map(a -> a.receivedAt().toString()).orElse(null));
  }

  @Override
  public List<ReconciliationView> reconciliation(long p, YearMonth m) {
    profile(p);
    return snapshot(p, date(m)).reconciliations().stream().map(this::reconciliation).toList();
  }

  private AccountingMonthSnapshot snapshot(long profileId, LocalDate period) {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes == null) return facts.snapshot(profileId, period);
    @SuppressWarnings("unchecked")
    var cache =
        (Map<String, AccountingMonthSnapshot>)
            attributes.getAttribute(REQUEST_SNAPSHOT_CACHE, RequestAttributes.SCOPE_REQUEST);
    if (cache == null) {
      cache = new java.util.HashMap<>();
      attributes.setAttribute(REQUEST_SNAPSHOT_CACHE, cache, RequestAttributes.SCOPE_REQUEST);
    }
    String key = profileId + ":" + period;
    return cache.computeIfAbsent(key, ignored -> facts.snapshot(profileId, period));
  }

  private ReconciliationView reconciliation(ReconciliationRow r) {
    return new ReconciliationView(
        r.reference(),
        r.kind(),
        r.expectedAmount(),
        r.matchedAmount(),
        r.status(),
        r.explanation(),
        r.currency(),
        r.paymentDate());
  }

  @Override
  @Transactional
  public CandidateView recognize(long p, String filename, String contentType, byte[] content) {
    profile(p);
    long id = sources.receiveUpload(p, filename, contentType, content);
    try {
      if (sources.status(id) == AccountingSourceStatus.FAILED) sources.retry(id);
      RecognizedInvoice r = recognition.recognize(p, filename, contentType, content);
      if (r == null) {
        throw new IllegalStateException("Invoice recognition returned no result");
      }
      r = enforceProfileDirection(p, r);
      boolean requiresReview = "UNKNOWN".equals(r.documentType());
      sources.status(
          id,
          requiresReview ? AccountingSourceStatus.REVIEW_REQUIRED : AccountingSourceStatus.PARSED,
          requiresReview ? "Invoice direction requires review" : null);
      Long existing = existingDocumentId(p, r.documentType(), r.reference());
      return new CandidateView(
          "sha256:" + hash(content),
          r.documentType(),
          r.issueDate(),
          r.saleDate(),
          r.dueDate(),
          r.reference(),
          r.seller(),
          r.buyer(),
          r.sellerNip(),
          r.buyerNip(),
          r.category(),
          r.currency(),
          r.netAmount(),
          r.vatAmount(),
          r.grossAmount(),
          r.note(),
          requiresReview ? "REVIEW_REQUIRED" : "PARSED",
          existing != null,
          existing,
          null,
          null,
          vatTreatmentOptions(r.documentType()),
          requiredInputs(r.documentType()));
    } catch (RuntimeException e) {
      sources.status(id, AccountingSourceStatus.FAILED, e.getMessage());
      throw e;
    }
  }

  private RecognizedInvoice enforceProfileDirection(long profileId, RecognizedInvoice invoice) {
    String ownNip = facts.accountingProfile(profileId).nip();
    if (ownNip == null || ownNip.isBlank() || "CREDIT_NOTE".equals(invoice.documentType())) {
      return invoice;
    }
    String own = ownNip.replaceAll("\\D", "");
    String seller = invoice.sellerNip() == null ? "" : invoice.sellerNip().replaceAll("\\D", "");
    String buyer = invoice.buyerNip() == null ? "" : invoice.buyerNip().replaceAll("\\D", "");
    String direction =
        own.equals(seller)
            ? "SALES_INVOICE"
            : own.equals(buyer) ? "PURCHASE_INVOICE" : invoice.documentType();
    if (direction.equals(invoice.documentType())) return invoice;
    return new RecognizedInvoice(
        direction,
        invoice.issueDate(),
        invoice.saleDate(),
        invoice.dueDate(),
        invoice.reference(),
        invoice.seller(),
        invoice.buyer(),
        invoice.category(),
        invoice.currency(),
        invoice.netAmount(),
        invoice.vatAmount(),
        invoice.grossAmount(),
        invoice.note(),
        invoice.sellerNip(),
        invoice.buyerNip(),
        invoice.evidence());
  }

  @Override
  public CandidateView reviewSource(long p, String sourceReference) {
    profile(p);
    var source =
        sources
            .findSource(p, AccountingSourceType.KSEF, sourceReference)
            .filter(row -> row.status() == AccountingSourceStatus.REVIEW_REQUIRED)
            .orElseThrow(() -> new IllegalArgumentException("KSeF review source is not available"));
    var result =
        extraction.extract(
            p,
            new AccountingSourceDocument(
                source.originalFilename(), source.contentType(), source.payload()));
    var candidate = result.candidate();
    if (candidate == null)
      throw new IllegalArgumentException("KSeF source could not be parsed for review");
    Long existing = existingDocumentId(p, candidate.documentType(), candidate.reference());
    return new CandidateView(
        sourceReference,
        candidate.documentType(),
        candidate.issueDate(),
        candidate.saleDate(),
        candidate.dueDate(),
        candidate.reference(),
        candidate.seller(),
        candidate.buyer(),
        candidate.sellerNip(),
        candidate.buyerNip(),
        candidate.suggestedCategory(),
        candidate.currency(),
        candidate.netAmount(),
        candidate.vatAmount(),
        candidate.grossAmount(),
        "KSeF source requires accounting review",
        "REVIEW_REQUIRED",
        existing != null,
        existing,
        null,
        null,
        vatTreatmentOptions(candidate.documentType()),
        requiredInputs(candidate.documentType()));
  }

  private Long existingDocumentId(long profileId, String documentType, String reference) {
    if (reference == null || reference.isBlank()) return null;
    String direction = directionForDocumentType(documentType);
    return direction == null
        ? null
        : repository.canonicalInvoiceId(profileId, direction, reference.trim());
  }

  private String directionForDocumentType(String documentType) {
    return switch (documentType == null ? "" : documentType) {
      case "PURCHASE_INVOICE", "RECEIPT" -> "PURCHASE";
      case "SALES_INVOICE", "CREDIT_NOTE" -> "SALE";
      default -> null;
    };
  }

  private static List<AccountingUserApi.Option> vatTreatmentOptions(String documentType) {
    if (!"PURCHASE_INVOICE".equals(documentType) && !"RECEIPT".equals(documentType)) {
      return List.of();
    }
    return List.of(
        new AccountingUserApi.Option("DOMESTIC_PURCHASE", "Zakup krajowy", false),
        new AccountingUserApi.Option("IMPORT_OF_SERVICES_EU", "Import usług z UE", false),
        new AccountingUserApi.Option("IMPORT_OF_SERVICES_NON_EU", "Import usług spoza UE", false));
  }

  static List<AccountingUserApi.RequiredInput> requiredInputs(String documentType) {
    List<AccountingUserApi.Option> options = vatTreatmentOptions(documentType);
    if (options.isEmpty()) return List.of();
    var inputs = new java.util.ArrayList<AccountingUserApi.RequiredInput>();
    inputs.add(
        new AccountingUserApi.RequiredInput(
            "vatTreatment", "choice", "Sposób rozliczenia VAT", true, options, null, List.of()));
    inputs.add(
        new AccountingUserApi.RequiredInput(
            "vatRate",
            "decimal",
            "Stawka VAT",
            true,
            List.of(),
            "vatTreatment",
            List.of("DOMESTIC_PURCHASE")));
    inputs.add(
        new AccountingUserApi.RequiredInput(
            "counterpartyCountry",
            "country",
            "Kraj kontrahenta",
            true,
            List.of(),
            "vatTreatment",
            List.of("IMPORT_OF_SERVICES_EU", "IMPORT_OF_SERVICES_NON_EU")));
    return List.copyOf(inputs);
  }

  @Override
  @Transactional
  public void saveReviewed(long p, ReviewedDocument d) {
    profile(p);
    try {
      validateReviewedDocument(d);
      var source = findReviewedSource(p, d.sourceReference());
      YearMonth effectiveTaxPeriod =
          YearMonth.from(
              AccountingDateRules.accountingPeriod(
                  d.saleDate(), d.issueDate(), null, "CREDIT_NOTE".equals(d.documentType())));
      staging.stageInvoice(
          p,
          new AccountingInvoiceIngestionService.ReviewedInvoice(
              effectiveTaxPeriod.atDay(1),
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
              Long.toString(source.id()),
              d.counterpartyTaxIdentifier(),
              counterpartyCountry(source, d.counterpartyCountry()),
              source.type() == AccountingSourceType.KSEF ? source.externalReference() : null,
              source.type() == AccountingSourceType.KSEF
                  ? new AccountingFilingEvidence(
                      AccountingFilingEvidence.Type.KSEF, source.externalReference())
                  : null,
              d.dueDate(),
              d.vatRate(),
              d.correctsDocumentReference()),
          normalizeVatTreatment(d.documentType(), d.vatTreatment()));
      stagingReconciliation.reconcile(p, effectiveTaxPeriod.atDay(1));
      // Staging is intentionally not reported as canonical IMPORTED data.
    } catch (RuntimeException exception) {
      sources
          .findId(p, AccountingSourceType.UPLOAD, d.sourceReference())
          .or(() -> sources.findId(p, AccountingSourceType.KSEF, d.sourceReference()))
          .ifPresent(
              id ->
                  sources.status(
                      id, AccountingSourceStatus.REVIEW_REQUIRED, exception.getMessage()));
      throw exception;
    }
  }

  @Override
  @Transactional
  public AccountingUserApi.DocumentMutationResult saveReviewedResult(long p, ReviewedDocument d) {
    profile(p);
    if (d == null || d.reference() == null || d.reference().isBlank()) {
      throw new IllegalArgumentException("Invoice reference is required");
    }
    String direction = directionForDocumentType(d.documentType());
    if (direction == null) {
      throw new IllegalArgumentException("Unsupported document type");
    }
    Long existing = repository.canonicalInvoiceId(p, direction, d.reference().trim());
    if (existing != null) {
      return new AccountingUserApi.DocumentMutationResult(
          existing,
          d.reference().trim(),
          "DUPLICATE",
          null,
          true,
          existing,
          "This document already exists");
    }
    saveReviewed(p, d);
    return new AccountingUserApi.DocumentMutationResult(
        null,
        d.reference().trim(),
        "STAGED",
        null,
        false,
        null,
        "Document is waiting for accounting review");
  }

  @Override
  @Transactional
  public AccountingUserApi.DocumentMutationResult issueInvoice(
      long p, AccountingUserApi.InvoiceIssueRequest request) {
    profile(p);
    if (request == null || request.reference() == null || request.reference().isBlank()) {
      throw new IllegalArgumentException("Invoice reference is required");
    }
    validateAmounts(request.netAmount(), request.vatAmount(), request.grossAmount());
    LocalDate issueDate =
        request.issueDate() == null ? java.time.LocalDate.now() : request.issueDate();
    Long existing = repository.canonicalInvoiceId(p, "SALE", request.reference().trim());
    if (existing != null) {
      return new AccountingUserApi.DocumentMutationResult(
          existing,
          request.reference().trim(),
          "DUPLICATE",
          null,
          true,
          existing,
          "This invoice already exists");
    }
    var invoice =
        new AccountingInvoiceIngestionService.ReviewedInvoice(
            issueDate,
            "SALES_INVOICE",
            issueDate,
            request.saleDate() == null ? issueDate : request.saleDate(),
            request.reference().trim(),
            request.counterpartyAlias(),
            null,
            request.currency(),
            request.netAmount(),
            request.vatAmount(),
            request.grossAmount(),
            null,
            "MOBILE_MANUAL",
            request.note(),
            null,
            request.counterpartyTaxIdentifier(),
            request.counterpartyCountry(),
            null,
            null,
            request.dueDate(),
            request.vatRate(),
            null);
    invoiceIngestion.ingest(p, invoice);
    Long id = repository.canonicalInvoiceId(p, "SALE", request.reference().trim());
    if (id == null) {
      throw new IllegalStateException("Invoice was not confirmed by the accounting store");
    }
    return new AccountingUserApi.DocumentMutationResult(
        id, request.reference().trim(), "CREATED", ksefStatus(), false, null, "Invoice created");
  }

  @Override
  @Transactional
  public AccountingUserApi.DocumentMutationResult recordManualIncome(
      long p, AccountingUserApi.ManualIncomeRequest request) {
    profile(p);
    if (request == null || request.amount() == null || request.amount().signum() <= 0) {
      throw new IllegalArgumentException("Income amount must be greater than zero");
    }
    if (request.description() == null || request.description().isBlank()) {
      throw new IllegalArgumentException("Income description is required");
    }
    LocalDate date = request.date() == null ? java.time.LocalDate.now() : request.date();
    String currency =
        request.currency() == null || request.currency().isBlank()
            ? "PLN"
            : request.currency().trim().toUpperCase(java.util.Locale.ROOT);
    String reference = "MOBILE-INCOME-" + java.util.UUID.randomUUID();
    var income =
        new AccountingInvoiceIngestionService.ReviewedInvoice(
            date,
            "SALES_INVOICE",
            date,
            date,
            reference,
            request.description().trim(),
            null,
            currency,
            request.amount(),
            BigDecimal.ZERO,
            request.amount(),
            null,
            "MOBILE_MANUAL",
            request.description().trim(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    invoiceIngestion.ingest(p, income);
    Long id = repository.canonicalInvoiceId(p, "SALE", reference);
    if (id == null) {
      throw new IllegalStateException("Income was not confirmed by the accounting store");
    }
    return new AccountingUserApi.DocumentMutationResult(
        id, reference, "CREATED", ksefStatus(), false, null, "Income recorded");
  }

  private void validateAmounts(BigDecimal net, BigDecimal vat, BigDecimal gross) {
    if (net == null || vat == null || gross == null) {
      throw new IllegalArgumentException("Net, VAT and gross amounts are required");
    }
    if (net.signum() < 0
        || vat.signum() < 0
        || gross.signum() <= 0
        || net.add(vat).compareTo(gross) != 0) {
      throw new IllegalArgumentException("Net + VAT must equal gross");
    }
  }

  private String ksefStatus() {
    return ksef.isPresent() ? "NOT_SUBMITTED" : "NOT_CONFIGURED";
  }

  private AccountingSourceRepository.SourceRow findReviewedSource(
      long profileId, String reference) {
    return sources
        .findSource(profileId, AccountingSourceType.UPLOAD, reference)
        .or(() -> sources.findSource(profileId, AccountingSourceType.KSEF, reference))
        .orElseThrow(() -> new IllegalArgumentException("Source evidence is missing"));
  }

  private void validateReviewedDocument(ReviewedDocument document) {
    if (document == null) throw new IllegalArgumentException("Reviewed document is required");
    String treatment = normalizeVatTreatment(document.documentType(), document.vatTreatment());
    if (("DOMESTIC_VAT".equals(treatment) || "DOMESTIC_PURCHASE".equals(treatment))
        && document.vatRate() == null) {
      throw new IllegalArgumentException("VAT rate is required for domestic VAT treatment");
    }
    if (Set.of(
                "EU_B2B_REVERSE_CHARGE",
                "NON_EU_B2B_OUTSIDE_POLAND",
                "IMPORT_OF_SERVICES_EU",
                "IMPORT_OF_SERVICES_NON_EU")
            .contains(treatment)
        && (document.counterpartyCountry() == null || document.counterpartyCountry().isBlank())) {
      throw new IllegalArgumentException(
          "Counterparty country is required for cross-border VAT treatment");
    }
  }

  private String normalizeVatTreatment(String documentType, String vatTreatment) {
    if (("PURCHASE_INVOICE".equals(documentType) || "RECEIPT".equals(documentType))
        && "DOMESTIC_VAT".equals(vatTreatment)) {
      return "DOMESTIC_PURCHASE";
    }
    return vatTreatment;
  }

  private String counterpartyCountry(AccountingSourceRepository.SourceRow source, String country) {
    if (country != null && !country.isBlank())
      return country.trim().toUpperCase(java.util.Locale.ROOT);
    return source.type() == AccountingSourceType.KSEF ? "PL" : null;
  }

  @Override
  public void importBank(long p, String f, String c, byte[] b, YearMonth m) {
    profile(p);
    bankImport
        .stageFile(p, f, c, b, date(m))
        .periods()
        .forEach(period -> stagingReconciliation.reconcile(p, period));
  }

  @Override
  public AccountingUserApi.KsefSyncResult syncKsef(long p, YearMonth m) {
    profile(p);
    return ksef.map(adapter -> adapter.syncAll(p, m))
        .orElseGet(
            () ->
                new AccountingUserApi.KsefSyncResult(
                    "NOT_CONFIGURED", 0, 0, 0, 0, 0, "KSeF is not configured."));
  }

  @Override
  public AccountingUserApi.KsefSyncResult reimportKsef(long p, YearMonth m) {
    profile(p);
    return ksef.map(adapter -> adapter.reimport(p, m))
        .orElseGet(
            () ->
                new AccountingUserApi.KsefSyncResult(
                    "NOT_CONFIGURED", 0, 0, 0, 0, 0, "KSeF is not configured."));
  }

  @Override
  public AccountingUserApi.KsefSyncResult syncKsefSeller(long p, YearMonth m) {
    profile(p);
    return ksef.map(adapter -> adapter.syncSeller(p, m))
        .orElseGet(
            () ->
                new AccountingUserApi.KsefSyncResult(
                    "NOT_CONFIGURED", 0, 0, 0, 0, 0, "KSeF is not configured."));
  }

  @Override
  public AccountingUserApi.KsefSyncResult syncKsefThirdParty(long p, YearMonth m) {
    profile(p);
    return ksef.map(adapter -> adapter.syncThirdParty(p, m))
        .orElseGet(
            () ->
                new AccountingUserApi.KsefSyncResult(
                    "NOT_CONFIGURED", 0, 0, 0, 0, 0, "KSeF is not configured."));
  }

  @Override
  public AccountingUserApi.FilingArtifactView generateJpk(long p, YearMonth m) {
    profile(p);
    filing.jpk(p, date(m));
    return artifact(p, m);
  }

  @Override
  public java.util.Optional<AccountingUserApi.FilingArtifactView> filingArtifact(
      long p, YearMonth m) {
    profile(p);
    return repository.filingArtifact(p, date(m), "JPK_V7M").map(this::artifactView);
  }

  private AccountingUserApi.FilingArtifactView artifact(long p, YearMonth m) {
    return filingArtifact(p, m)
        .orElseThrow(() -> new IllegalStateException("JPK artifact was not persisted"));
  }

  private AccountingUserApi.FilingArtifactView artifactView(AccountingFilingArtifact artifact) {
    return new AccountingUserApi.FilingArtifactView(
        artifact.type().name(),
        "JPK_V7M_" + artifact.period() + ".xml",
        "application/xml",
        artifact.payload(),
        artifact.payloadHash(),
        artifact.generatedAt(),
        artifact.status().name());
  }

  @Override
  public void recordConfirmation(long p, AccountingUserApi.ConfirmationInput input) {
    profile(p);
    if (input == null || input.confirmationType() == null || input.status() == null)
      throw new IllegalArgumentException("Confirmation type and status are required");
    filing.recordAuthorityConfirmation(
        p,
        new AuthorityConfirmation(
            "MANUAL",
            input.obligationOrArtifactType() == null ? "JPK_V7M" : input.obligationOrArtifactType(),
            date(input.taxPeriod()),
            input.externalReference(),
            AuthorityConfirmation.ConfirmationType.valueOf(input.confirmationType()),
            AuthorityConfirmation.ConfirmationStatus.valueOf(input.status()),
            input.receivedAt(),
            null,
            input.note(),
            input.amount()));
  }

  @Override
  public void confirm(long p, YearMonth m) {
    profile(p);
    filing.confirm(p, date(m));
  }

  @Override
  public void file(long p, YearMonth m) {
    profile(p);
    filing.markFiled(p, date(m));
  }

  @Override
  public void settle(long p, YearMonth m) {
    profile(p);
    filing.settle(p, date(m));
  }

  @Override
  public void lock(long p, YearMonth m) {
    profile(p);
    filing.lock(p, date(m));
  }

  @Override
  public void reopen(long p, YearMonth m, String reason) {
    profile(p);
    filing.reopen(p, date(m), reason);
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

  private static String nextLabel(AccountingPeriodLifecycle.NextAction action) {
    return switch (action) {
      case WAITING_FOR_SOURCE -> "Waiting for source data";
      case REVIEW -> "Review issues";
      case CONFIRM -> "Confirm month";
      case FILE -> "Record filing";
      case SETTLE -> "Settle month";
      case LOCK -> "Lock month";
      default -> "No action";
    };
  }

  private static String title(String code) {
    return code == null ? "Accounting issue" : code.replace('_', ' ');
  }
}
