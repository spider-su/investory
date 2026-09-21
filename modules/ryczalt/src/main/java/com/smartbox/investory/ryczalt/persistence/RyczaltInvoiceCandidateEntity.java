package com.smartbox.investory.ryczalt.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbox.investory.ryczalt.domain.ApprovalStatus;
import com.smartbox.investory.ryczalt.domain.PaymentVerificationPolicy;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ryczalt_invoice_candidate", schema = "investory")
public class RyczaltInvoiceCandidateEntity extends RyczaltEntity {
  private static final ObjectMapper JSON = new ObjectMapper();

  @Column(name = "profile_id", nullable = false)
  private long profileId;

  @Column(name = "candidate_key", nullable = false)
  private UUID candidateKey;

  @Column(name = "source_type", nullable = false)
  private String sourceType;

  @Column(name = "source_external_id", nullable = false)
  private String sourceExternalId;

  @Column(name = "document_type", nullable = false)
  private String documentType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 8)
  private InvoiceDirection direction;

  @Column(name = "issue_date", nullable = false)
  private LocalDate issueDate;

  private LocalDate saleDate;
  private LocalDate dueDate;

  @Column(nullable = false)
  private String reference;

  private String sellerLegalName;
  private String sellerTaxIdentifier;
  private String sellerCountry;
  private String buyerLegalName;
  private String buyerTaxIdentifier;
  private String buyerCountry;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal netAmount;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal vatAmount;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal grossAmount;

  @Column(name = "counterparty_id")
  private Long counterpartyId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "source_metadata", columnDefinition = "jsonb")
  private JsonNode sourceMetadata;

  private BigDecimal confidence;
  private String serviceKey;
  private String classification;
  private String vatTreatment;
  private BigDecimal vatDeductionRatio;
  private BigDecimal ryczaltRate;

  @Enumerated(EnumType.STRING)
  @Column(name = "approval_status", nullable = false)
  private ApprovalStatus approvalStatus = ApprovalStatus.NEEDS_REVIEW;

  @Column(name = "approval_source")
  private String approvalSource;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_verification_policy", nullable = false)
  private PaymentVerificationPolicy paymentVerificationPolicy = PaymentVerificationPolicy.REQUIRED;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "required_inputs", nullable = false, columnDefinition = "jsonb")
  private JsonNode requiredInputs = JSON.createArrayNode();

  @Column(nullable = false)
  private boolean duplicate;

  @Column(nullable = false)
  private boolean consumed;

  @jakarta.persistence.Version
  @Column(nullable = false)
  private long version;

  @Column(name = "period_year", nullable = false)
  private int periodYear;

  @Column(name = "period_month", nullable = false)
  private int periodMonth;

  protected RyczaltInvoiceCandidateEntity() {}

  public RyczaltInvoiceCandidateEntity(
      long profileId,
      UUID key,
      String sourceType,
      String sourceExternalId,
      String documentType,
      InvoiceDirection direction,
      LocalDate issueDate,
      LocalDate saleDate,
      LocalDate dueDate,
      String reference,
      String sellerLegalName,
      String sellerTaxIdentifier,
      String sellerCountry,
      String buyerLegalName,
      String buyerTaxIdentifier,
      String buyerCountry,
      String currency,
      BigDecimal netAmount,
      BigDecimal vatAmount,
      BigDecimal grossAmount,
      String metadata,
      BigDecimal confidence,
      int periodYear,
      int periodMonth) {
    this.profileId = profileId;
    this.candidateKey = key;
    this.sourceType = sourceType;
    this.sourceExternalId = sourceExternalId;
    this.documentType = documentType;
    this.direction = direction;
    this.issueDate = issueDate;
    this.saleDate = saleDate;
    this.dueDate = dueDate;
    this.reference = reference;
    this.sellerLegalName = sellerLegalName;
    this.sellerTaxIdentifier = sellerTaxIdentifier;
    this.sellerCountry = sellerCountry;
    this.buyerLegalName = buyerLegalName;
    this.buyerTaxIdentifier = buyerTaxIdentifier;
    this.buyerCountry = buyerCountry;
    this.currency = currency;
    this.netAmount = netAmount;
    this.vatAmount = vatAmount;
    this.grossAmount = grossAmount;
    this.confidence = confidence;
    this.periodYear = periodYear;
    this.periodMonth = periodMonth;
    try {
      this.sourceMetadata = metadata == null ? null : JSON.readTree(metadata);
    } catch (Exception e) {
      throw new IllegalArgumentException("Source metadata must be valid JSON", e);
    }
  }

  public long getProfileId() {
    return profileId;
  }

  public UUID getCandidateKey() {
    return candidateKey;
  }

  public String getSourceType() {
    return sourceType;
  }

  public String getSourceExternalId() {
    return sourceExternalId;
  }

  public String getDocumentType() {
    return documentType;
  }

  public InvoiceDirection getDirection() {
    return direction;
  }

  public LocalDate getIssueDate() {
    return issueDate;
  }

  public LocalDate getSaleDate() {
    return saleDate;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public String getReference() {
    return reference;
  }

  public String getSellerLegalName() {
    return sellerLegalName;
  }

  public String getSellerTaxIdentifier() {
    return sellerTaxIdentifier;
  }

  public String getSellerCountry() {
    return sellerCountry;
  }

  public String getBuyerLegalName() {
    return buyerLegalName;
  }

  public String getBuyerTaxIdentifier() {
    return buyerTaxIdentifier;
  }

  public String getBuyerCountry() {
    return buyerCountry;
  }

  public String getCurrency() {
    return currency;
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

  public Long getCounterpartyId() {
    return counterpartyId;
  }

  public void setCounterpartyId(Long id) {
    this.counterpartyId = id;
  }

  public ApprovalStatus getApprovalStatus() {
    return approvalStatus;
  }

  public PaymentVerificationPolicy getPaymentVerificationPolicy() {
    return paymentVerificationPolicy;
  }

  public String getApprovalSource() {
    return approvalSource;
  }

  public int getPeriodYear() {
    return periodYear;
  }

  public int getPeriodMonth() {
    return periodMonth;
  }

  public boolean isDuplicate() {
    return duplicate;
  }

  public boolean isConsumed() {
    return consumed;
  }

  public void apply(
      String classification,
      String vatTreatment,
      BigDecimal ratio,
      BigDecimal rate,
      PaymentVerificationPolicy policy,
      ApprovalStatus status,
      String source) {
    this.classification = classification;
    this.vatTreatment = vatTreatment;
    this.vatDeductionRatio = ratio;
    this.ryczaltRate = rate;
    this.paymentVerificationPolicy = policy;
    this.approvalStatus = status;
    this.approvalSource = source;
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

  public BigDecimal getRyczaltRate() {
    return ryczaltRate;
  }

  public void consume() {
    this.consumed = true;
  }

  public Long id() {
    return getId();
  }
}
