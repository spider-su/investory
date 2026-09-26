package com.smartbox.investory.ryczalt.calculation.vat;

import java.math.BigDecimal;
import java.util.Objects;

/** Normalized VAT components. Corrections and adjustments remain explicit and signed. */
public record VatCalculationInput(
    BigDecimal outputVatBeforeCorrections,
    BigDecimal salesCorrections,
    BigDecimal deductibleInputVat,
    BigDecimal explicitAdjustments,
    BigDecimal carryForwardInputVat) {
  public VatCalculationInput {
    outputVatBeforeCorrections = required(outputVatBeforeCorrections, "outputVatBeforeCorrections");
    salesCorrections = required(salesCorrections, "salesCorrections");
    deductibleInputVat = required(deductibleInputVat, "deductibleInputVat");
    explicitAdjustments = required(explicitAdjustments, "explicitAdjustments");
    carryForwardInputVat = required(carryForwardInputVat, "carryForwardInputVat");
  }

  public VatCalculationInput(
      BigDecimal outputVatBeforeCorrections,
      BigDecimal salesCorrections,
      BigDecimal deductibleInputVat) {
    this(
        outputVatBeforeCorrections,
        salesCorrections,
        deductibleInputVat,
        BigDecimal.ZERO,
        BigDecimal.ZERO);
  }

  public VatCalculationInput(
      BigDecimal outputVatBeforeCorrections,
      BigDecimal salesCorrections,
      BigDecimal deductibleInputVat,
      BigDecimal explicitAdjustments) {
    this(
        outputVatBeforeCorrections,
        salesCorrections,
        deductibleInputVat,
        explicitAdjustments,
        BigDecimal.ZERO);
  }

  private static BigDecimal required(BigDecimal value, String name) {
    return Objects.requireNonNull(value, name);
  }
}
