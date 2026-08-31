package com.smartbox.investory.longterm.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RentalContractModel(
    Long id,
    LocalDate startDate,
    LocalDate endDate,
    LocalDate terminatedDate,
    Boolean rentalTaxPaidByTenant,
    BigDecimal monthlyTaxBase,
    String tenantName,
    String tenantEmail,
    String tenantPhone,
    List<Term> terms) {
  public RentalContractModel {
    terms = com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(terms);
  }

  public record Term(
      CashFlowType type, BigDecimal amount, Frequency frequency, boolean paidByTenant) {}
}
