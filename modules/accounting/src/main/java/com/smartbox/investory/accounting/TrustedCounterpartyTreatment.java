package com.smartbox.investory.accounting;

import java.math.BigDecimal;

/** Accounting treatment learned only from an explicitly user-confirmed document. */
public record TrustedCounterpartyTreatment(
    long id,
    long profileId,
    long counterpartyId,
    long sourceDocumentId,
    String direction,
    String documentType,
    String category,
    String vatTreatment,
    BigDecimal vatDeductionRatio,
    BigDecimal vatRate,
    String jpkEvidence,
    java.time.Instant confirmedAt) {
  public boolean matches(
      String candidateDirection,
      String candidateType,
      String candidateCategory,
      String candidateVatTreatment,
      BigDecimal candidateVatDeductionRatio,
      BigDecimal candidateVatRate,
      String candidateJpkEvidence) {
    return same(direction, candidateDirection)
        && same(documentType, candidateType)
        && same(category, candidateCategory)
        && same(vatTreatment, candidateVatTreatment)
        && same(vatDeductionRatio, candidateVatDeductionRatio)
        && same(vatRate, candidateVatRate)
        && same(jpkEvidence, candidateJpkEvidence);
  }

  private static boolean same(Object one, Object two) {
    if (one instanceof BigDecimal a && two instanceof BigDecimal b) return a.compareTo(b) == 0;
    return one == null ? two == null : one.equals(two);
  }
}
