package com.smartbox.investory.longterm.api;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;

/** Current Long-Term asset facts required for profile allocation; {@code currentValue} is USD. */
public record LongTermAssetProfileAsset(
    LongTermAssetType type, CurrencyType currency, BigDecimal currentValue) {}
