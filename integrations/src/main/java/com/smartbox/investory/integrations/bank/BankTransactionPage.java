package com.smartbox.investory.integrations.bank;

import java.util.List;

public record BankTransactionPage(
    List<ExternalBankTransaction> transactions, String nextContinuationToken) {
  public BankTransactionPage {
    transactions = List.copyOf(transactions);
  }
}
