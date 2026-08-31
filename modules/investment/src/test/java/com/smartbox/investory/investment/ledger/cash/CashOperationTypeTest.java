package com.smartbox.investory.investment.ledger.cash;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Cash Operation Type")
class CashOperationTypeTest {

  @DisplayName("from String handles Null Value")
  @Test
  void fromString_handlesNullValue() {
    assertEquals(CashOperationType.UNKNOWN, CashOperationType.fromString(null));
  }

  @DisplayName("from String handles Basic Values")
  @Test
  void fromString_handlesBasicValues() {
    assertEquals(CashOperationType.DEPOSIT, CashOperationType.fromString("deposit"));
    assertEquals(CashOperationType.WITHDRAWAL, CashOperationType.fromString("withdrawal"));
    assertEquals(CashOperationType.DIVIDEND, CashOperationType.fromString("dividend"));
    assertEquals(CashOperationType.STOCK_PURCHASE, CashOperationType.fromString("stock purchase"));
    assertEquals(CashOperationType.STOCK_SELL, CashOperationType.fromString("stock sell"));
  }

  @DisplayName("from String handles Cas Insensitivity")
  @Test
  void fromString_handlesCasInsensitivity() {
    assertEquals(CashOperationType.DEPOSIT, CashOperationType.fromString("DEPOSIT"));
    assertEquals(CashOperationType.DEPOSIT, CashOperationType.fromString("Deposit"));
  }

  @DisplayName("from String handles Free Founds Interest Without Date")
  @Test
  void fromString_handlesFreeFoundsInterestWithoutDate() {
    assertEquals(
        CashOperationType.FREE_FUNDS_INTEREST, CashOperationType.fromString("free-funds interest"));
    assertEquals(
        CashOperationType.FREE_FUNDS_INTEREST, CashOperationType.fromString("Free-funds Interest"));
  }

  @DisplayName("from String handles Free Founds Interest With Date")
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

  @DisplayName("from String handles Free Founds Interest Tax With Date")
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

  @DisplayName("from String handles Free Funds Interest Unicode Variants")
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

  @DisplayName("from String handles Withholding Tax")
  @Test
  void fromString_handlesWithholdingTax() {
    assertEquals(
        CashOperationType.WITHHOLDING_TAX, CashOperationType.fromString("withholding tax"));
    assertEquals(
        CashOperationType.WITHHOLDING_TAX, CashOperationType.fromString("WITHHOLDING TAX"));
  }

  @DisplayName("from String handles Unknown Values")
  @Test
  void fromString_handlesUnknownValues() {
    assertEquals(CashOperationType.UNKNOWN, CashOperationType.fromString("unknown operation"));
    assertEquals(CashOperationType.UNKNOWN, CashOperationType.fromString(""));
  }

  @DisplayName("from String handles Stock Sell With Backward Compatibility")
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
