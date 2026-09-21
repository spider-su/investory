package com.smartbox.investory.ryczalt.web;

import com.smartbox.investory.ryczalt.domain.ApprovalMethod;
import com.smartbox.investory.ryczalt.domain.ApprovalStatus;
import com.smartbox.investory.ryczalt.domain.PaymentVerificationPolicy;
import com.smartbox.investory.ryczalt.persistence.InvoiceDirection;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.time.LocalDate;

public record InvoiceResponse(
    long id,
    InvoiceDirection direction,
    String reference,
    LocalDate issueDate,
    LocalDate accountingDate,
    String netAmount,
    String vatAmount,
    String grossAmount,
    CurrencyType currency,
    String bookedNetPln,
    String ryczaltRate,
    String deductibleVat,
    CounterpartyView counterparty,
    ApprovalStatus approvalStatus,
    ApprovalMethod approvalMethod,
    PaymentVerificationPolicy paymentVerificationPolicy,
    String paymentStatus) {
  public record CounterpartyView(long id, String legalName, String alias) {}
}
