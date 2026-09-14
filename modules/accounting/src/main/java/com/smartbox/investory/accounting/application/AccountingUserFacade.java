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
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
  private final AccountingDocumentExtractionService extraction;
  private final com.smartbox.investory.accounting.staging.AccountingStagingAcquisitionService
      staging;
  private final com.smartbox.investory.accounting.staging.AccountingStagingReconciliationService
      stagingReconciliation;
  private final com.smartbox.investory.accounting.staging.AccountingBankStagingImportService
      bankImport;
  private final Optional<AccountingKsefSyncPort> ksef;
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
    return facts.availablePeriods(profileId).stream()
        .map(
            d -> {
              var state = repository.periodState(profileId, d);
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
    var snapshot = facts.snapshot(profileId, date(month));
    var state = repository.periodState(profileId, date(month));
    var lifecycle = state == null ? PeriodLifecycleStatus.OPEN : state.lifecycleStatus();
    var outcomes = sources.outcomes(profileId, date(month));
    var filingIssues = filing.filing(profileId, date(month)).issues();
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
    var payments = paymentInstructions(profileId, date(month));
    var filingResult = filing.filing(profileId, date(month));
    var reconciliations = snapshot.reconciliations();
    int unmatched =
        (int)
            reconciliations.stream()
                .filter(row -> !"MATCHED".equalsIgnoreCase(row.status()))
                .count();
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
            snapshot.bankTransactions().size()),
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
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)),
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
                new ReferenceSummary(false, null, null, null, null, null, null, null, 0, 0, null)));
  }

  private List<AccountingPaymentInstruction> paymentInstructions(
      long profileId, java.time.LocalDate period) {
    try {
      return filing.paymentInstructions(profileId, period);
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
    var s = facts.snapshot(p, date(m));
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
        row.sourceId() == null ? null : row.sourceId().toString(),
        row.counterparty(),
        row.category(),
        row.saleDate());
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
        r.ksefNumber(),
        r.customerAlias(),
        null,
        r.saleDate());
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
        null);
  }

  @Override
  public List<BankTransactionView> bankTransactions(long p, YearMonth m) {
    profile(p);
    return facts.snapshot(p, date(m)).bankTransactions().stream().map(this::bank).toList();
  }

  private BankTransactionView bank(BankRow r) {
    return new BankTransactionView(
        r.id(), r.bookingDate(), r.reference(), r.amount(), r.currency(), "IMPORTED");
  }

  @Override
  public List<PaymentView> payments(long p, YearMonth m) {
    profile(p);
    return paymentInstructions(p, date(m)).stream()
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
    return facts.snapshot(p, date(m)).reconciliations().stream().map(this::reconciliation).toList();
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
    if (p != 1L) {
      throw new IllegalArgumentException(
          "Document recognition is certified only for the primary Accounting profile.");
    }
    long id = sources.receiveUpload(p, filename, contentType, content);
    try {
      if (sources.status(id) == AccountingSourceStatus.FAILED) sources.retry(id);
      RecognizedInvoice r = recognition.recognize(filename, contentType, content);
      if (r == null) {
        throw new IllegalStateException("Invoice recognition returned no result");
      }
      r = enforceProfileDirection(p, r);
      boolean requiresReview = "UNKNOWN".equals(r.documentType());
      sources.status(
          id,
          requiresReview ? AccountingSourceStatus.REVIEW_REQUIRED : AccountingSourceStatus.PARSED,
          requiresReview ? "Invoice direction requires review" : null);
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
          requiresReview ? "REVIEW_REQUIRED" : "PARSED");
    } catch (RuntimeException e) {
      sources.status(id, AccountingSourceStatus.FAILED, e.getMessage());
      throw e;
    }
  }

  private RecognizedInvoice enforceProfileDirection(long profileId, RecognizedInvoice invoice) {
    String ownNip = facts.accountingProfile().nip();
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
            new AccountingSourceDocument(
                source.originalFilename(), source.contentType(), source.payload()));
    var candidate = result.candidate();
    if (candidate == null)
      throw new IllegalArgumentException("KSeF source could not be parsed for review");
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
        "REVIEW_REQUIRED");
  }

  @Override
  public void saveReviewed(long p, ReviewedDocument d) {
    profile(p);
    try {
      validateReviewedDocument(d);
      var source = findReviewedSource(p, d.sourceReference());
      YearMonth effectiveTaxPeriod =
          "CREDIT_NOTE".equals(d.documentType()) && d.issueDate() != null
              ? YearMonth.from(d.issueDate())
              : d.taxPeriod();
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
              d.vatRate()),
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

  private AccountingSourceRepository.SourceRow findReviewedSource(
      long profileId, String reference) {
    return sources
        .findSource(profileId, AccountingSourceType.UPLOAD, reference)
        .or(() -> sources.findSource(profileId, AccountingSourceType.KSEF, reference))
        .orElseThrow(() -> new IllegalArgumentException("Source evidence is missing"));
  }

  private void validateReviewedDocument(ReviewedDocument document) {
    if (document == null) throw new IllegalArgumentException("Reviewed document is required");
    if (document.taxPeriod() == null)
      throw new IllegalArgumentException("Accounting month is required");
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
    return ksef.map(adapter -> adapter.syncAll(java.time.YearMonth.now()))
        .orElseGet(
            () ->
                new AccountingUserApi.KsefSyncResult(
                    "NOT_CONFIGURED", 0, 0, 0, 0, 0, "KSeF is not configured."));
  }

  @Override
  public AccountingUserApi.KsefSyncResult reimportKsef(long p, YearMonth m) {
    profile(p);
    return ksef.map(adapter -> adapter.reimport(m))
        .orElseGet(
            () ->
                new AccountingUserApi.KsefSyncResult(
                    "NOT_CONFIGURED", 0, 0, 0, 0, 0, "KSeF is not configured."));
  }

  @Override
  public AccountingUserApi.KsefSyncResult syncKsefSeller(long p, YearMonth m) {
    profile(p);
    return ksef.map(adapter -> adapter.syncSeller(m))
        .orElseGet(
            () ->
                new AccountingUserApi.KsefSyncResult(
                    "NOT_CONFIGURED", 0, 0, 0, 0, 0, "KSeF is not configured."));
  }

  @Override
  public AccountingUserApi.KsefSyncResult syncKsefThirdParty(long p, YearMonth m) {
    profile(p);
    return ksef.map(adapter -> adapter.syncThirdParty(m))
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
