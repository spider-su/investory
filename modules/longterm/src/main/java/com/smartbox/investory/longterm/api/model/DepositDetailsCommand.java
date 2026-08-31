package com.smartbox.investory.longterm.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Public Long-Term API model. */
public record DepositDetailsCommand(
    LocalDate maturityDate,
    BigDecimal annualInterestRate,
    BigDecimal taxRate,
    InterestTreatment interestTreatment) {}
