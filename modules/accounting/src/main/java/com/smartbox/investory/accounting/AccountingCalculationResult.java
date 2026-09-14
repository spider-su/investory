package com.smartbox.investory.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Authoritative calculated output. Historical expectations and bank state do not belong here. */
public record AccountingCalculationResult(
    LocalDate period,
    RevenueCalculation revenue,
    FxCalculation fx,
    RyczaltCalculation ryczalt,
    VatCalculation vat,
    ZusCalculation zus,
    List<CalculatedObligation> calculatedObligations,
    List<AccountingIssue> issues) {
  public boolean complete() {
    return issues.isEmpty();
  }

  public record RevenueCalculation(BigDecimal domesticPln, BigDecimal convertedForeignPln) {
    public BigDecimal totalPln() {
      return domesticPln.add(convertedForeignPln);
    }
  }

  public record FxCalculation(
      List<Conversion> entries,
      BigDecimal convertedRevenuePln,
      List<String> unavailableReferences) {
    public boolean complete() {
      return unavailableReferences.isEmpty();
    }

    public record Conversion(
        String reference, String currency, BigDecimal sourceAmount, BigDecimal convertedPln) {}
  }

  public record RyczaltCalculation(
      BigDecimal revenueBeforeDeductions,
      BigDecimal socialContributionDeduction,
      BigDecimal healthContributionPaid,
      BigDecimal healthDeduction,
      BigDecimal availableDeduction,
      BigDecimal deductionUsed,
      BigDecimal deductionCarryForward,
      BigDecimal taxableBase,
      Map<BigDecimal, BigDecimal> revenueByRate,
      Map<BigDecimal, BigDecimal> taxableByRate,
      BigDecimal calculatedTax) {}

  public record VatCalculation(
      BigDecimal outputVatBeforeCorrection,
      BigDecimal salesCorrectionVat,
      BigDecimal outputVat,
      BigDecimal deductibleInputVat,
      BigDecimal calculatedVat) {}

  public record ZusCalculation(
      BigDecimal socialZus,
      BigDecimal healthZus,
      BigDecimal totalZus,
      boolean hasUop,
      String socialZusReasonCode) {}

  public record CalculatedObligation(String type, BigDecimal amount, LocalDate period) {}
}
