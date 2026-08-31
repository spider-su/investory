package com.smartbox.investory.investment.api.asset.model;

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
    AssetSettlementModel settlementModel) {}
