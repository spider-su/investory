package com.example.demo.services.dashboard;

import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.PositionSettlementModel;

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
