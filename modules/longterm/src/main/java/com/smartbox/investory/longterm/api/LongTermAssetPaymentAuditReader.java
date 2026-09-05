package com.smartbox.investory.longterm.api;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Read model for operational checks of currently payable rental amounts. */
public interface LongTermAssetPaymentAuditReader {
  List<PaymentAuditRow> paymentAudit(Long portfolioId, LocalDate date);

  record PaymentAuditRow(
      String assetName, String tenantName, BigDecimal totalMonthlyPayment, CurrencyType currency) {}
}
