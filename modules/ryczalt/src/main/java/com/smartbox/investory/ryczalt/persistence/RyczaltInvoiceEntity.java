package com.smartbox.investory.ryczalt.persistence;

import com.smartbox.investory.ryczalt.domain.ApprovalMethod;
import com.smartbox.investory.ryczalt.domain.ApprovalStatus;
import com.smartbox.investory.ryczalt.domain.PaymentVerificationPolicy;
import com.smartbox.investory.shared.currency.CurrencyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "ryczalt_invoice", schema = "investory")
public class RyczaltInvoiceEntity extends RyczaltEntity {
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "counterparty_id")
  private RyczaltCounterpartyEntity counterparty;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "period_id", nullable = false)
  private RyczaltPeriodEntity period;

  @Column(name = "profile_id", nullable = false)
  private long profileId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 8)
  private InvoiceDirection direction;

  @Column(nullable = false, length = 128)
  private String reference;

  @Column(name = "issue_date", nullable = false)
  private LocalDate issueDate;

  @Column(name = "accounting_date", nullable = false)
  private LocalDate accountingDate;

  @Column(name = "net_amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal netAmount;

  @Column(name = "vat_amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal vatAmount;

  @Column(name = "gross_amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal grossAmount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 3)
  private CurrencyType currency;

  @Column(name = "booked_net_pln", precision = 19, scale = 4)
  private BigDecimal bookedNetPln;

  @Column(name = "ryczalt_rate", precision = 7, scale = 4)
  private BigDecimal ryczaltRate;

  @Column(name = "deductible_vat", precision = 19, scale = 4)
  private BigDecimal deductibleVat;

  @Enumerated(EnumType.STRING)
  @Column(name = "approval_status", nullable = false, length = 16)
  private ApprovalStatus approvalStatus = ApprovalStatus.NEEDS_REVIEW;

  @Enumerated(EnumType.STRING)
  @Column(name = "approval_method", length = 32)
  private ApprovalMethod approvalMethod;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_verification_policy", nullable = false, length = 16)
  private PaymentVerificationPolicy paymentVerificationPolicy = PaymentVerificationPolicy.REQUIRED;

  @Column(name = "payment_status", nullable = false, length = 20)
  private String paymentStatus = "UNMATCHED";

  @Column(name = "manual_paid_date")
  private LocalDate manualPaidDate;

  @Column(name = "manual_paid_note", length = 1000)
  private String manualPaidNote;

  @Column(length = 128)
  private String classification;

  @Column(name = "vat_treatment", length = 128)
  private String vatTreatment;

  @Column(name = "vat_deduction_ratio", precision = 7, scale = 6)
  private BigDecimal vatDeductionRatio;

  protected RyczaltInvoiceEntity() {}

  public RyczaltInvoiceEntity(
      RyczaltPeriodEntity period,
      long profileId,
      InvoiceDirection direction,
      String reference,
      LocalDate issueDate,
      LocalDate accountingDate,
      BigDecimal netAmount,
      BigDecimal vatAmount,
      BigDecimal grossAmount,
      CurrencyType currency,
      BigDecimal bookedNetPln,
      BigDecimal ryczaltRate,
      BigDecimal deductibleVat) {
    this.period = period;
    this.profileId = profileId;
    this.direction = direction;
    this.reference = reference;
    this.issueDate = issueDate;
    this.accountingDate = accountingDate;
    this.netAmount = netAmount;
    this.vatAmount = vatAmount;
    this.grossAmount = grossAmount;
    this.currency = currency;
    this.bookedNetPln = bookedNetPln;
    this.ryczaltRate = ryczaltRate;
    this.deductibleVat = deductibleVat;
  }

  public RyczaltPeriodEntity getPeriod() {
    return period;
  }

  public void moveToPeriod(RyczaltPeriodEntity period) {
    if (period == null) throw new IllegalArgumentException("Invoice period is required");
    this.period = period;
  }

  public long getProfileId() {
    return profileId;
  }

  public InvoiceDirection getDirection() {
    return direction;
  }

  public String getReference() {
    return reference;
  }

  public LocalDate getIssueDate() {
    return issueDate;
  }

  public LocalDate getAccountingDate() {
    return accountingDate;
  }

  public BigDecimal getNetAmount() {
    return netAmount;
  }

  public BigDecimal getVatAmount() {
    return vatAmount;
  }

  public BigDecimal getGrossAmount() {
    return grossAmount;
  }

  public CurrencyType getCurrency() {
    return currency;
  }

  public BigDecimal getBookedNetPln() {
    return bookedNetPln;
  }

  public BigDecimal getRyczaltRate() {
    return ryczaltRate;
  }

  public BigDecimal getDeductibleVat() {
    return deductibleVat;
  }

  public RyczaltCounterpartyEntity getCounterparty() {
    return counterparty;
  }

  public ApprovalStatus getApprovalStatus() {
    return approvalStatus;
  }

  public ApprovalMethod getApprovalMethod() {
    return approvalMethod;
  }

  public PaymentVerificationPolicy getPaymentVerificationPolicy() {
    return paymentVerificationPolicy;
  }

  public String getPaymentStatus() {
    return paymentStatus;
  }

  public LocalDate getManualPaidDate() {
    return manualPaidDate;
  }

  public String getManualPaidNote() {
    return manualPaidNote;
  }

  public void markManuallyPaid(LocalDate paidDate, String note) {
    if (paidDate == null) throw new IllegalArgumentException("Payment date is required");
    this.paymentStatus = "MANUALLY_CONFIRMED";
    this.manualPaidDate = paidDate;
    this.manualPaidNote = note == null || note.isBlank() ? null : note.trim();
  }

  public void markUnpaid() {
    this.paymentStatus = "UNMATCHED";
    this.manualPaidDate = null;
    this.manualPaidNote = null;
  }

  public String getClassification() {
    return classification;
  }

  public String getVatTreatment() {
    return vatTreatment;
  }

  public BigDecimal getVatDeductionRatio() {
    return vatDeductionRatio;
  }

  public void applyDecision(
      RyczaltCounterpartyEntity counterparty,
      String classification,
      String vatTreatment,
      BigDecimal vatDeductionRatio,
      BigDecimal ryczaltRate,
      PaymentVerificationPolicy policy,
      ApprovalStatus status,
      ApprovalMethod method) {
    this.counterparty = counterparty;
    this.classification = classification;
    this.vatTreatment = vatTreatment;
    this.vatDeductionRatio = vatDeductionRatio;
    this.ryczaltRate = ryczaltRate;
    this.paymentVerificationPolicy = policy;
    this.approvalStatus = status;
    this.approvalMethod = method;
    this.paymentStatus =
        policy == PaymentVerificationPolicy.NOT_REQUIRED ? "NOT_REQUIRED" : "UNMATCHED";
    this.manualPaidDate = null;
    this.manualPaidNote = null;
  }

  public void applyCounterpartyRule(
      RyczaltCounterpartyEntity counterparty, boolean approve, PaymentVerificationPolicy policy) {
    this.counterparty = counterparty;
    this.paymentVerificationPolicy = policy;
    this.approvalStatus = approve ? ApprovalStatus.APPROVED : ApprovalStatus.NEEDS_REVIEW;
    this.approvalMethod = approve ? ApprovalMethod.COUNTERPARTY_RULE : null;
  }

  public void update(
      LocalDate issueDate,
      LocalDate accountingDate,
      BigDecimal netAmount,
      BigDecimal vatAmount,
      BigDecimal grossAmount,
      CurrencyType currency) {
    this.issueDate = issueDate;
    this.accountingDate = accountingDate;
    this.netAmount = netAmount;
    this.vatAmount = vatAmount;
    this.grossAmount = grossAmount;
    this.currency = currency;
  }

  public Long id() {
    return getId();
  }
}
