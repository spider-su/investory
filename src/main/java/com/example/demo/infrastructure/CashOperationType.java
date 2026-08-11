package com.example.demo.infrastructure;

import java.util.Locale;

public enum CashOperationType {
  SEC_FEE,
  SUBACCOUNT_TRANSFER,
  STOCK_PURCHASE,
  STOCK_SELL,
  CLOSE_TRADE,
  DIVIDEND,
  FREE_FUNDS_INTEREST,
  FREE_FUNDS_INTEREST_TAX,
  COMMISSION,
  TRANSFER,
  WITHDRAWAL,
  DEPOSIT,
  WITHHOLDING_TAX,
  SWAP,
  ROLLOVER,
  CORRECTION,
  STAMP_DUTY,
  TRANSACTION_TAX,
  UNKNOWN;

  public static CashOperationType fromString(String value) {
    if (value == null) {
      return UNKNOWN;
    }
    String normalized = normalizeOperationLabel(value);

    // Special handling for date-suffixed values
    if (normalized.startsWith("free-funds interest tax")) {
      return FREE_FUNDS_INTEREST_TAX;
    }
    if (normalized.startsWith("free-funds interest")) {
      return FREE_FUNDS_INTEREST;
    }

    return switch (normalized) {
      case "sec fee" -> SEC_FEE;
      case "subaccount transfer" -> SUBACCOUNT_TRANSFER;
      case "stock purchase" -> STOCK_PURCHASE;
      case "stock sell", "stock sale" -> STOCK_SELL;
      case "close trade" -> CLOSE_TRADE;
      case "dividend" -> DIVIDEND;
      case "commission" -> COMMISSION;
      case "transfer" -> TRANSFER;
      case "withdrawal", "withdraw" -> WITHDRAWAL;
      case "deposit", "ike deposit" -> DEPOSIT;
      case "withholding tax" -> WITHHOLDING_TAX;
      case "swap" -> SWAP;
      case "rollover" -> ROLLOVER;
      case "correction" -> CORRECTION;
      case "stamp duty" -> STAMP_DUTY;
      case "tax iftt" -> TRANSACTION_TAX;
      default -> UNKNOWN;
    };
  }

  private static String normalizeOperationLabel(String value) {
    return value
        .toLowerCase(Locale.ROOT)
        .replace('\u2010', '-') // hyphen
        .replace('\u2011', '-') // non-breaking hyphen
        .replace('\u2012', '-') // figure dash
        .replace('\u2013', '-') // en dash
        .replace('\u2014', '-') // em dash
        .replace('\u2212', '-') // minus sign
        .replace('\u00a0', ' ') // non-breaking space
        .replaceAll("\\s+", " ")
        .trim();
  }
}
