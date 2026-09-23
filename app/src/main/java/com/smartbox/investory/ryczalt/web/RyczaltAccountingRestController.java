package com.smartbox.investory.ryczalt.web;

import com.smartbox.investory.config.AuthorizationService;
import com.smartbox.investory.ryczalt.application.RyczaltAccountingApi;
import com.smartbox.investory.ryczalt.application.RyczaltInvoicePaymentService;
import com.smartbox.investory.ryczalt.application.query.RyczaltInvoiceReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltIssueReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltObligationReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltPaymentHistoryReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltPeriodListItem;
import com.smartbox.investory.ryczalt.application.query.RyczaltPeriodNotFoundException;
import com.smartbox.investory.ryczalt.application.query.RyczaltPeriodReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltTransactionReadModel;
import com.smartbox.investory.ryczalt.calculation.application.NativeMonthCalculationResult;
import com.smartbox.investory.ryczalt.reference.RyczaltObligationReferenceReader;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Native-only common accounting REST adapter. Legacy routes remain separate during migration. */
@RestController
@RequestMapping("/api/profiles/{profileId}/accounting")
public class RyczaltAccountingRestController {
  private final RyczaltAccountingApi accounting;
  private final RyczaltInvoicePaymentService invoicePayments;
  private final AuthorizationService authorization;
  private final RyczaltObligationReferenceReader referenceObligations;

  public RyczaltAccountingRestController(
      RyczaltAccountingApi accounting,
      RyczaltInvoicePaymentService invoicePayments,
      AuthorizationService authorization,
      RyczaltObligationReferenceReader referenceObligations) {
    this.accounting = accounting;
    this.invoicePayments = invoicePayments;
    this.authorization = authorization;
    this.referenceObligations = referenceObligations;
  }

  @GetMapping("/periods")
  public List<PeriodRefResponse> periods(
      @PathVariable long profileId, Authentication authentication) {
    read(profileId, authentication);
    return accounting.periods(profileId).stream().map(this::periodRef).toList();
  }

  @GetMapping("/periods/{month}")
  public PeriodResponse period(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication authentication) {
    read(profileId, authentication);
    return periodResponse(accounting.period(profileId, month));
  }

  @GetMapping("/periods/{month}/invoices")
  public List<InvoiceResponse> invoices(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication authentication) {
    read(profileId, authentication);
    return accounting.invoices(profileId, month).stream().map(this::invoice).toList();
  }

  /** Canonical native invoice filter used by counterparty detail views. */
  @GetMapping("/invoices")
  public List<InvoiceResponse> invoicesByCounterparty(
      @PathVariable long profileId,
      @RequestParam(required = false) YearMonth month,
      @RequestParam(required = false) Long counterpartyId,
      Authentication authentication) {
    read(profileId, authentication);
    return accounting.invoices(profileId, month, counterpartyId).stream()
        .map(this::invoice)
        .toList();
  }

