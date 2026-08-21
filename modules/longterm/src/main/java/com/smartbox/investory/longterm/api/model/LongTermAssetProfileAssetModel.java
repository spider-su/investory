package com.smartbox.investory.longterm.api.model;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;

/** Current Long-Term asset facts required for profile allocation; {@code currentValue} is USD. */
public record LongTermAssetProfileAssetModel(
    LongTermAssetType type, CurrencyType currency, BigDecimal currentValue) {}
