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

class HappyInvestorSettlementTest {
  @Test
  void recognizesTheKnownJuly2026ZusPaymentAsPartial() {
    Obligation obligation =
        new Obligation(
            ObligationType.ZUS,
            new BigDecimal("1495.04"),
            Currency.getInstance("PLN"),
            LocalDate.of(2026, 8, 20),
            null);
    Transaction payment =
        new Transaction(
            "HI-ACC-2026-07-ZUS",
            LocalDate.of(2026, 8, 18),
            new BigDecimal("-1495.00"),
            Currency.getInstance("PLN"),
            "ZUS",
            "ZUS_PAYMENT");

    PaymentCheckResult result = new PaymentChecker().check(obligation, List.of(payment));

    assertEquals(PaymentCheckStatus.PARTIALLY_PAID, result.status());
    assertEquals(new BigDecimal("1495.00"), result.matchedAmount());
  }
}
