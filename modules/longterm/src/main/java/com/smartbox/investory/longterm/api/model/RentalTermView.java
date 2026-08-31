package com.smartbox.investory.longterm.api.model;

import java.math.BigDecimal;

/** Public Long-Term API model. */
public record RentalTermView(
    CashFlowType type, BigDecimal amount, Frequency frequency, boolean paidByTenant) {}
