package com.smartbox.investory.integrations.zus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.integrations.zus.persistence.BankTransactionZusPaymentSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockZusClientTest {
  @Test
  void mapsBankTransactionsToPositiveStableSettledPayments() {
    BankTransactionZusPaymentSource source = mock(BankTransactionZusPaymentSource.class);
    when(source.findOutgoingZusTransactions(1L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1)))
        .thenReturn(
            List.of(
                transaction(12L, LocalDate.of(2026, 7, 10), "ZUS", new BigDecimal("-2543.39")),
                transaction(
                    9L,
                    LocalDate.of(2026, 7, 10),
                    "Zakład Ubezpieczeń Społecznych",
                    new BigDecimal("-100.00"))));

    List<ZusPayment> payments =
        new MockZusClient(source)
            .findPayments(1L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1));

    assertThat(payments)
        .extracting(ZusPayment::externalId)
        .containsExactly("BANK-TX-9", "BANK-TX-12");
    assertThat(payments)
        .extracting(ZusPayment::amount)
        .containsExactly(new BigDecimal("100.00"), new BigDecimal("2543.39"));
    assertThat(payments).allMatch(payment -> payment.status() == ZusPaymentStatus.SETTLED);
  }

  private BankTransactionZusPaymentSource.BankTransaction transaction(
      long id, LocalDate date, String counterparty, BigDecimal amount) {
    return new BankTransactionZusPaymentSource.BankTransaction(
        id, date, amount, "PLN", "ZUS/2026", counterparty, null);
  }
}
