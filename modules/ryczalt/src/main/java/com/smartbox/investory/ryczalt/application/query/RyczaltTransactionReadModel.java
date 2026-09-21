package com.smartbox.investory.ryczalt.application.query;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RyczaltTransactionReadModel(
    long id,
    LocalDate bookingDate,
    BigDecimal amount,
    CurrencyType currency,
    String reference,
    String counterparty,
    String description,
    BigDecimal matchedAmount) {}
