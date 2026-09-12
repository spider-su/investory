package com.smartbox.investory.integrations.zus;

import com.smartbox.investory.integrations.zus.persistence.BankTransactionZusPaymentSource;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** Mock provider that presents canonical bank history as a future ZUS API would. */
@Component
public class MockZusClient implements ZusClient {
  private final BankTransactionZusPaymentSource source;

  public MockZusClient(BankTransactionZusPaymentSource source) {
    this.source = source;
  }

  @Override
  public List<ZusPayment> findPayments(
      Long profileId, java.time.LocalDate from, java.time.LocalDate to) {
    return source.findOutgoingZusTransactions(profileId, from, to).stream()
        .sorted(
            Comparator.comparing(BankTransactionZusPaymentSource.BankTransaction::paymentDate)
                .thenComparing(BankTransactionZusPaymentSource.BankTransaction::id))
        .map(
            transaction ->
                new ZusPayment(
                    "BANK-TX-" + transaction.id(),
                    transaction.paymentDate(),
                    transaction.amount().abs(),
                    transaction.currency(),
                    title(transaction),
                    ZusPaymentStatus.SETTLED))
        .toList();
  }

  private String title(BankTransactionZusPaymentSource.BankTransaction transaction) {
    if (transaction.reference() != null && !transaction.reference().isBlank()) {
      return transaction.reference();
    }
    return transaction.counterparty();
  }
}
