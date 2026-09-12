package com.smartbox.investory.integrations.bank;

import java.time.LocalDate;

public record BankTransactionQuery(
    String externalAccountId, LocalDate from, LocalDate to, String continuationToken) {}
