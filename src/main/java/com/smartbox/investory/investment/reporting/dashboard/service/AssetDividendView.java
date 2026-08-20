package com.smartbox.investory.investment.reporting.dashboard.service;

import com.smartbox.investory.infrastructure.CashOperationType;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.time.ZonedDateTime;

public record AssetDividendView(
    Long id,
    Long accountId,
    CashOperationType type,
    ZonedDateTime date,
    Double amount,
    CurrencyType currency,
    String comment) {}
