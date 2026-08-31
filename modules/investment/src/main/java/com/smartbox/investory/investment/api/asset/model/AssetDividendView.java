package com.smartbox.investory.investment.api.asset.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.time.ZonedDateTime;

public record AssetDividendView(
    Long id,
    Long accountId,
    AssetCashFlowType type,
    ZonedDateTime date,
    Double amount,
    CurrencyType currency,
    String comment) {}
