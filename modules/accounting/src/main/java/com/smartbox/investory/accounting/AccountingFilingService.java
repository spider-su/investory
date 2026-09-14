package com.smartbox.investory.accounting;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountingFilingService {
  private final AccountingFactService factService;
  private final AccountingPocRepository repository;
  private final AccountingJpkGenerator jpkGenerator;
  private final AccountingDueDatePolicy dueDatePolicy;
  private final AccountingJpkXmlValidator jpkXmlValidator;

  public FilingResult filing(LocalDate period) {
    return filing(1L, period);
  }

  public FilingResult filing(long profileId, LocalDate period) {
    AccountingMonthSnapshot snapshot = factService.snapshot(profileId, period);
    AccountingProfile profile = repository.accountingProfile(profileId);
    AccountingFilingInput filingInput =
        new AccountingFilingService.FilingResult(period, snapshot, profile, "", false, List.of())
            .filingInput(
                repository.vatTransactionsForPeriod(profileId, period).stream()
                    .filter(t -> t.reference() != null)
                    .filter(t -> t.vatRate() != null)
                    .collect(
                        java.util.stream.Collectors.toMap(
                            AccountingVatTransaction::reference,
                            AccountingVatTransaction::vatRate,
                            (left, right) -> left)));
    String hash = AccountingFilingFingerprint.sha256(filingInput);
    AccountingPocRepository.PeriodState state = repository.periodState(profileId, period);
    boolean confirmed = state != null && hash.equals(state.confirmedCalculationHash());
    List<String> issues = new ArrayList<>();
    snapshot.issues().stream()
        .filter(i -> !"INFO".equals(i.severity()))
        .forEach(i -> issues.add(i.message()));
    snapshot
        .invoices()
        .forEach(
            invoice -> {
              if (invoice.reference() == null
                  || invoice.reference().isBlank()
                  || invoice.issueDate() == null
                  || invoice.currency() == null
                  || invoice.netAmount() == null
                  || invoice.vatAmount() == null
                  || invoice.grossAmount() == null) {
                issues.add("Incomplete invoice fields: " + invoice.reference());
              }
            });
    if (!confirmed) issues.add("Month calculation is not confirmed or changed after confirmation.");
    if (blank(profile.nip())
        || blank(profile.fullName())
        || blank(profile.taxOfficeCode())
        || blank(profile.email())
        || blank(profile.firstName())
        || blank(profile.surname())
        || profile.dateOfBirth() == null) {
      issues.add("MISSING_TAXPAYER_CONFIGURATION");
    }
    filingInput.sales().forEach(document -> validateDocument(document, issues));
    filingInput.purchases().forEach(document -> validateDocument(document, issues));
    if (positive(snapshot.vat().calculatedVat()) && blank(profile.taxMicroAccount())) {
      issues.add("MISSING_PAYMENT_CONFIGURATION: VAT");
    }
    if (positive(snapshot.ryczalt().calculatedTax()) && blank(profile.taxMicroAccount())) {
      issues.add("MISSING_PAYMENT_CONFIGURATION: RYCZALT");
    }
    if (positive(snapshot.zus().totalZus()) && blank(profile.zusPaymentAccount())) {
      issues.add("MISSING_PAYMENT_CONFIGURATION: ZUS");
    }
    return new FilingResult(period, snapshot, profile, hash, confirmed, issues);
  }

  private void validateDocument(
      AccountingFilingInput.FilingDocument document, List<String> issues) {
    if (blank(document.counterpartyIdentifier())) {
      issues.add("MISSING_COUNTERPARTY_IDENTIFIER: " + document.reference());
    }
    if (document.evidence() == null || document.evidence().type() == null) {
      issues.add("MISSING_JPK_EVIDENCE_CLASSIFICATION: " + document.reference());
    }
    if ((document.treatment() == VatTreatment.DOMESTIC_VAT
            || document.treatment() == VatTreatment.DOMESTIC_PURCHASE)
        && document.vatRate() == null) {
      issues.add("MISSING_EXPLICIT_VAT_RATE: " + document.reference());
    }
  }

  public void confirm(LocalDate period) {
    confirm(1L, period);
  }

  public void confirm(long profileId, LocalDate period) {
    FilingResult result = filing(profileId, period);
    if (!result.issues().isEmpty() && !result.onlyNotConfirmed()) {
      throw new AccountingInvalidTransitionException(
          "Cannot confirm month: " + String.join("; ", result.issues()));
    }
    var state = repository.periodState(profileId, period);
    var current = state == null ? PeriodLifecycleStatus.OPEN : state.lifecycleStatus();
    if (current == PeriodLifecycleStatus.OPEN) {
      repository.updateLifecycleStatus(profileId, period, PeriodLifecycleStatus.READY_FOR_REVIEW);
      current = PeriodLifecycleStatus.READY_FOR_REVIEW;
    }
    new AccountingPeriodLifecycle()
        .transition(current, PeriodLifecycleStatus.CONFIRMED, false, false, false);
    repository.confirm(profileId, period, result.calculationHash(), Instant.now());
    repository.updateLifecycleStatus(profileId, period, PeriodLifecycleStatus.CONFIRMED);
  }

  public void reopen(LocalDate period, String reason) {
    reopen(1L, period, reason);
  }

  public void reopen(long profileId, LocalDate period, String reason) {
    var state = repository.periodState(profileId, period);
    var current = state == null ? PeriodLifecycleStatus.OPEN : state.lifecycleStatus();
    new AccountingPeriodLifecycle().reopen(current, reason);
    repository.reopen(profileId, period, reason, Instant.now());
  }

  /** Evidence-derived filing transition. */
  public void markFiled(LocalDate period) {
    markFiled(1L, period);
  }

  public void markFiled(long profileId, LocalDate period) {
    FilingResult result = filing(profileId, period);
    if (!result.confirmed())
      throw new AccountingInvalidTransitionException("Cannot file an unconfirmed period");
    if (!repository.hasFilingArtifact(profileId, period, "JPK_V7M", result.calculationHash())
        || !repository.hasAcceptedConfirmation(
            profileId, period, "JPK_UPO", result.calculationHash())) {
      throw new AccountingInvalidTransitionException("Accepted JPK filing evidence is required");
    }
    boolean vatEuRequired =
        repository.vatTransactionsForPeriod(profileId, period).stream()
            .anyMatch(t -> t.treatment() == VatTreatment.EU_B2B_REVERSE_CHARGE);
    if (vatEuRequired
        && (!repository.hasFilingArtifact(profileId, period, "VAT_UE")
            || !repository.hasAcceptedConfirmation(
                profileId, period, "VAT_UE_UPO", result.calculationHash()))) {
      throw new AccountingInvalidTransitionException("Accepted VAT-UE filing evidence is required");
    }
    if (result.snapshot().zus().totalZus().signum() > 0
        && !repository.hasAcceptedConfirmation(
            profileId, period, "ZUS_DRA_ACCEPTANCE", result.calculationHash())) {
      throw new AccountingInvalidTransitionException("Accepted ZUS DRA evidence is required");
    }
    repository.updateLifecycleStatus(profileId, period, PeriodLifecycleStatus.FILED);
  }

  /** Evidence-derived payment transition. */
  public void markPaid(LocalDate period) {
    markPaid(1L, period);
  }

  public void markPaid(long profileId, LocalDate period) {
    AccountingMonthSnapshot snapshot = factService.snapshot(profileId, period);
    for (var obligation : payableObligations(snapshot)) {
      if (obligation.amount().signum() <= 0) continue;
      boolean paid =
          snapshot.bankTransactions().stream()
                  .filter(t -> (obligation.type() + "_PAYMENT").equals(t.transactionType()))
                  .filter(t -> "BUSINESS".equals(t.scope()))
                  .filter(t -> "PLN".equals(t.currency()))
                  .filter(t -> period.equals(t.relatedPeriod()))
                  .map(t -> t.amount().abs())
                  .reduce(BigDecimal.ZERO, BigDecimal::add)
                  .compareTo(obligation.amount())
              >= 0;
      if (!paid)
        throw new AccountingInvalidTransitionException(
            "Missing payment evidence: " + obligation.type());
    }
    repository.updateLifecycleStatus(profileId, period, PeriodLifecycleStatus.PAID);
  }

  /**
   * Evidence-derived settlement transition; authority evidence is required separately from cash.
   */
  public void settle(LocalDate period) {
    settle(1L, period);
  }

  public void settle(long profileId, LocalDate period) {
    AccountingMonthSnapshot snapshot = factService.snapshot(profileId, period);
    markFiled(profileId, period);
    for (var obligation : payableObligations(snapshot)) {
      if (obligation.amount().signum() <= 0) continue;
      String confirmationType =
          switch (obligation.type()) {
            case "ZUS" -> "ZUS_ACCOUNT_POSTING";
            default -> "TAX_ACCOUNT_POSTING";
          };
      if (!repository.hasAcceptedConfirmationForAmount(
          profileId, period, obligation.type(), confirmationType, obligation.amount()))
        throw new AccountingInvalidTransitionException(
            "Missing authority posting: " + obligation.type());
    }
    markPaid(profileId, period);
    repository.updateLifecycleStatus(profileId, period, PeriodLifecycleStatus.SETTLED);
  }

  private List<AccountingCalculationResult.CalculatedObligation> payableObligations(
      AccountingMonthSnapshot snapshot) {
    return List.of(
        new AccountingCalculationResult.CalculatedObligation(
            "VAT", snapshot.vat().calculatedVat(), snapshot.period()),
        new AccountingCalculationResult.CalculatedObligation(
            "RYCZALT", snapshot.ryczalt().calculatedTax(), snapshot.period()),
        new AccountingCalculationResult.CalculatedObligation(
            "ZUS", snapshot.zus().totalZus(), snapshot.period()));
  }

  public void lock(LocalDate period) {
    lock(1L, period);
  }

  public void lock(long profileId, LocalDate period) {
    var state = repository.periodState(profileId, period);
    if (state == null || state.lifecycleStatus() != PeriodLifecycleStatus.SETTLED)
      throw new AccountingInvalidTransitionException("Only a settled period can be locked");
    repository.updateLifecycleStatus(profileId, period, PeriodLifecycleStatus.LOCKED);
  }

  public byte[] jpk(LocalDate period) {
    return jpk(1L, period);
  }

  public byte[] jpk(long profileId, LocalDate period) {
    FilingResult result = filing(profileId, period);
    if (!result.ready())
      throw new AccountingInvalidTransitionException(String.join("; ", result.issues()));
    var existing = repository.filingArtifact(profileId, period, "JPK_V7M");
    if (existing.isPresent() && result.calculationHash().equals(existing.get().calculationHash())) {
      return existing.get().payload();
    }
    byte[] payload = jpkGenerator.generate(result);
    jpkXmlValidator.validate(payload);
    repository.saveFilingArtifact(
        profileId,
        new AccountingFilingArtifact(
            AccountingFilingArtifact.Type.JPK_V7M,
            period,
            "JPK_V7M_3",
            payload,
            AccountingFilingFingerprint.sha256(payload),
            result.calculationHash(),
            Instant.now(),
            AccountingFilingArtifact.Status.VALID));
    return payload;
  }

  /** Records imported/manual authority evidence; no government submission is performed. */
  public void recordAuthorityConfirmation(AuthorityConfirmation confirmation) {
    recordAuthorityConfirmation(1L, confirmation);
  }

  public void recordAuthorityConfirmation(long profileId, AuthorityConfirmation confirmation) {
    String hash =
        confirmation.calculationHash() == null
            ? filing(profileId, confirmation.period()).calculationHash()
            : confirmation.calculationHash();
    repository.saveAuthorityConfirmation(
        profileId,
        new AuthorityConfirmation(
            confirmation.authority(),
            confirmation.obligationOrArtifactType(),
            confirmation.period(),
            confirmation.externalReference(),
            confirmation.confirmationType(),
            confirmation.status(),
            confirmation.receivedAt(),
            confirmation.sourceDocumentId(),
            confirmation.note(),
            confirmation.amount(),
            hash));
  }

  public List<AccountingPaymentInstruction> paymentInstructions(LocalDate period) {
    return paymentInstructions(1L, period);
  }

  public List<AccountingPaymentInstruction> paymentInstructions(long profileId, LocalDate period) {
    FilingResult result = filing(profileId, period);
    if (!result.ready()) throw new IllegalStateException(String.join("; ", result.issues()));
    AccountingMonthSnapshot s = result.snapshot();
    AccountingProfile p = result.profile();
    List<AccountingPaymentInstruction> output = new ArrayList<>();
    add(
        output,
        period,
        "VAT",
        s.vat().calculatedVat(),
        p.taxMicroAccount(),
        "VAT-7",
        s.obligations());
    add(
        output,
        period,
        "RYCZALT",
        s.ryczalt().calculatedTax(),
        p.taxMicroAccount(),
        "RYCZALT",
        s.obligations());
    add(output, period, "ZUS", s.zus().totalZus(), p.zusPaymentAccount(), "ZUS", s.obligations());
    return output;
  }

  private void add(
      List<AccountingPaymentInstruction> out,
      LocalDate period,
      String type,
      BigDecimal amount,
      String account,
      String title,
      List<AccountingMonthSnapshot.ObligationRow> obligations) {
    if (amount == null || amount.signum() <= 0) return;
    if (blank(account)) throw new IllegalStateException("MISSING_PAYMENT_CONFIGURATION: " + type);
    AccountingMonthSnapshot.ObligationRow existing =
        obligations.stream().filter(o -> type.equals(o.obligationType())).findFirst().orElse(null);
    BigDecimal paid =
        existing == null || existing.paidAmount() == null ? BigDecimal.ZERO : existing.paidAmount();
    String status = dueDatePolicy.paymentStatus(amount, paid).name();
    out.add(
        new AccountingPaymentInstruction(
            type,
            amount,
            dueDatePolicy.dueDate(period, type),
            type.equals("ZUS") ? "ZUS" : "TAX_OFFICE",
            account,
            title + " " + period,
            period,
            status,
            paid));
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private boolean positive(BigDecimal value) {
    return value != null && value.signum() > 0;
  }

  public record FilingResult(
      LocalDate period,
      AccountingMonthSnapshot snapshot,
      AccountingProfile profile,
      String calculationHash,
      boolean confirmed,
      List<String> issues) {
    public List<AccountingFilingIssue> typedIssues() {
      return issues.stream().map(FilingResult::typedIssue).toList();
    }

    public boolean onlyNotConfirmed() {
      return !issues.isEmpty()
          && typedIssues().stream()
              .allMatch(issue -> issue.code() == AccountingFilingIssueCode.NOT_CONFIRMED);
    }

    private static AccountingFilingIssue typedIssue(String issue) {
      if (issue.startsWith("MISSING_TAXPAYER_CONFIGURATION"))
        return new AccountingFilingIssue(
            AccountingFilingIssueCode.MISSING_TAXPAYER_CONFIGURATION, null, issue);
      if (issue.startsWith("MISSING_PAYMENT_CONFIGURATION"))
        return new AccountingFilingIssue(
            AccountingFilingIssueCode.MISSING_PAYMENT_CONFIGURATION, null, issue);
      if (issue.startsWith("MISSING_COUNTERPARTY_IDENTIFIER"))
        return new AccountingFilingIssue(
            AccountingFilingIssueCode.MISSING_COUNTERPARTY_IDENTIFIER, null, issue);
      if (issue.startsWith("MISSING_JPK_EVIDENCE_CLASSIFICATION"))
        return new AccountingFilingIssue(
            AccountingFilingIssueCode.MISSING_JPK_EVIDENCE_CLASSIFICATION, null, issue);
      if (issue.startsWith("Month calculation"))
        return new AccountingFilingIssue(AccountingFilingIssueCode.NOT_CONFIRMED, null, issue);
      return new AccountingFilingIssue(
          AccountingFilingIssueCode.CALCULATION_INCOMPLETE, null, issue);
    }

    public AccountingFilingInput filingInput() {
      return filingInput(java.util.Map.of());
    }

    public AccountingFilingInput filingInput(java.util.Map<String, BigDecimal> vatRates) {
      var sales =
          snapshot.invoices().stream()
              .filter(
                  invoice ->
                      "SALES_INVOICE".equals(invoice.invoiceKind())
                          || "DOMESTIC_SERVICE".equals(invoice.invoiceKind())
                          || "EU_SERVICE".equals(invoice.invoiceKind())
                          || "CREDIT_NOTE".equals(invoice.invoiceKind()))
              .map(
                  invoice ->
                      new AccountingFilingInput.FilingDocument(
                          invoice.reference(),
                          invoice.issueDate(),
                          invoice.saleDate(),
                          null,
                          invoice.counterpartyTaxIdentifier(),
                          invoice.customerAlias(),
                          filingNetAmount(invoice),
                          invoice.vatAmount(),
                          invoice.vatAmount(),
                          invoice.filingEvidence() == null && invoice.ksefNumber() != null
                              ? new AccountingFilingEvidence(
                                  AccountingFilingEvidence.Type.KSEF, invoice.ksefNumber())
                              : invoice.filingEvidence(),
                          filingTreatment(invoice.invoiceKind()),
                          invoice.counterpartyCountry(),
                          resolveVatRate(
                              vatRates.get(invoice.reference()),
                              filingNetAmount(invoice),
                              invoice.vatAmount()),
                          BigDecimal.ONE))
              .toList();
      var purchases =
          snapshot.expenses().stream()
              .map(
                  expense ->
                      new AccountingFilingInput.FilingDocument(
                          expense.reference(),
                          expense.invoiceDate(),
                          null,
                          expense.invoiceDate(),
                          expense.counterpartyTaxIdentifier(),
                          expense.supplierAlias(),
                          expense.netAmount(),
                          expense.vatAmount(),
                          expense.deductibleVat(),
                          expense.filingEvidence() == null && expense.ksefNumber() != null
                              ? new AccountingFilingEvidence(
                                  AccountingFilingEvidence.Type.KSEF, expense.ksefNumber())
                              : expense.filingEvidence(),
                          VatTreatment.DOMESTIC_PURCHASE,
                          expense.counterpartyCountry(),
                          resolveVatRate(
                              vatRates.get(expense.reference()),
                              expense.netAmount(),
                              expense.vatAmount()),
                          expense.vatDeductionRatio()))
              .toList();
      return new AccountingFilingInput(
          period,
          snapshot.vat(),
          snapshot.ryczalt(),
          snapshot.zus(),
          sales,
          purchases,
          profile,
          "JPK_V7M(3)");
    }

    private BigDecimal filingNetAmount(AccountingMonthSnapshot.InvoiceRow invoice) {
      if (!"PLN".equalsIgnoreCase(invoice.currency()) && invoice.bookedNetPln() != null) {
        return invoice.bookedNetPln();
      }
      return invoice.netAmount();
    }

    private BigDecimal resolveVatRate(
        BigDecimal explicitRate, BigDecimal netAmount, BigDecimal vatAmount) {
      if (explicitRate != null) return explicitRate;
      if (vatAmount == null || vatAmount.signum() == 0) return BigDecimal.ZERO;
      if (netAmount == null || netAmount.signum() == 0) return null;
      return vatAmount
          .divide(netAmount, 4, java.math.RoundingMode.HALF_UP)
          .multiply(new BigDecimal("100"))
          .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private VatTreatment filingTreatment(String invoiceKind) {
      return switch (invoiceKind) {
        case "EU_SERVICE" -> VatTreatment.EU_B2B_REVERSE_CHARGE;
        case "DOMESTIC_SERVICE", "SALES_INVOICE", "CREDIT_NOTE" -> VatTreatment.DOMESTIC_VAT;
        default -> null;
      };
    }

    public boolean ready() {
      return confirmed && issues.isEmpty();
    }
  }
}
