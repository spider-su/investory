package com.smartbox.investory.ryczalt.application.query;

import com.smartbox.investory.ryczalt.persistence.InvoiceDirection;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RyczaltInvoiceReadModel(
    long id,
    InvoiceDirection direction,
    String reference,
    LocalDate issueDate,
    LocalDate accountingDate,
    BigDecimal netAmount,
    BigDecimal vatAmount,
    BigDecimal grossAmount,
    CurrencyType currency,
    BigDecimal bookedNetPln,
    BigDecimal ryczaltRate,
    BigDecimal deductibleVat) {}
