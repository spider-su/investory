package com.smartbox.investory.ryczalt.application.query;

import com.smartbox.investory.ryczalt.domain.ApprovalMethod;
import com.smartbox.investory.ryczalt.domain.ApprovalStatus;
import com.smartbox.investory.ryczalt.domain.InvoicePaymentStatus;
import com.smartbox.investory.ryczalt.domain.PaymentVerificationPolicy;
import com.smartbox.investory.ryczalt.persistence.InvoiceDirection;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RyczaltInvoiceReadModel(
    long id,
    InvoiceDirection direction,
    String reference,
    LocalDate issueDate,
    LocalDate accountingDate,
    BigDecimal netAmount,
    BigDecimal vatAmount,
    BigDecimal grossAmount,
    CurrencyType currency,
    BigDecimal bookedNetPln,
    BigDecimal ryczaltRate,
    BigDecimal deductibleVat,
    String classification,
    CounterpartyView counterparty,
    ApprovalStatus approvalStatus,
    ApprovalMethod approvalMethod,
    PaymentVerificationPolicy paymentVerificationPolicy,
    InvoicePaymentStatus paymentStatus,
    String sourceType,
    String sourceReference) {
  public record CounterpartyView(long id, String legalName, String alias, String taxIdentifier) {}

  public RyczaltInvoiceReadModel(
      long id,
      InvoiceDirection direction,
      String reference,
      LocalDate issueDate,
      LocalDate accountingDate,
      BigDecimal netAmount,
      BigDecimal vatAmount,
      BigDecimal grossAmount,
      CurrencyType currency,
      BigDecimal bookedNetPln,
      BigDecimal ryczaltRate,
      BigDecimal deductibleVat) {
    this(
        id,
        direction,
        reference,
        issueDate,
        accountingDate,
        netAmount,
        vatAmount,
        grossAmount,
        currency,
        bookedNetPln,
        ryczaltRate,
        deductibleVat,
        null,
        null,
        ApprovalStatus.NEEDS_REVIEW,
        null,
        PaymentVerificationPolicy.REQUIRED,
        InvoicePaymentStatus.UNMATCHED,
        null,
        null);
  }
}
