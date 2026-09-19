package com.smartbox.investory.ryczalt.integration.ksef;

import com.smartbox.investory.ryczalt.persistence.InvoiceDirection;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ryczalt-owned, provider-neutral invoice fact produced by an {@link InvoiceSourcePort}. Raw KSeF
 * FA(3) models never cross this boundary. Accounting classifications that KSeF does not provide
 * ({@code ryczaltRate}, {@code deductibleVat}) stay {@code null} and are surfaced by completeness
 * checks rather than guessed.
 *
 * @param sourceExternalId stable external identity (KSeF number) used for idempotency/provenance
 */
public record InvoiceSourceRecord(
    String sourceExternalId,
    InvoiceDirection direction,
    String reference,
    LocalDate issueDate,
    LocalDate accountingDate,
    BigDecimal netAmount,
    BigDecimal vatAmount,
    BigDecimal grossAmount,
    String currency,
    String counterpartyName,
    String counterpartyTaxId,
    BigDecimal ryczaltRate,
    BigDecimal deductibleVat) {}
