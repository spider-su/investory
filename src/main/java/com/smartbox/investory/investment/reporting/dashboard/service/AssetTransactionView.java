package com.smartbox.investory.investment.reporting.dashboard.service;

import com.smartbox.investory.infrastructure.PositionType;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.time.ZonedDateTime;

public record AssetTransactionView(
    Long id,
    Long accountId,
    PositionType type,
    double quantity,
    ZonedDateTime openTime,
    ZonedDateTime closeTime,
    Double openPrice,
    Double closePrice,
    Double commission,
    CurrencyType commissionCurrency,
    Double realizedProfitLoss,
    CurrencyType profitCurrency,
    String sourcePositionId,
    String brokerSymbol) {}
