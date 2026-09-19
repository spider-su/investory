package com.smartbox.investory.ryczalt.integration.bank;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ryczalt-owned, provider-neutral bank transaction fact produced by a {@link
 * BankTransactionSourcePort}. Provider DTOs never cross this boundary.
 *
 * @param sourceExternalId stable, content-derived identity used for idempotency/provenance
 */
public record BankTransactionSourceRecord(
    String sourceExternalId,
    String source,
    LocalDate bookingDate,
    LocalDate relatedPeriod,
    BigDecimal amount,
    String currency,
    String counterparty,
    String reference,
    String description) {}
