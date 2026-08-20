package com.smartbox.investory.investment.reporting.dashboard.service;

import com.smartbox.investory.investment.accounting.model.PositionSettlementModel;
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
