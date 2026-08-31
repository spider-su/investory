package com.smartbox.investory.longterm.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Public Long-Term API model. */
public record BondDetailsView(
    LocalDate maturityDate,
    BigDecimal taxRate,
    InterestTreatment interestTreatment,
    BigDecimal redemptionValue) {}
