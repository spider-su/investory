package com.smartbox.investory.ryczalt.calculation.zus;

import java.math.BigDecimal;

public record ZusCalculationResult(
    BigDecimal social,
    BigDecimal health,
    BigDecimal total,
    BigDecimal deductibleSocial,
    ZusRules2026.HealthBand healthBand,
    String reason,
    String ruleVersion) {}