  @PostMapping("/invoices/{invoiceId}/manual-paid")
  public ResponseEntity<Void> markInvoicePaid(
      @PathVariable long profileId,
      @PathVariable long invoiceId,
      @RequestBody ManualPaidRequest request,
      Authentication authentication) {
    write(profileId, authentication);
    command(
        () ->
            invoicePayments.markPaid(
                profileId,
                invoiceId,
                request == null ? null : request.paidDate(),
                request == null ? null : request.note()));
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/invoices/{invoiceId}/manual-paid")
  public ResponseEntity<Void> markInvoiceUnpaid(
      @PathVariable long profileId, @PathVariable long invoiceId, Authentication authentication) {
    write(profileId, authentication);
    command(() -> invoicePayments.markUnpaid(profileId, invoiceId));
    return ResponseEntity.noContent().build();
  }

  public record ManualPaidRequest(java.time.LocalDate paidDate, String note) {}

  @GetMapping("/periods/{month}/transactions")
  public List<TransactionResponse> transactions(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication authentication) {
    read(profileId, authentication);
    return accounting.transactions(profileId, month).stream().map(this::transaction).toList();
  }

  @GetMapping("/periods/{month}/obligations")
  public List<ObligationResponse> obligations(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication authentication) {
    read(profileId, authentication);
    return accounting.obligations(profileId, month).stream().map(this::obligation).toList();
  }

  @GetMapping("/periods/{month}/reference-obligations")
  public List<ReferenceObligationResponse> referenceObligations(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication authentication) {
    read(profileId, authentication);
    return referenceObligations.find(profileId, month).stream()
        .map(value -> new ReferenceObligationResponse(value.type(), value.expected()))
        .toList();
  }

  @GetMapping("/periods/{month}/issues")
  public List<IssueResponse> issues(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication authentication) {
    read(profileId, authentication);
    return accounting.issues(profileId, month).stream().map(this::issue).toList();
  }

  @GetMapping("/payments")
  public List<PaymentHistoryResponse> paymentHistory(
      @PathVariable long profileId,
      @RequestParam YearMonth from,
      @RequestParam YearMonth to,
      @RequestParam(required = false) String type,
      Authentication authentication) {
    read(profileId, authentication);
    return accounting.paymentHistory(profileId, from, to, type).stream()
        .map(this::paymentHistory)
        .toList();
  }

  @PostMapping("/periods/{month}/freeze")
  public ResponseEntity<Void> freeze(
      @PathVariable long profileId,
      @PathVariable YearMonth month,
      @RequestBody LifecycleRequest request,
      Authentication authentication) {
    write(profileId, authentication);
    command(() -> accounting.freeze(profileId, month, actor(authentication), reason(request)));
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/periods/{month}/reopen")
  public ResponseEntity<Void> reopen(
      @PathVariable long profileId,
      @PathVariable YearMonth month,
      @RequestBody LifecycleRequest request,
      Authentication authentication) {
    write(profileId, authentication);
    command(() -> accounting.reopen(profileId, month, actor(authentication), reason(request)));
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/periods/{month}/calculate")
  public NativeMonthCalculationResult calculate(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication authentication) {
    write(profileId, authentication);
    try {
      return accounting.calculateFromPersistedFacts(profileId, month);
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
    } catch (IllegalStateException exception) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
    }
  }

  public record LifecycleRequest(String reason) {}

  private void command(Runnable operation) {
    try {
      operation.run();
    } catch (RyczaltPeriodNotFoundException exception) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
    } catch (IllegalArgumentException exception) {
      if (exception.getMessage() != null && exception.getMessage().contains("does not exist")) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
      }
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
    } catch (IllegalStateException exception) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
    }
  }

  private String reason(LifecycleRequest request) {
    if (request == null || request.reason() == null || request.reason().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reason is required");
    }
    return request.reason();
  }

  private String actor(Authentication authentication) {
    return authentication == null || authentication.getName() == null
        ? "rest"
        : authentication.getName();
  }

  private void read(long profileId, Authentication authentication) {
    if (!authorization.canRead(profileId, authentication)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
  }

  private void write(long profileId, Authentication authentication) {
    if (!authorization.canWrite(profileId, authentication)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
  }

  private PeriodRefResponse periodRef(RyczaltPeriodListItem value) {
    return new PeriodRefResponse(
        value.month(),
        value.status() == com.smartbox.investory.ryczalt.domain.PeriodStatus.FROZEN
            ? AccountingPeriodLifecycle.FROZEN
            : AccountingPeriodLifecycle.OPEN);
  }

  private PeriodResponse periodResponse(RyczaltPeriodReadModel value) {
    return new PeriodResponse(
        value.month(),
        value.periodStatus() == com.smartbox.investory.ryczalt.domain.PeriodStatus.FROZEN
            ? AccountingPeriodLifecycle.FROZEN
            : AccountingPeriodLifecycle.OPEN,
        value.calculations().stream()
            .map(
                calculation ->
                    new CalculationResponse(
                        calculation.type(),
                        calculation.status().name(),
                        decimal(calculation.amount())))
            .toList(),
        new PeriodResponse.SummaryResponse(
            decimal(value.summary().revenue()),
            decimal(value.summary().ryczalt()),
            decimal(value.summary().vat()),
            decimal(value.summary().zus())),
        new PeriodResponse.AuditResponse(
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
        new PeriodResponse.DocumentsResponse(
            value.documents().invoiceCount(), value.documents().transactionCount()),
        new PeriodResponse.SettlementResponse(
            value.settlement().expectedCount(),
            value.settlement().paidCount(),
            value.settlement().outstandingCount(),
            decimal(value.settlement().totalExpected()),
            decimal(value.settlement().totalPaid()),
            decimal(value.settlement().totalOutstanding()),
            value.settlement().fullySettled()),
        new PeriodResponse.ReconciliationResponse(
            value.reconciliation().rowCount(),
            value.reconciliation().settledCount(),
            value.reconciliation().mismatchCount(),
            value.reconciliation().missingEvidenceCount()),
        new PeriodResponse.CompletenessResponse(
            value.completeness().status(), value.completeness().blockingIssueCount()),
        value.allowedActions());
  }

  private InvoiceResponse invoice(RyczaltInvoiceReadModel value) {
    return new InvoiceResponse(
        value.id(),
        value.direction().name(),
        value.reference(),
        value.issueDate(),
        value.accountingDate(),
        decimal(value.netAmount()),
        decimal(value.vatAmount()),
        decimal(value.grossAmount()),
        value.currency(),
        decimal(value.bookedNetPln()),
        decimal(value.ryczaltRate()),
        decimal(value.deductibleVat()),
        value.classification(),
        value.counterparty() == null
            ? null
            : new InvoiceResponse.CounterpartyView(
                value.counterparty().id(),
                value.counterparty().legalName(),
                value.counterparty().alias(),
                value.counterparty().taxIdentifier()),
        value.approvalStatus(),
        value.approvalMethod(),
        value.paymentVerificationPolicy(),
        value.paymentStatus(),
        value.sourceType(),
        value.sourceReference());
  }

  private TransactionResponse transaction(RyczaltTransactionReadModel value) {
    return new TransactionResponse(
        value.id(),
        value.bookingDate(),
        decimal(value.amount()),
        value.currency(),
        value.reference(),
        value.counterparty(),
        value.description(),
        decimal(value.matchedAmount()));
  }

  private ObligationResponse obligation(RyczaltObligationReadModel value) {
    return new ObligationResponse(
        value.id(),
        value.type(),
        decimal(value.expectedAmount()),
        decimal(value.paidAmount()),
        decimal(value.outstandingAmount()),
        value.currency(),
        value.dueDate(),
        value.status());
  }

  private IssueResponse issue(RyczaltIssueReadModel value) {
    return new IssueResponse(
        value.id(),
        value.code(),
        value.severity(),
        value.kind(),
        value.title(),
        value.message(),
        value.sourceReference());
  }

  public record ReferenceObligationResponse(String type, BigDecimal expected) {}

  private PaymentHistoryResponse paymentHistory(RyczaltPaymentHistoryReadModel value) {
    return new PaymentHistoryResponse(
        value.type(),
        value.period(),
        decimal(value.expectedAmount()),
        decimal(value.paidAmount()),
        decimal(value.outstandingAmount()),
        value.dueDate(),
        value.paymentDate(),
        value.status());
  }

  private String decimal(BigDecimal value) {
    return value == null ? null : value.toPlainString();
  }
}
