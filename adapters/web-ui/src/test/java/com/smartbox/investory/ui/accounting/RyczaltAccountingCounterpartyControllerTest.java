package com.smartbox.investory.ui.accounting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RyczaltAccountingCounterpartyControllerTest {
  @Test
  void counterpartyBillTotalSumsGrossAmountsExactly() {
    var bills = List.of(invoice(1, new BigDecimal("100.10")), invoice(2, new BigDecimal("23.45")));

    assertEquals(
        new BigDecimal("123.55"),
        RyczaltAccountingCounterpartyController.counterpartyInvoiceTotal(bills));
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
