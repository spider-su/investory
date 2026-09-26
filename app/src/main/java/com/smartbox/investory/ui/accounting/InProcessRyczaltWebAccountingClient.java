package com.smartbox.investory.ui.accounting;

import com.smartbox.investory.ryczalt.web.RyczaltAccountingRestController;
import com.smartbox.investory.ryczalt.web.RyczaltBankImportRestController;
import com.smartbox.investory.ryczalt.web.RyczaltCounterpartyRestController;
import com.smartbox.investory.ryczalt.web.RyczaltInvoiceRecognitionRestController;
import com.smartbox.investory.ryczalt.web.RyczaltKsefRestController;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** In-process simulated REST adapter. Web calls native controllers, never legacy services. */
@Component
@Primary
public final class InProcessRyczaltWebAccountingClient implements RyczaltWebAccountingClient {
  private final RyczaltAccountingRestController accounting;
  private final RyczaltCounterpartyRestController counterparties;
  private final RyczaltInvoiceRecognitionRestController recognition;
  private final RyczaltBankImportRestController bank;
  private final RyczaltKsefRestController ksef;

  public InProcessRyczaltWebAccountingClient(
      RyczaltAccountingRestController accounting,
      RyczaltCounterpartyRestController counterparties,
      RyczaltInvoiceRecognitionRestController recognition,
      RyczaltBankImportRestController bank,
      RyczaltKsefRestController ksef) {
    this.accounting = accounting;
    this.counterparties = counterparties;
    this.recognition = recognition;
    this.bank = bank;
    this.ksef = ksef;
  }

  @Override
  public List<Month> periods(long profileId) {
    return accounting.periods(profileId, authentication()).stream()
        .map(value -> new Month(value.month(), value.status().name()))
        .toList();
  }

  @Override
  public Period period(long profileId, YearMonth month) {
    var value = accounting.period(profileId, month, authentication());
    return new Period(
        value.month(),
        value.status().name(),
        value.calculations().stream()
            .map(v -> new Calculation(v.type(), v.status(), decimal(v.amount())))
            .toList(),
        new Summary(
            decimal(value.summary().revenue()),
            decimal(value.summary().ryczalt()),
            decimal(value.summary().vat()),
            decimal(value.summary().zus())),
        new Audit(
            decimal(value.audit().revenue()),
            decimal(value.audit().socialDeduction()),
            decimal(value.audit().healthDeduction()),
            decimal(value.audit().otherDeduction()),
            decimal(value.audit().taxableBase()),
            decimal(value.audit().cumulativeTax()),
            decimal(value.audit().monthlyAdvance()),
            decimal(value.audit().outputVat()),
            decimal(value.audit().inputVat()),
            decimal(value.audit().vatAdjustments()),
            decimal(value.audit().finalPayable())),
        new Documents(value.documents().invoiceCount(), value.documents().transactionCount()),
        new Settlement(
            value.settlement().expectedCount(),
            value.settlement().paidCount(),
            value.settlement().outstandingCount(),
            decimal(value.settlement().totalExpected()),
            decimal(value.settlement().totalPaid()),
            decimal(value.settlement().totalOutstanding()),
            value.settlement().fullySettled()),
        new Reconciliation(
            value.reconciliation().rowCount(),
            value.reconciliation().settledCount(),
            value.reconciliation().mismatchCount(),
            value.reconciliation().missingEvidenceCount()),
        new Completeness(value.completeness().status(), value.completeness().blockingIssueCount()),
        value.allowedActions().stream().map(Enum::name).toList());
  }

  @Override
  public List<Invoice> invoices(long profileId, YearMonth month) {
    return accounting.invoices(profileId, month, authentication()).stream()
        .map(this::invoice)
        .toList();
  }

  @Override
  public List<Invoice> invoices(long profileId, Long counterpartyId) {
    return accounting
        .invoicesByCounterparty(profileId, null, counterpartyId, authentication())
        .stream()
        .map(this::invoice)
        .toList();
  }

  @Override
  public List<Transaction> transactions(long profileId, YearMonth month) {
    return accounting.transactions(profileId, month, authentication()).stream()
        .map(
            v ->
                new Transaction(
                    v.id(),
                    v.bookingDate(),
                    decimal(v.amount()),
                    v.currency().name(),
                    v.reference(),
                    v.counterparty(),
                    v.description(),
                    decimal(v.matchedAmount())))
        .toList();
  }

  @Override
  public List<Obligation> obligations(long profileId, YearMonth month) {
    return accounting.obligations(profileId, month, authentication()).stream()
        .map(
            v ->
                new Obligation(
                    v.id(),
                    v.type().name(),
                    decimal(v.expectedAmount()),
                    decimal(v.paidAmount()),
                    decimal(v.outstandingAmount()),
                    v.currency().name(),
                    v.dueDate(),
                    v.status().name(),
                    v.manuallyPaid(),
                    v.manualPaidDate()))
        .toList();
  }

