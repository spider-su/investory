package com.smartbox.investory.ryczalt.web;

import com.smartbox.investory.ryczalt.domain.CounterpartyRule;
import java.math.BigDecimal;

public record CounterpartyRuleResponse(
    long id,
    String name,
    String sourceType,
    String documentType,
    String serviceKey,
    String classification,
    String vatTreatment,
    BigDecimal vatDeductionRatio,
    BigDecimal ryczaltRate,
    boolean autoApprove,
    String paymentVerificationPolicy) {
  static CounterpartyRuleResponse of(CounterpartyRule r) {
    return new CounterpartyRuleResponse(
        r.id(),
        r.name(),
        r.sourceType(),
        r.documentType(),
        r.serviceKey(),
        r.classification(),
        r.vatTreatment(),
        r.vatDeductionRatio(),
        r.ryczaltRate(),
        r.autoApprove(),
        r.paymentVerificationPolicy().name());
  }
}
