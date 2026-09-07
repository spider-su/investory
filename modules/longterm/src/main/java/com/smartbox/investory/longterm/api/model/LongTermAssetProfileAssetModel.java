package com.smartbox.investory.longterm.api.model;

import com.smartbox.investory.shared.assets.AssetEconomicCategory;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;

/**
 * Current Long-Term asset facts required for profile allocation; {@code currentValue} is expressed
 * in the portfolio base currency reported by {@code currency}. {@code fundingAvailable} means the
 * value can enter the current retirement cash reserve without waiting for maturity or sale.
 */
public record LongTermAssetProfileAssetModel(
    AssetEconomicCategory category,
    CurrencyType currency,
    BigDecimal currentValue,
    boolean fundingAvailable) {}
