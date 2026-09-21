package com.smartbox.investory.ui.accounting;

import com.smartbox.investory.accounting.api.AccountingUserApi;
import com.smartbox.investory.ryczalt.web.RyczaltAccountingRestController;
import com.smartbox.investory.ryczalt.web.RyczaltCounterpartyRestController;
import com.smartbox.investory.ryczalt.web.RyczaltInvoiceRecognitionRestController;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** In-process simulated REST adapter. Web calls native controllers, never legacy services. */
@Component
@Primary
public final class InProcessRyczaltWebAccountingClient implements RyczaltWebAccountingClient {
  private final RyczaltAccountingRestController accounting;
  private final RyczaltCounterpartyRestController counterparties;
  private final AccountingUserApi accountingReference;

  public InProcessRyczaltWebAccountingClient(
      RyczaltAccountingRestController accounting,
      RyczaltCounterpartyRestController counterparties,
      RyczaltInvoiceRecognitionRestController ignoredRecognitionController,
      @Qualifier("legacyAccountingApiBridge") AccountingUserApi accountingReference) {
    this.accounting = accounting;
    this.counterparties = counterparties;
    this.accountingReference = accountingReference;
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
  public Reference reference(long profileId, YearMonth month) {
    var value = accountingReference.overview(profileId, month).reference();
    if (value == null || !value.available()) {
      return new Reference(false, null, null, null, null, null, null, null, 0, 0, null);
    }
    return new Reference(
        true,
        value.revenue(),
        value.expenses(),
        value.outputVat(),
        value.deductibleInputVat(),
        value.vatPayable(),
        value.ryczalt(),
        value.zus(),
        value.documentCount(),
        value.bankCount(),
        value.filingStatus());
  }

  @Override
  public List<Invoice> invoices(long profileId, YearMonth month) {
    return accounting.invoices(profileId, month, authentication()).stream()
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
                    v.status().name()))
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
  public void alias(long profileId, long id, String alias) {
    counterparties.alias(
        profileId, id, new RyczaltCounterpartyRestController.AliasRequest(alias), authentication());
  }

  @Override
  public void settle(long p, YearMonth m) {
    accounting.settle(p, m, authentication());
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
        v.approvalMethod().name(),
        v.paymentVerificationPolicy().name(),
        v.paymentStatus().name(),
        v.counterparty() == null
            ? null
            : (v.counterparty().alias() == null
                ? v.counterparty().legalName()
                : v.counterparty().alias()));
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
