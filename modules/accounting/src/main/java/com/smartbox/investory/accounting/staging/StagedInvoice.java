package com.smartbox.investory.accounting.staging;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StagedInvoice(
    long id,
    long profileId,
    LocalDate taxPeriod,
    long sourceId,
    String sourceReference,
    String documentKind,
    LocalDate documentDate,
    LocalDate dueDate,
    String reference,
    String counterpartyName,
    String counterpartyTaxIdentifier,
    String counterpartyCountry,
    String currency,
    BigDecimal netAmount,
    BigDecimal vatAmount,
    BigDecimal grossAmount,
    BigDecimal vatDeductionRatio,
    BigDecimal deductibleVat,
    String vatTreatment,
    String ksefNumber,
    StagingReconciliationStatus status,
    java.util.List<String> reasonCodes,
    String message,
    java.time.Instant promotedAt,
    Long canonicalId) {}