  @Override
  public void manualObligationPaid(
      long profileId, YearMonth month, long obligationId, LocalDate paidDate, String note) {
    accounting.markObligationPaid(
        profileId,
        month,
        obligationId,
        new RyczaltAccountingRestController.ManualPaidRequest(paidDate, note),
        authentication());
  }

  @Override
  public void manualObligationUnpaid(long profileId, YearMonth month, long obligationId) {
    accounting.markObligationUnpaid(profileId, month, obligationId, authentication());
  }

  @Override
  public List<ReferenceObligation> referenceObligations(long profileId, YearMonth month) {
    return accounting.referenceObligations(profileId, month, authentication()).stream()
        .map(value -> new ReferenceObligation(value.type(), value.expected()))
        .toList();
  }

  @Override
  public List<Issue> issues(long profileId, YearMonth month) {
    return accounting.issues(profileId, month, authentication()).stream()
        .map(
            v ->
                new Issue(
                    v.id(),
                    v.code(),
                    v.severity().name(),
                    v.kind().name(),
                    v.title(),
                    v.message(),
                    v.sourceReference()))
        .toList();
  }

  @Override
  public List<PaymentHistory> paymentHistory(
      long profileId, YearMonth from, YearMonth to, String type) {
    return accounting.paymentHistory(profileId, from, to, type, authentication()).stream()
        .map(
            v ->
                new PaymentHistory(
                    v.type().name(),
                    v.period(),
                    decimal(v.expectedAmount()),
                    decimal(v.paidAmount()),
                    decimal(v.outstandingAmount()),
                    v.dueDate(),
                    v.paymentDate(),
                    v.status().name()))
        .toList();
  }

  @Override
  public List<Counterparty> counterparties(long profileId) {
    return counterparties.list(profileId, authentication()).stream()
        .map(
            v ->
                new Counterparty(
                    v.id(),
                    v.legalName(),
                    v.alias(),
                    v.displayName(),
                    v.taxIdentifier(),
                    v.country(),
                    v.bankAccount(),
                    v.ruleCount(),
                    v.invoiceCount()))
        .toList();
  }

  @Override
  public Counterparty counterparty(long profileId, long id) {
    var v = counterparties.get(profileId, id, authentication());
    return new Counterparty(
        v.id(),
        v.legalName(),
        v.alias(),
        v.displayName(),
        v.taxIdentifier(),
        v.country(),
        v.bankAccount(),
        v.ruleCount(),
        v.invoiceCount());
  }

  @Override
  public List<Rule> rules(long profileId, long id) {
    return counterparties.rules(profileId, id, authentication()).stream()
        .map(
            v ->
                new Rule(
                    v.id(),
                    v.name(),
                    v.sourceType(),
                    v.documentType(),
                    v.serviceKey(),
                    v.classification(),
                    v.vatTreatment(),
                    v.vatDeductionRatio(),
                    v.ryczaltRate(),
                    v.autoApprove(),
                    v.paymentVerificationPolicy().name()))
        .toList();
  }

  @Override
  public Candidate recognize(long profileId, String filename, String contentType, byte[] content) {
    return candidate(
        recognition.recognize(profileId, filename, contentType, content, authentication()));
  }

  @Override
  public Candidate candidate(long profileId, UUID candidateKey) {
    return candidate(recognition.candidate(profileId, candidateKey, authentication()));
  }

  @Override
  public void approveCandidate(
      long profileId,
      UUID candidateKey,
      Long counterpartyId,
      String classification,
      String vatTreatment,
      String vatDeductionRatio,
      String ryczaltRate,
      String paymentVerificationPolicy,
      boolean approve,
      boolean rememberRule,
      String ruleName,
      String serviceKey) {
    recognition.approve(
        profileId,
        new RyczaltInvoiceRecognitionRestController.ApprovalRequest(
            candidateKey,
            counterpartyId,
            classification,
            vatTreatment,
            vatDeductionRatio,
            ryczaltRate,
            paymentVerificationPolicy == null
                ? null
                : com.smartbox.investory.ryczalt.domain.PaymentVerificationPolicy.valueOf(
                    paymentVerificationPolicy),
            approve,
            rememberRule,
            ruleName,
            serviceKey),
        authentication());
  }

  @Override
  public void manualPaid(long profileId, long invoiceId, LocalDate paidDate, String note) {
    accounting.markInvoicePaid(
        profileId,
        invoiceId,
        new RyczaltAccountingRestController.ManualPaidRequest(paidDate, note),
        authentication());
  }

