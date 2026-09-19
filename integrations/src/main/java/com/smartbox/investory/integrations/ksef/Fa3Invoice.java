package com.smartbox.investory.integrations.ksef;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Provider-neutral facts extracted from a KSeF FA(3) invoice document. */
public record Fa3Invoice(
    String reference,
    LocalDate issueDate,
    LocalDate saleDate,
    String sellerNip,
    String sellerName,
    String buyerNip,
    String buyerName,
    String currency,
    BigDecimal netAmount,
    BigDecimal vatAmount,
    BigDecimal grossAmount,
    String invoiceType,
    BigDecimal vatRate) {}
