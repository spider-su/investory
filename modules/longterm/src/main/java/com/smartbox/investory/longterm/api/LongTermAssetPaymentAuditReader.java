package com.smartbox.investory.longterm.api;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Operational read models for current rental payments and stored fee amounts. */
public interface LongTermAssetPaymentAuditReader {
  List<PaymentAuditRow> paymentAudit(Long portfolioId, LocalDate date);

  /** Stored monthly fee amounts for active rental contracts; null means no term is configured. */
  List<FeeReviewRow> feeReview(Long portfolioId, LocalDate date);

  record FeeReviewRow(
      String assetName,
      BigDecimal monthlyUtilities,
      BigDecimal monthlyAdminFee,
      CurrencyType currency) {}

  record PaymentAuditRow(
      String assetName,
      String tenantName,
      BigDecimal totalMonthlyPayment,
      BigDecimal monthlyRentalIncome,
      CurrencyType currency) {}
}
