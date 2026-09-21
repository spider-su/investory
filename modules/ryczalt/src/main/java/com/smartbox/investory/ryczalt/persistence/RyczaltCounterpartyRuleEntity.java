package com.smartbox.investory.ryczalt.persistence;

import com.smartbox.investory.ryczalt.domain.PaymentVerificationPolicy;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "ryczalt_counterparty_rule", schema = "investory")
public class RyczaltCounterpartyRuleEntity extends RyczaltEntity {
  @Column(name = "profile_id", nullable = false)
  private long profileId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "counterparty_id", nullable = false)
  private RyczaltCounterpartyEntity counterparty;

  @Column(nullable = false, length = 128)
  private String name;

  @Column(name = "source_type", length = 32)
  private String sourceType;

  @Column(name = "document_type", length = 64)
  private String documentType;

  @Column(name = "service_key", length = 256)
  private String serviceKey;

  @Column(length = 64)
  private String classification;

  @Column(name = "vat_treatment", length = 64)
  private String vatTreatment;

  @Column(name = "vat_deduction_ratio", precision = 7, scale = 4)
  private BigDecimal vatDeductionRatio;

  @Column(name = "ryczalt_rate", precision = 7, scale = 4)
  private BigDecimal ryczaltRate;

  @Column(name = "auto_approve", nullable = false)
  private boolean autoApprove;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_verification_policy", nullable = false, length = 16)
  private PaymentVerificationPolicy paymentVerificationPolicy;

  protected RyczaltCounterpartyRuleEntity() {}

  public RyczaltCounterpartyRuleEntity(
      long profileId,
      RyczaltCounterpartyEntity counterparty,
      String name,
      String sourceType,
      String documentType,
      String serviceKey,
      String classification,
      String vatTreatment,
      BigDecimal vatDeductionRatio,
      BigDecimal ryczaltRate,
      boolean autoApprove,
      PaymentVerificationPolicy policy) {
    this.profileId = profileId;
    this.counterparty = counterparty;
    this.name = name;
    this.sourceType = sourceType;
    this.documentType = documentType;
    this.serviceKey = serviceKey;
    this.classification = classification;
    this.vatTreatment = vatTreatment;
    this.vatDeductionRatio = vatDeductionRatio;
    this.ryczaltRate = ryczaltRate;
    this.autoApprove = autoApprove;
    this.paymentVerificationPolicy = policy;
  }

  public Long id() {
    return getId();
  }

  public long getProfileId() {
    return profileId;
  }

  public RyczaltCounterpartyEntity getCounterparty() {
    return counterparty;
  }

  public String getName() {
    return name;
  }

  public String getSourceType() {
    return sourceType;
  }

  public String getDocumentType() {
    return documentType;
  }

  public String getServiceKey() {
    return serviceKey;
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

  public boolean isAutoApprove() {
    return autoApprove;
  }

  public PaymentVerificationPolicy getPaymentVerificationPolicy() {
    return paymentVerificationPolicy;
  }

  public void update(
      String name,
      String sourceType,
      String documentType,
      String serviceKey,
      String classification,
      String vatTreatment,
      BigDecimal vatDeductionRatio,
      BigDecimal ryczaltRate,
      boolean autoApprove,
      PaymentVerificationPolicy policy) {
    this.name = name;
    this.sourceType = sourceType;
    this.documentType = documentType;
    this.serviceKey = serviceKey;
    this.classification = classification;
    this.vatTreatment = vatTreatment;
    this.vatDeductionRatio = vatDeductionRatio;
    this.ryczaltRate = ryczaltRate;
    this.autoApprove = autoApprove;
    this.paymentVerificationPolicy = policy == null ? PaymentVerificationPolicy.REQUIRED : policy;
  }
}
