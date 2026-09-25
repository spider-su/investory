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
  void recognizesTheSyntheticJuly2026ZusPaymentAsPartial() {
    Obligation obligation =
        new Obligation(
            ObligationType.ZUS,
            new BigDecimal("3283.33"),
            Currency.getInstance("PLN"),
            LocalDate.of(2026, 8, 20),
            null);
    Transaction payment =
        new Transaction(
            "HI-RYC-2026-07-ZUS",
            LocalDate.of(2026, 8, 18),
            new BigDecimal("-3283.29"),
            Currency.getInstance("PLN"),
            "ZUS",
            "ZUS_PAYMENT");

    PaymentCheckResult result = new PaymentChecker().check(obligation, List.of(payment));

    assertEquals(PaymentCheckStatus.PARTIALLY_PAID, result.status());
    assertEquals(new BigDecimal("3283.29"), result.matchedAmount());
  }
}
