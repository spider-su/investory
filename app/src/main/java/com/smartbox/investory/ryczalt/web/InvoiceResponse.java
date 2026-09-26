package com.smartbox.investory.ryczalt.web;

import com.smartbox.investory.ryczalt.domain.ApprovalMethod;
import com.smartbox.investory.ryczalt.domain.ApprovalStatus;
import com.smartbox.investory.ryczalt.domain.InvoicePaymentStatus;
import com.smartbox.investory.ryczalt.domain.PaymentVerificationPolicy;
import com.smartbox.investory.shared.currency.CurrencyType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public record InvoiceResponse(
    long id,
    String direction,
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
    String classification,
    String vatTreatment,
    String vatDeductionRatio,
    CounterpartyView counterparty,
    ApprovalStatus approvalStatus,
    ApprovalMethod approvalMethod,
    PaymentVerificationPolicy paymentVerificationPolicy,
    InvoicePaymentStatus paymentStatus,
    @Schema(nullable = true, description = "Provenance source, for example KSEF or UPLOAD")
        String sourceType,
    @Schema(nullable = true, description = "Display-safe provider reference; null for uploads")
        String sourceReference) {
  public record CounterpartyView(
      long id,
      String legalName,
      String alias,
      @Schema(nullable = true, description = "Counterparty tax identifier, when known")
          String taxIdentifier) {}
}
