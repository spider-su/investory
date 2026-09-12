package com.smartbox.investory.accounting;

import java.math.BigDecimal;

/** Calculation-side ZUS inputs; payment observations are intentionally not represented here. */
public record ZusCalculationInput(
    BigDecimal socialAmount,
    BigDecimal healthAmount,
    BigDecimal fpFsAmount,
    String healthBand,
    String socialReasonCode) {}
