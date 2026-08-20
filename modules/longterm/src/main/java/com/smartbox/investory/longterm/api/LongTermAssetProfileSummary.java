package com.smartbox.investory.longterm.api;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;

/** Long-Term facts required for retirement-profile composition, with all money in canonical USD. */
public record LongTermAssetProfileSummary(
    CurrencyType currency, BigDecimal totalCurrentValue, BigDecimal netAnnualIncomeAfterTax) {}
