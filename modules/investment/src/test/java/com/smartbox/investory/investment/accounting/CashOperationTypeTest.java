package com.smartbox.investory.investment.accounting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CashOperationTypeTest {

  @Test
  void fromString_handlesNullValue() {
    assertEquals(CashOperationType.UNKNOWN, CashOperationType.fromString(null));
  }

  @Test
  void fromString_handlesBasicValues() {
    assertEquals(CashOperationType.DEPOSIT, CashOperationType.fromString("deposit"));
    assertEquals(CashOperationType.WITHDRAWAL, CashOperationType.fromString("withdrawal"));
    assertEquals(CashOperationType.DIVIDEND, CashOperationType.fromString("dividend"));
    assertEquals(CashOperationType.STOCK_PURCHASE, CashOperationType.fromString("stock purchase"));
    assertEquals(CashOperationType.STOCK_SELL, CashOperationType.fromString("stock sell"));
  }

  @Test
  void fromString_handlesCasInsensitivity() {
    assertEquals(CashOperationType.DEPOSIT, CashOperationType.fromString("DEPOSIT"));
    assertEquals(CashOperationType.DEPOSIT, CashOperationType.fromString("Deposit"));
  }

  @Test
  void fromString_handlesFreeFoundsInterestWithoutDate() {
    assertEquals(
        CashOperationType.FREE_FUNDS_INTEREST, CashOperationType.fromString("free-funds interest"));
    assertEquals(
        CashOperationType.FREE_FUNDS_INTEREST, CashOperationType.fromString("Free-funds Interest"));
  }

  @Test
  void fromString_handlesFreeFoundsInterestWithDate() {
    assertEquals(
        CashOperationType.FREE_FUNDS_INTEREST,
        CashOperationType.fromString("Free-funds Interest 2025-09"));
    assertEquals(
        CashOperationType.FREE_FUNDS_INTEREST,
        CashOperationType.fromString("free-funds interest 2025-09"));
    assertEquals(
        CashOperationType.FREE_FUNDS_INTEREST,
        CashOperationType.fromString("free-funds interest 2024-01"));
  }

  @Test
  void fromString_handlesFreeFoundsInterestTaxWithDate() {
    assertEquals(
        CashOperationType.FREE_FUNDS_INTEREST_TAX,
        CashOperationType.fromString("Free-funds Interest tax 2025-09"));
    assertEquals(
        CashOperationType.FREE_FUNDS_INTEREST_TAX,
        CashOperationType.fromString("free-funds interest tax 2025-09"));
    assertEquals(
        CashOperationType.FREE_FUNDS_INTEREST_TAX,
        CashOperationType.fromString("free-funds interest tax 2024-12"));
    assertEquals(
        CashOperationType.FREE_FUNDS_INTEREST_TAX,
        CashOperationType.fromString("Free\u2011funds Interest Tax 2026-02"));
    assertEquals(
        CashOperationType.FREE_FUNDS_INTEREST_TAX,
        CashOperationType.fromString("Free\u2013funds Interest Tax 2026-02"));
    assertEquals(
        CashOperationType.FREE_FUNDS_INTEREST_TAX,
        CashOperationType.fromString("Free-funds\u00A0Interest Tax 2026-02"));
  }

  @Test
  void fromString_handlesFreeFundsInterestUnicodeVariants() {
    assertEquals(
        CashOperationType.FREE_FUNDS_INTEREST,
        CashOperationType.fromString("Free\u2011funds Interest 2026-02"));
    assertEquals(
        CashOperationType.FREE_FUNDS_INTEREST,
        CashOperationType.fromString("Free\u2013funds Interest 2026-02"));
    assertEquals(
        CashOperationType.FREE_FUNDS_INTEREST,
        CashOperationType.fromString("Free-funds\u00A0Interest 2026-02"));
  }

  @Test
  void fromString_handlesWithholdingTax() {
    assertEquals(
        CashOperationType.WITHHOLDING_TAX, CashOperationType.fromString("withholding tax"));
    assertEquals(
        CashOperationType.WITHHOLDING_TAX, CashOperationType.fromString("WITHHOLDING TAX"));
  }

  @Test
  void fromString_handlesUnknownValues() {
    assertEquals(CashOperationType.UNKNOWN, CashOperationType.fromString("unknown operation"));
    assertEquals(CashOperationType.UNKNOWN, CashOperationType.fromString(""));
  }

  @Test
  void fromString_handlesStockSellWithBackwardCompatibility() {
    assertEquals(CashOperationType.STOCK_SELL, CashOperationType.fromString("stock sell"));
    assertEquals(
        CashOperationType.STOCK_SELL, CashOperationType.fromString("stock sale")); // backward
    // compat
    assertEquals(CashOperationType.STOCK_SELL, CashOperationType.fromString("STOCK SELL"));
    assertEquals(CashOperationType.STOCK_SELL, CashOperationType.fromString("Stock Sale"));
  }
}
