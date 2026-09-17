package com.smartbox.investory.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountingFact(
    long id,
    LocalDate factDate,
    String factType,
    String reference,
    String counterpartyAlias,
    String currency,
    BigDecimal amount,
    BigDecimal taxRate,
    String note) {}
