package com.smartbox.investory.ryczalt.calculation.vat;

import com.smartbox.investory.ryczalt.calculation.RoundingPolicy;
import java.math.BigDecimal;

/** Pure VAT settlement over normalized VAT facts. */
public final class VatCalculator {
  public VatCalculationResult calculate(VatCalculationInput input) {
    BigDecimal output = input.outputVatBeforeCorrections().add(input.salesCorrections());
    BigDecimal payable =
        RoundingPolicy.roundVatSettlementAmount(output)
            .add(RoundingPolicy.roundVatSettlementAmount(input.explicitAdjustments()))
            .subtract(RoundingPolicy.roundVatSettlementAmount(input.deductibleInputVat()))
            .max(BigDecimal.ZERO);
    return new VatCalculationResult(
        input.outputVatBeforeCorrections(),
        input.salesCorrections(),
        output,
        input.deductibleInputVat(),
        input.explicitAdjustments(),
        payable,
        VatRules2026.VERSION);
  }
}
