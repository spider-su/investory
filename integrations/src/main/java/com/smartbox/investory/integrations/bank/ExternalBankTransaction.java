package com.smartbox.investory.integrations.bank;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExternalBankTransaction(
    BankDataProvider provider,
    String externalAccountId,
    String externalTransactionId,
    LocalDate bookingDate,
    LocalDate valueDate,
    LocalDate relatedPeriod,
    BigDecimal amount,
    String currency,
    String counterpartyName,
    String counterpartyAccount,
    String remittanceInformation,
    String rawReference,
    String sourcePayloadHash) {}
