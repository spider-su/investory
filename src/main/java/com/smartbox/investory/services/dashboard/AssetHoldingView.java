package com.smartbox.investory.services.dashboard;

import com.smartbox.investory.infrastructure.PositionSettlementModel;
import com.smartbox.investory.shared.currency.CurrencyType;

public record AssetHoldingView(
    Long accountId,
    double quantity,
    double averageCost,
    CurrencyType costCurrency,
    Double marketPrice,
    CurrencyType priceCurrency,
    Double marketValue,
    Double unrealizedProfitLoss,
    PositionSettlementModel settlementModel) {}
