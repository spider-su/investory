package com.smartbox.investory.ryczalt.domain;

import java.math.BigDecimal;

/** A reusable, explainable accounting decision for one service shape from a counterparty. */
public record CounterpartyRule(
    Long id,
    long profileId,
    long counterpartyId,
    String name,
    String sourceType,
    String documentType,
    String serviceKey,
    String classification,
    String vatTreatment,
    BigDecimal vatDeductionRatio,
    BigDecimal ryczaltRate,
    boolean autoApprove,
    PaymentVerificationPolicy paymentVerificationPolicy) {
  public CounterpartyRule {
    if (name == null || name.isBlank()) throw new IllegalArgumentException("Rule name is required");
    if (paymentVerificationPolicy == null)
      throw new IllegalArgumentException("Payment policy is required");
  }
}
