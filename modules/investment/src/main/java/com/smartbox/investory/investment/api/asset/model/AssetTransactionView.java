package com.smartbox.investory.investment.api.asset.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.time.ZonedDateTime;

public record AssetTransactionView(
    Long id,
    Long accountId,
    AssetTransactionType type,
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
