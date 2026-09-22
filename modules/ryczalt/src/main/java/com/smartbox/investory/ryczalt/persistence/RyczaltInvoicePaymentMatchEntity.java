package com.smartbox.investory.ryczalt.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "ryczalt_invoice_payment_match", schema = "investory")
public class RyczaltInvoicePaymentMatchEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "profile_id", nullable = false)
  private long profileId;

  @Column(name = "invoice_id", nullable = false)
  private long invoiceId;

  @Column(name = "transaction_id", nullable = false)
  private long transactionId;

  @Column(name = "matched_amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal matchedAmount;

  protected RyczaltInvoicePaymentMatchEntity() {}

  public RyczaltInvoicePaymentMatchEntity(
      long profileId, long invoiceId, long transactionId, BigDecimal matchedAmount) {
    this.profileId = profileId;
    this.invoiceId = invoiceId;
    this.transactionId = transactionId;
    this.matchedAmount = matchedAmount;
  }

  public BigDecimal getMatchedAmount() {
    return matchedAmount;
  }
}
