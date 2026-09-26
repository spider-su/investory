package com.smartbox.investory.ryczalt.calculation.vat;

import com.smartbox.investory.ryczalt.calculation.RoundingPolicy;
import java.math.BigDecimal;

/** Pure VAT settlement over normalized VAT facts. */
public final class VatCalculator {
  private final String ruleVersion;

  public VatCalculator() {
    this(VatRules2026.VERSION);
  }

  public VatCalculator(String ruleVersion) {
    this.ruleVersion = java.util.Objects.requireNonNull(ruleVersion, "ruleVersion");
  }

  public VatCalculationResult calculate(VatCalculationInput input) {
    BigDecimal output = input.outputVatBeforeCorrections().add(input.salesCorrections());
    BigDecimal availableInputVat =
        RoundingPolicy.roundVatSettlementAmount(input.deductibleInputVat())
            .add(RoundingPolicy.roundVatSettlementAmount(input.carryForwardInputVat()));
    BigDecimal grossPayable =
        RoundingPolicy.roundVatSettlementAmount(output)
            .add(RoundingPolicy.roundVatSettlementAmount(input.explicitAdjustments()));
    BigDecimal payable = grossPayable.subtract(availableInputVat).max(BigDecimal.ZERO);
    BigDecimal excess = availableInputVat.subtract(grossPayable).max(BigDecimal.ZERO);
    return new VatCalculationResult(
        input.outputVatBeforeCorrections(),
        input.salesCorrections(),
        output,
        input.deductibleInputVat(),
        input.explicitAdjustments(),
        payable,
        input.carryForwardInputVat(),
        excess,
        ruleVersion);
  }
}
