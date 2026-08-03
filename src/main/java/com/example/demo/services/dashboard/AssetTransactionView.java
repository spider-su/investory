package com.example.demo.services.dashboard;

import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.PositionType;
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
