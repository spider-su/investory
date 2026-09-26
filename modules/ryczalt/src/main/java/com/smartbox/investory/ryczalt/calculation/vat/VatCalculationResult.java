package com.smartbox.investory.ryczalt.calculation.vat;

import java.math.BigDecimal;

public record VatCalculationResult(
    BigDecimal outputVatBeforeCorrections,
    BigDecimal salesCorrections,
    BigDecimal outputVat,
    BigDecimal deductibleInputVat,
    BigDecimal explicitAdjustments,
    BigDecimal calculatedVat,
    BigDecimal carryForwardInputVat,
    BigDecimal excessVatCarryForward,
    String ruleVersion) {}
