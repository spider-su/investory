package com.smartbox.investory.ryczalt.checker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbox.investory.ryczalt.domain.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class TaxPaymentPeriodReferenceTest {
  private static final Currency PLN = Currency.getInstance("PLN");

  @Test
  void excludesTaxOfficeTransferMarkedForAnotherPeriod() {
    assertFalse(
        TaxPaymentPeriodReference.matches(
            YearMonth.of(2025, 1),
            transaction("URZĄD SKARBOWY", "/TI/NIP/OKR/24M12/SFP/PPE [Podatki]")));
  }

  @Test
  void includesTaxOfficeTransferMarkedForCurrentPeriod() {
    assertTrue(
        TaxPaymentPeriodReference.matches(
            YearMonth.of(2025, 1),
            transaction("URZĄD SKARBOWY", "/TI/NIP/OKR/25M01/SFP/PPE [Podatki]")));
  }

  @Test
  void excludesZusTransferMarkedForAnotherPeriod() {
    assertFalse(
        TaxPaymentPeriodReference.matches(
            YearMonth.of(2025, 1), transaction("ZUS", "24M12 [Podatki]")));
  }

  @Test
  void leavesTransactionsWithoutExplicitTaxPeriodEligible() {
    assertTrue(
        TaxPaymentPeriodReference.matches(
            YearMonth.of(2025, 1), transaction("URZĄD SKARBOWY", "Podatki")));
  }

  private Transaction transaction(String counterparty, String description) {
    return new Transaction(
        "1",
        "",
        LocalDate.of(2025, 1, 16),
        new BigDecimal("100.00"),
        PLN,
        counterparty,
        null,
        description);
  }
}
