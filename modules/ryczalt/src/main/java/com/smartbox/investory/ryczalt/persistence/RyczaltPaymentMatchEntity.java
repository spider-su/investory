package com.smartbox.investory.ryczalt.persistence;

import com.smartbox.investory.ryczalt.checker.PaymentMatchType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ryczalt_payment_match", schema = "investory")
public class RyczaltPaymentMatchEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "profile_id", nullable = false)
  private long profileId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "obligation_id", nullable = false)
  private RyczaltObligationEntity obligation;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "transaction_id", nullable = false)
  private RyczaltTransactionEntity transaction;

  @Column(name = "matched_amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal matchedAmount;

  @Enumerated(EnumType.STRING)
  @Column(name = "match_type", nullable = false, length = 16)
  private PaymentMatchType matchType;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected RyczaltPaymentMatchEntity() {}

  public RyczaltPaymentMatchEntity(
      long profileId,
      RyczaltObligationEntity obligation,
      RyczaltTransactionEntity transaction,
      BigDecimal matchedAmount,
      PaymentMatchType matchType,
      Instant createdAt) {
    this.profileId = profileId;
    this.obligation = obligation;
    this.transaction = transaction;
    this.matchedAmount = matchedAmount;
    this.matchType = matchType;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public long getProfileId() {
    return profileId;
  }

  public RyczaltObligationEntity getObligation() {
    return obligation;
  }

  public RyczaltTransactionEntity getTransaction() {
    return transaction;
  }

  public BigDecimal getMatchedAmount() {
    return matchedAmount;
  }

  public PaymentMatchType getMatchType() {
    return matchType;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
