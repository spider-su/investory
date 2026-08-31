package com.smartbox.investory.investment.api.operations;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record ManualAssetPriceView(
    String symbol,
    BigDecimal marketPrice,
    BigDecimal marketPriceUsd,
    CurrencyType currency,
    String source,
    ZonedDateTime updatedAt) {}
