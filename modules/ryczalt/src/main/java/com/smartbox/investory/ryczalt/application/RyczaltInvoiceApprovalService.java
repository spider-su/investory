package com.smartbox.investory.ryczalt.application;

import com.smartbox.investory.ryczalt.calculation.InputChange;
import com.smartbox.investory.ryczalt.domain.*;
import com.smartbox.investory.ryczalt.persistence.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
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
            .findLockedByProfileIdAndCandidateKey(profileId, key)
            .orElseThrow(() -> new RyczaltInvoiceCandidateNotFoundException(profileId));
    if (candidate.isConsumed()) throw new RyczaltInvoiceCandidateConsumedException();
    Long originalCounterpartyId = candidate.getCounterpartyId();
    long cpId =
        command.counterpartyId() == null
            ? requireCounterparty(originalCounterpartyId)
            : command.counterpartyId();
    boolean counterpartyChanged = originalCounterpartyId == null || cpId != originalCounterpartyId;
    var cp =
        counterparties
            .findByIdAndProfileId(cpId, profileId)
            .orElseThrow(() -> new RyczaltCounterpartyNotFoundException(profileId, cpId));
    String classification =
        explicit(command.classification())
            ? command.classification()
            : counterpartyChanged ? null : candidate.getClassification();
    String vatTreatment =
        explicit(command.vatTreatment())
            ? command.vatTreatment()
            : counterpartyChanged ? null : candidate.getVatTreatment();
    BigDecimal ratio =
        command.vatDeductionRatio() != null
            ? command.vatDeductionRatio()
            : counterpartyChanged ? null : candidate.getVatDeductionRatio();
    BigDecimal rate =
        command.ryczaltRate() != null
            ? command.ryczaltRate()
            : counterpartyChanged ? null : candidate.getRyczaltRate();
    PaymentVerificationPolicy policy =
        command.paymentVerificationPolicy() != null
            ? command.paymentVerificationPolicy()
            : counterpartyChanged
                ? PaymentVerificationPolicy.REQUIRED
                : candidate.getPaymentVerificationPolicy();
    if (command.approve() && (blank(classification) || rate == null))
      throw new IllegalArgumentException(
          "Classification and Ryczalt rate are required for approval");
    if (sources
        .findByProfileIdAndEntityTypeAndSourceAndExternalId(
            profileId, "INVOICE", candidate.getSourceType(), candidate.getSourceExternalId())
        .isPresent()) throw new RyczaltInvoiceSourceConflictException();
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
    ApprovalStatus status =
        command.approve() ? ApprovalStatus.APPROVED : ApprovalStatus.NEEDS_REVIEW;
    ApprovalMethod method =
        command.approve()
            ? (!counterpartyChanged
                    && candidate.getApprovalMethod() == ApprovalMethod.COUNTERPARTY_RULE
                ? ApprovalMethod.COUNTERPARTY_RULE
                : ApprovalMethod.MANUAL)
            : null;
    candidate.apply(classification, vatTreatment, ratio, rate, policy, status, method);
    candidate.setCounterpartyId(cpId);
    RyczaltInvoiceEntity invoice;
    try {
      invoice =
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
                  rate,
                  ratio));
      invoice.applyDecision(cp, classification, vatTreatment, ratio, rate, policy, status, method);
      invoices.save(invoice);
      sources.save(
          new RyczaltSourceReferenceEntity(
              profileId,
              "INVOICE",
              invoice.id(),
              candidate.getSourceType(),
              candidate.getSourceExternalId(),
              null));
      sources.flush();
    } catch (DataIntegrityViolationException exception) {
      if (knownSourceConflict(exception)) throw new RyczaltInvoiceSourceConflictException();
      throw exception;
    }
    candidate.consume();
    candidates.save(candidate);
    if (command.rememberRule()
        && originalCounterpartyId != null
        && cpId == originalCounterpartyId
        && !blank(
            command.serviceKey() == null ? candidate.getServiceKey() : command.serviceKey())) {
      String serviceKey =
          command.serviceKey() == null ? candidate.getServiceKey() : command.serviceKey();
      counterpartyService.addRule(
          profileId,
          cpId,
          new RyczaltCounterpartyService.RuleCommand(
              command.ruleName() == null ? candidate.getReference() : command.ruleName(),
              candidate.getSourceType(),
              candidate.getDocumentType(),
              serviceKey,
              classification,
              vatTreatment,
              ratio,
              rate,
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

  private static boolean explicit(String value) {
    return value != null && !value.isBlank();
  }

  private static long requireCounterparty(Long value) {
    if (value == null) throw new IllegalArgumentException("Counterparty is required");
    return value;
  }

  private static boolean knownSourceConflict(DataIntegrityViolationException exception) {
    Throwable cause = exception;
    while (cause != null) {
      if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
        String name = violation.getConstraintName();
        return "uq_ryczalt_source_reference".equals(name)
            || "uq_ryczalt_invoice_source".equals(name);
      }
      cause = cause.getCause();
    }
    return false;
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
