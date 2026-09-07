package com.smartbox.investory.longterm.api.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;

/**
 * Long-Term facts required for retirement-profile composition, with all money in the portfolio
 * local currency (from {@code portfolios.local_currency}); {@code currency} reports that local
 * currency.
 */
public record LongTermAssetProfileSummaryModel(
    CurrencyType currency, BigDecimal totalCurrentValue, BigDecimal netAnnualIncomeAfterTax) {}
