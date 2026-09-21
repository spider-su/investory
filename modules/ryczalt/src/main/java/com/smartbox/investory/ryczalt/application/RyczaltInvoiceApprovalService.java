package com.smartbox.investory.ryczalt.application;

import com.smartbox.investory.ryczalt.calculation.InputChange;
import com.smartbox.investory.ryczalt.domain.*;
import com.smartbox.investory.ryczalt.persistence.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns the candidate-to-canonical-invoice transaction. */
@Service
public class RyczaltInvoiceApprovalService {
  private final RyczaltInvoiceCandidateJpaRepository candidates;
  private final RyczaltCounterpartyJpaRepository counterparties;
  private final RyczaltInvoiceJpaRepository invoices;
  private final RyczaltPeriodJpaRepository periods;
  private final RyczaltSourceReferenceJpaRepository sources;
  private final RyczaltPeriodLifecycleService lifecycle;
  private final RyczaltCounterpartyService counterpartyService;

  public RyczaltInvoiceApprovalService(
      RyczaltInvoiceCandidateJpaRepository candidates,
      RyczaltCounterpartyJpaRepository counterparties,
      RyczaltInvoiceJpaRepository invoices,
      RyczaltPeriodJpaRepository periods,
      RyczaltSourceReferenceJpaRepository sources,
      RyczaltPeriodLifecycleService lifecycle,
      RyczaltCounterpartyService counterpartyService) {
    this.candidates = candidates;
    this.counterparties = counterparties;
    this.invoices = invoices;
    this.periods = periods;
    this.sources = sources;
    this.lifecycle = lifecycle;
    this.counterpartyService = counterpartyService;
  }

  @Transactional
  public InvoiceView approve(long profileId, UUID key, ApproveCommand command) {
    var candidate =
        candidates
            .findByProfileIdAndCandidateKey(profileId, key)
            .orElseThrow(() -> new RyczaltInvoiceCandidateNotFoundException(profileId));
    if (candidate.isConsumed())
      throw new IllegalStateException("Invoice candidate was already consumed");
    if (candidate.getCounterpartyId() == null)
      throw new IllegalArgumentException("Counterparty is required");
    Long originalCounterpartyId = candidate.getCounterpartyId();
    long cpId =
        command.counterpartyId() == null ? candidate.getCounterpartyId() : command.counterpartyId();
    var cp =
        counterparties
            .findByIdAndProfileId(cpId, profileId)
            .orElseThrow(() -> new RyczaltCounterpartyNotFoundException(profileId, cpId));
    PaymentVerificationPolicy policy =
        command.paymentVerificationPolicy() == null
            ? PaymentVerificationPolicy.REQUIRED
            : command.paymentVerificationPolicy();
    if (command.approve() && (blank(command.classification()) || command.ryczaltRate() == null))
      throw new IllegalArgumentException(
          "Classification and Ryczalt rate are required for approval");
    ApprovalStatus status =
        command.approve() ? ApprovalStatus.APPROVED : ApprovalStatus.NEEDS_REVIEW;
    candidate.apply(
        command.classification(),
        command.vatTreatment(),
        command.vatDeductionRatio(),
        command.ryczaltRate(),
        policy,
        status,
        command.approve() ? "MANUAL" : null);
    candidate.setCounterpartyId(cpId);
    YearMonth month = YearMonth.of(candidate.getPeriodYear(), candidate.getPeriodMonth());
    var period =
        periods
            .findByProfileIdAndYearAndMonth(profileId, month.getYear(), month.getMonthValue())
            .orElseGet(
                () ->
                    periods.save(
                        new RyczaltPeriodEntity(
                            profileId, month.getYear(), month.getMonthValue(), PeriodStatus.OPEN)));
    if (period.getStatus() == PeriodStatus.FROZEN)
      throw new FrozenPeriodMutationException(profileId, month.getYear(), month.getMonthValue());
    var invoice =
        invoices.save(
            new RyczaltInvoiceEntity(
                period,
                profileId,
                candidate.getDirection(),
                candidate.getReference(),
                candidate.getIssueDate(),
                candidate.getSaleDate() == null
                    ? candidate.getIssueDate()
                    : candidate.getSaleDate(),
                candidate.getNetAmount(),
                candidate.getVatAmount(),
                candidate.getGrossAmount(),
                CurrencyType.valueOf(candidate.getCurrency()),
                null,
                command.ryczaltRate(),
                command.vatDeductionRatio()));
    invoice.applyDecision(
        cp,
        command.classification(),
        command.vatTreatment(),
        command.vatDeductionRatio(),
        command.ryczaltRate(),
        policy,
        status,
        command.approve() ? ApprovalMethod.MANUAL : null);
    invoices.save(invoice);
    sources.save(
        new RyczaltSourceReferenceEntity(
            profileId,
            "INVOICE",
            invoice.id(),
            candidate.getSourceType(),
            candidate.getSourceExternalId(),
            null));
    candidate.consume();
    candidates.save(candidate);
    if (command.rememberRule()
        && originalCounterpartyId != null
        && cpId == originalCounterpartyId) {
      counterpartyService.addRule(
          profileId,
          cpId,
          new RyczaltCounterpartyService.RuleCommand(
              command.ruleName() == null ? candidate.getReference() : command.ruleName(),
              candidate.getSourceType(),
              candidate.getDocumentType(),
              command.serviceKey(),
              command.classification(),
              command.vatTreatment(),
              command.vatDeductionRatio(),
              command.ryczaltRate(),
              command.approve(),
              policy));
    }
    lifecycle.invalidate(
        profileId,
        month,
        candidate.getDirection() == InvoiceDirection.INCOME
            ? InputChange.INCOME_INVOICE_CHANGED
            : InputChange.COST_INVOICE_CHANGED,
        "INVOICE_APPROVAL");
    return new InvoiceView(
        invoice.id(),
        profileId,
        candidate.getDirection(),
        invoice.getReference(),
        invoice.getIssueDate(),
        invoice.getAccountingDate(),
        invoice.getCurrency().name(),
        decimal(invoice.getNetAmount()),
        decimal(invoice.getVatAmount()),
        decimal(invoice.getGrossAmount()),
        invoice.getApprovalStatus(),
        invoice.getPaymentVerificationPolicy(),
        invoice.getPaymentStatus());
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static String decimal(BigDecimal value) {
    return value == null ? null : value.toPlainString();
  }

  public record ApproveCommand(
      Long counterpartyId,
      String classification,
      String vatTreatment,
      BigDecimal vatDeductionRatio,
      BigDecimal ryczaltRate,
      PaymentVerificationPolicy paymentVerificationPolicy,
      boolean approve,
      boolean rememberRule,
      String ruleName,
      String serviceKey) {}

  public record InvoiceView(
      long id,
      long profileId,
      InvoiceDirection direction,
      String reference,
      java.time.LocalDate issueDate,
      java.time.LocalDate accountingDate,
      String currency,
      String netAmount,
      String vatAmount,
      String grossAmount,
      ApprovalStatus approvalStatus,
      PaymentVerificationPolicy paymentVerificationPolicy,
      String paymentStatus) {}
}
