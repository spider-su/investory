package com.example.demo.services.dashboard;

import com.example.demo.infrastructure.CashOperationType;
import com.example.demo.infrastructure.CurrencyType;
import java.time.ZonedDateTime;

public record AssetDividendView(
    Long id,
    Long accountId,
    CashOperationType type,
    ZonedDateTime date,
    Double amount,
    CurrencyType currency,
    String comment) {}
