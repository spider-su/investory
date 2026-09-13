package com.smartbox.investory.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A payment instruction projected from a canonical monthly calculation. */
public record AccountingPaymentInstruction(
    String obligationType,
    BigDecimal amount,
    LocalDate dueDate,
    String recipient,
    String account,
    String title,
    LocalDate period,
    String status,
    BigDecimal matchedBankPayment) {
  public String transferSymbol() {
    return "RYCZALT".equals(obligationType) ? "PPE" : obligationType;
  }
}