  @Override
  public void manualUnpaid(long profileId, long invoiceId) {
    accounting.markInvoiceUnpaid(profileId, invoiceId, authentication());
  }

  @Override
  public void addRule(long profileId, long counterpartyId, RuleForm rule) {
    counterparties.add(profileId, counterpartyId, ruleRequest(rule), authentication());
  }

  @Override
  public void updateRule(long profileId, long counterpartyId, long ruleId, RuleForm rule) {
    counterparties.update(profileId, counterpartyId, ruleId, ruleRequest(rule), authentication());
  }

  @Override
  public void deleteRule(long profileId, long counterpartyId, long ruleId) {
    counterparties.delete(profileId, counterpartyId, ruleId, authentication());
  }

  @Override
  public ImportResult importBank(
      long profileId, String filename, String contentType, byte[] content) {
    var result = bank.importBank(profileId, filename, contentType, content, authentication());
    return new ImportResult(result.received(), result.imported(), result.duplicates(), 0, 0);
  }

  @Override
  public ImportResult syncKsef(long profileId, YearMonth month) {
    var result =
        ksef.sync(
            profileId,
            new RyczaltKsefRestController.SyncRequest(
                month,
                java.util.Set.of(
                    com.smartbox.investory.ryczalt.integration.ksef.KsefSyncMode.SALES,
                    com.smartbox.investory.ryczalt.integration.ksef.KsefSyncMode.PURCHASES)),
            authentication());
    return new ImportResult(
        result.received(),
        result.imported(),
        result.duplicates(),
        result.updated(),
        result.failed());
  }

  @Override
  public void alias(long profileId, long id, String alias) {
    counterparties.alias(
        profileId, id, new RyczaltCounterpartyRestController.AliasRequest(alias), authentication());
  }

  @Override
  public void freeze(long p, YearMonth m, String reason) {
    accounting.freeze(
        p, m, new RyczaltAccountingRestController.LifecycleRequest(reason), authentication());
  }

  @Override
  public void reopen(long p, YearMonth m, String reason) {
    accounting.reopen(
        p, m, new RyczaltAccountingRestController.LifecycleRequest(reason), authentication());
  }

  private Invoice invoice(com.smartbox.investory.ryczalt.web.InvoiceResponse v) {
    return new Invoice(
        v.id(),
        v.direction(),
        v.reference(),
        v.issueDate(),
        v.accountingDate(),
        decimal(v.netAmount()),
        decimal(v.vatAmount()),
        decimal(v.grossAmount()),
        v.currency().name(),
        v.approvalStatus().name(),
        v.approvalMethod() == null ? null : v.approvalMethod().name(),
        v.paymentVerificationPolicy().name(),
        v.paymentStatus().name(),
        v.counterparty() == null
            ? null
            : (v.counterparty().alias() == null
                ? v.counterparty().legalName()
                : v.counterparty().alias()));
  }

  private Candidate candidate(
      com.smartbox.investory.ryczalt.application.RyczaltInvoiceRecognitionService.CandidateView v) {
    return new Candidate(
        v.candidateKey(),
        v.sourceType(),
        v.documentType(),
        v.direction(),
        v.issueDate(),
        v.saleDate(),
        v.dueDate(),
        v.reference(),
        v.counterpartyId(),
        v.currency(),
        v.netAmount(),
        v.vatAmount(),
        v.grossAmount(),
        v.classification(),
        v.vatTreatment(),
        v.ryczaltRate(),
        v.approvalStatus().name(),
        v.approvalMethod() == null ? null : v.approvalMethod().name(),
        v.paymentVerificationPolicy().name(),
        v.ruleMatchStatus().name(),
        v.paymentStatus().name(),
        v.sourceState().name(),
        v.periodYear(),
        v.periodMonth(),
        v.requiredInputs().stream().map(input -> input.field()).toList());
  }

  private static RyczaltCounterpartyRestController.RuleRequest ruleRequest(RuleForm rule) {
    return new RyczaltCounterpartyRestController.RuleRequest(
        rule.name(),
        rule.sourceType(),
        rule.documentType(),
        rule.serviceKey(),
        rule.classification(),
        rule.vatTreatment(),
        rule.vatDeductionRatio(),
        rule.ryczaltRate(),
        rule.autoApprove(),
        rule.paymentVerificationPolicy() == null
            ? null
            : com.smartbox.investory.ryczalt.domain.PaymentVerificationPolicy.valueOf(
                rule.paymentVerificationPolicy()));
  }

  private static BigDecimal decimal(String value) {
    return value == null ? null : new BigDecimal(value);
  }

  private static java.math.BigDecimal decimal(java.math.BigDecimal value) {
    return value;
  }

  private static org.springframework.security.core.Authentication authentication() {
    return SecurityContextHolder.getContext().getAuthentication();
  }
}
