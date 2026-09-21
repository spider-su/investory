package com.smartbox.investory.ryczalt.checker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.ryczalt.domain.Obligation;
import com.smartbox.investory.ryczalt.domain.ObligationType;
import com.smartbox.investory.ryczalt.domain.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentCheckerTest {
  private static final Currency PLN = Currency.getInstance("PLN");
  private final PaymentChecker checker = new PaymentChecker();

  @Test
  void recognizesExactPaymentAndLatePayment() {
    Obligation obligation =
        new Obligation(
            ObligationType.VAT, new BigDecimal("100.00"), PLN, LocalDate.of(2026, 2, 25), null);
    PaymentCheckResult result =
        checker.check(obligation, List.of(tx("vat", "100.00", "2026-02-25", "VAT")));
    assertEquals(PaymentCheckStatus.PAID, result.status());
    assertEquals(
        PaymentCheckStatus.PAID,
        checker
            .check(
                obligation,
                List.of(
                    new Transaction(
                        "vat-outflow",
                        LocalDate.of(2026, 2, 25),
                        new BigDecimal("-100.00"),
                        PLN,
                        "",
                        "VAT")))
            .status());
    assertEquals(
        new BigDecimal("100.00"),
        checker
            .check(
                obligation,
                List.of(
                    new Transaction(
                        "vat-outflow",
                        LocalDate.of(2026, 2, 25),
                        new BigDecimal("-100.00"),
                        PLN,
                        "",
                        "VAT")))
            .matchedAmount());
    assertEquals(
        PaymentCheckStatus.PAID_LATE,
        checker.check(obligation, List.of(tx("vat-late", "100.00", "2026-02-26", "VAT"))).status());
  }

  @Test
  void supportsPartialMultipleAndOverpayment() {
    Obligation obligation =
        new Obligation(ObligationType.ZUS, new BigDecimal("1000.00"), PLN, null, null);
    PaymentCheckResult partial =
        checker.check(obligation, List.of(tx("one", "600.00", "2026-02-10", "ZUS")));
    assertEquals(PaymentCheckStatus.PARTIALLY_PAID, partial.status());
    assertEquals(new BigDecimal("600.00"), partial.matchedAmount());
    assertEquals(
        PaymentCheckStatus.PAID,
        checker
            .check(
                obligation,
                List.of(
                    tx("one", "600.00", "2026-02-10", "ZUS"),
                    tx("two", "400.00", "2026-02-11", "ZUS")))
            .status());
    assertEquals(
        PaymentCheckStatus.OVERPAID,
        checker.check(obligation, List.of(tx("one", "1200.00", "2026-02-10", "ZUS"))).status());
  }

  @Test
  void refusesAmbiguousUnrelatedAndWrongCurrencyCandidates() {
    Obligation obligation =
        new Obligation(ObligationType.RYCZALT, new BigDecimal("100.00"), PLN, null, null);
    assertEquals(
        PaymentCheckStatus.AMBIGUOUS,
        checker
            .check(
                obligation,
                List.of(
                    tx("one", "100.00", "2026-02-01", ""), tx("two", "100.00", "2026-02-02", "")))
            .status());
    assertEquals(
        PaymentCheckStatus.NOT_FOUND,
        checker.check(obligation, List.of(tx("other", "50.00", "2026-02-01", "OTHER"))).status());
    assertEquals(
        PaymentIssue.Code.WRONG_CURRENCY,
        checker
            .check(
                obligation,
                List.of(
                    new Transaction(
                        "eur",
                        LocalDate.of(2026, 2, 1),
                        new BigDecimal("100.00"),
                        Currency.getInstance("EUR"),
                        "",
                        "")))
            .issues()
            .getFirst()
            .code());
  }

  private Transaction tx(String reference, String amount, String date, String description) {
    return new Transaction(
        reference, LocalDate.parse(date), new BigDecimal(amount), PLN, "", description);
  }
}
