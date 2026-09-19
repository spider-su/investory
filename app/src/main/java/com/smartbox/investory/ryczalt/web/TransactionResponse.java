package com.smartbox.investory.ryczalt.web;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.time.LocalDate;

public record TransactionResponse(
    long id,
    LocalDate bookingDate,
    String amount,
    CurrencyType currency,
    String reference,
    String counterparty,
    String description,
    String matchedAmount) {}
