package com.smartbox.investory.longterm.api.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;

/** Public Long-Term API model. */
public record CashReserveCommand(
    Long portfolioId,
    Long id,
    String name,
    CurrencyType currency,
    BigDecimal value,
    BigDecimal annualReturnRate,
    String notes) {}
