package com.smartbox.investory.ryczalt.calculation.application;

import com.smartbox.investory.ryczalt.calculation.vat.VatCalculationInput;
import com.smartbox.investory.ryczalt.calculation.zus.ZusCalculationInput;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/** Normalized inputs required to calculate one native Ryczalt month. */
public record NativeMonthCalculationInput(
    Map<BigDecimal, BigDecimal> revenueByRate,
    VatCalculationInput vat,
    ZusCalculationInput zus,
    BigDecimal deductionsAlreadyConsumed) {
  public NativeMonthCalculationInput {
    revenueByRate = Map.copyOf(Objects.requireNonNull(revenueByRate, "revenueByRate"));
    vat = Objects.requireNonNull(vat, "vat");
    zus = Objects.requireNonNull(zus, "zus");
    deductionsAlreadyConsumed =
        Objects.requireNonNull(deductionsAlreadyConsumed, "deductionsAlreadyConsumed");
    if (deductionsAlreadyConsumed.signum() < 0) {
      throw new IllegalArgumentException("deductionsAlreadyConsumed must not be negative");
    }
  }
}
