package com.smartbox.investory.integrations.bank;

public record ExternalBankAccount(
    BankDataProvider provider,
    String externalAccountId,
    String maskedAccountIdentifier,
    String currency,
    String displayName) {}
