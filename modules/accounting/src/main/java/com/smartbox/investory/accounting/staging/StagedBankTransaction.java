package com.smartbox.investory.accounting.staging;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StagedBankTransaction(
    long id,
    long profileId,
    LocalDate taxPeriod,
    long sourceId,
    String sourceReference,
    String provider,
    String externalAccountId,
    String externalTransactionId,
    LocalDate bookingDate,
    LocalDate valueDate,
    BigDecimal amount,
    String currency,
    String counterpartyName,
    String counterpartyAccount,
    String remittanceInformation,
    String sourcePayloadHash,
    StagingReconciliationStatus status,
    java.util.List<String> reasonCodes,
    String message,
    java.time.Instant promotedAt,
    Long canonicalId) {}
