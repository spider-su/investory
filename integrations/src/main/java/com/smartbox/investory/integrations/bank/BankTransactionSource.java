package com.smartbox.investory.integrations.bank;

public interface BankTransactionSource {
  BankTransactionPage transactions(BankTransactionQuery query);
}
