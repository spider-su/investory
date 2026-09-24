package com.smartbox.investory.ui.accounting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RyczaltAccountingPageViewAssemblerTest {
  @Test
  void bankRowsUseCounterpartyAliasAndShowWholeAmountAndAllocationStatus() {
    var counterparties =
        List.of(
            new RyczaltWebAccountingClient.Counterparty(
                1, "Żabka Sp. z o.o.", "Zabka", "Zabka", null, "PL", null, 1, 2));
    var transactions =
        List.of(
            new RyczaltWebAccountingClient.Transaction(
                7,
                LocalDate.of(2026, 9, 10),
                new BigDecimal("-99.60"),
                "PLN",
                "BANK-7",
                "ZABKA SP Z OO",
                "Invoice payment",
                new BigDecimal("50.00")));

    var rows = RyczaltAccountingPageViewAssembler.bankTransactionRows(counterparties, transactions);

    assertEquals(1, rows.size());
    assertEquals("Zabka", rows.getFirst().counterparty());
    assertEquals("-100", rows.getFirst().amount());
    assertEquals("Partially matched", rows.getFirst().status());
    assertEquals("Invoice payment", rows.getFirst().description());
  }

  @Test
  void bankRowsRetainUnknownCounterpartyAndReportUnmatchedStatus() {
    var transactions =
        List.of(
            new RyczaltWebAccountingClient.Transaction(
                8,
                LocalDate.of(2026, 9, 11),
                new BigDecimal("125.00"),
                "EUR",
                "BANK-8",
                "Unlisted Client",
                null,
                BigDecimal.ZERO));

    var rows = RyczaltAccountingPageViewAssembler.bankTransactionRows(List.of(), transactions);

    assertEquals("Unlisted Client", rows.getFirst().counterparty());
    assertEquals("125", rows.getFirst().amount());
    assertEquals("Unmatched", rows.getFirst().status());
    assertEquals(null, rows.getFirst().description());
  }

  @Test
  void bankTransactionsUseCurrentMonthIncomeAndNextMonthPayments() {
    var currentIncome = transaction(1, LocalDate.of(2026, 2, 3), new BigDecimal("100.00"));
    var currentPayment = transaction(2, LocalDate.of(2026, 2, 4), new BigDecimal("-50.00"));
    var nextIncome = transaction(3, LocalDate.of(2026, 3, 3), new BigDecimal("200.00"));
    var nextPayment = transaction(4, LocalDate.of(2026, 3, 4), new BigDecimal("-75.00"));

    var result =
        RyczaltAccountingPageViewAssembler.bankTransactionsForDisplay(
            List.of(currentIncome, currentPayment), List.of(nextIncome, nextPayment));

    assertEquals(List.of(currentIncome, nextPayment), result);
  }

  @Test
  void counterpartyBillTotalSumsGrossAmountsExactly() {
    var bills = List.of(invoice(1, new BigDecimal("100.10")), invoice(2, new BigDecimal("23.45")));

    assertEquals(
        new BigDecimal("123.55"), RyczaltAccountingPageController.counterpartyInvoiceTotal(bills));
  }

  private static RyczaltWebAccountingClient.Transaction transaction(
      long id, LocalDate date, BigDecimal amount) {
    return new RyczaltWebAccountingClient.Transaction(
        id, date, amount, "PLN", "BANK-" + id, "Counterparty", null, BigDecimal.ZERO);
  }

  private static RyczaltWebAccountingClient.Invoice invoice(long id, BigDecimal grossAmount) {
    return new RyczaltWebAccountingClient.Invoice(
        id,
        "COST",
        "FV/" + id,
        LocalDate.of(2026, 9, 1),
        LocalDate.of(2026, 9, 1),
        grossAmount,
        BigDecimal.ZERO,
        grossAmount,
        "PLN",
        "APPROVED",
        null,
        "REQUIRED",
        "UNMATCHED",
        "Supplier");
  }
}
