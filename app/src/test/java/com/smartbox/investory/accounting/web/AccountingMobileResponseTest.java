package com.smartbox.investory.accounting.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbox.investory.accounting.api.AccountingUserApi;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AccountingMobileResponseTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void preservesNullPaymentAmountsAndDoesNotInventOutstandingMoney() {
    var payment =
        AccountingMobileResponse.Payment.from(
            new AccountingUserApi.PaymentView(
                "VAT", null, null, null, null, null, null, null, "UNKNOWN"));

    assertThat(payment.amount()).isNull();
    assertThat(payment.paidAmount()).isNull();
    assertThat(payment.outstandingAmount()).isNull();
  }

  @Test
  void serializesMobileMoneyAsDecimalStrings() throws Exception {
    var summary =
        new AccountingMobileResponse.Summary(
            new BigDecimal("1234.5600"),
            new BigDecimal("23.00"),
            BigDecimal.ZERO,
            null,
            0,
            0,
            new BigDecimal("1257.5600"));

    var json = mapper.writeValueAsString(summary);

    assertThat(json).contains("\"revenue\":\"1234.5600\"");
    assertThat(json).contains("\"vat\":\"23.00\"");
    assertThat(json).contains("\"totalObligations\":\"1257.5600\"");
  }
}
