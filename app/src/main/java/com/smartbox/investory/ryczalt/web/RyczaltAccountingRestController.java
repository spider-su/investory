package com.smartbox.investory.ryczalt.web;

import com.smartbox.investory.config.AuthorizationService;
import com.smartbox.investory.ryczalt.application.RyczaltAccountingApi;
import com.smartbox.investory.ryczalt.application.query.RyczaltInvoiceReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltIssueReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltObligationReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltPaymentHistoryReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltPeriodListItem;
import com.smartbox.investory.ryczalt.application.query.RyczaltPeriodNotFoundException;
import com.smartbox.investory.ryczalt.application.query.RyczaltPeriodReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltTransactionReadModel;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
  private final AuthorizationService authorization;

  public RyczaltAccountingRestController(
      RyczaltAccountingApi accounting, AuthorizationService authorization) {
    this.accounting = accounting;
    this.authorization = authorization;
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
      @RequestParam YearMonth month,
      @RequestParam long counterpartyId,
      Authentication authentication) {
    read(profileId, authentication);
    return accounting.invoices(profileId, month, counterpartyId).stream()
        .map(this::invoice)
        .toList();
  }

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

  @PostMapping("/periods/{month}/settle")
  public ResponseEntity<Void> settle(
      @PathVariable long profileId, @PathVariable YearMonth month, Authentication authentication) {
    write(profileId, authentication);
    command(() -> accounting.settle(profileId, month));
    return ResponseEntity.noContent().build();
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
    return new PeriodRefResponse(value.month(), value.status());
  }

  private PeriodResponse periodResponse(RyczaltPeriodReadModel value) {
    return new PeriodResponse(
        value.month(),
        value.periodStatus(),
        value.calculations().stream()
            .map(
                calculation ->
                    new CalculationResponse(
                        calculation.type(), calculation.status(), decimal(calculation.amount())))
            .toList(),
        new PeriodResponse.SummaryResponse(
            decimal(value.summary().revenue()),
            decimal(value.summary().ryczalt()),
            decimal(value.summary().vat()),
            decimal(value.summary().zus())),
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
        value.allowedActions().stream()
            .map(Enum::name)
            .collect(java.util.stream.Collectors.toSet()));
  }

  private InvoiceResponse invoice(RyczaltInvoiceReadModel value) {
    return new InvoiceResponse(
        value.id(),
        value.direction(),
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
        value.counterparty() == null
            ? null
            : new InvoiceResponse.CounterpartyView(
                value.counterparty().id(),
                value.counterparty().legalName(),
                value.counterparty().alias()),
        value.approvalStatus(),
        value.approvalMethod(),
        value.paymentVerificationPolicy(),
        value.paymentStatus());
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
