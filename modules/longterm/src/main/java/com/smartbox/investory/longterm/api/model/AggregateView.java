package com.smartbox.investory.longterm.api.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;

/** Public Long-Term API model. */
public record AggregateView(
    CurrencyType currency, BigDecimal totalCurrentValue, AnnualEconomicsView annualEconomics) {}
