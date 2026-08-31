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
    terms = terms == null ? List.of() : List.copyOf(terms);
  }

  public RentalContractModel(Long id, LocalDate startDate, LocalDate endDate, List<Term> terms) {
    this(id, startDate, endDate, null, null, null, null, null, null, terms);
  }

  public RentalContractModel(
      Long id,
      LocalDate startDate,
      LocalDate endDate,
      LocalDate terminatedDate,
      Boolean rentalTaxPaidByTenant,
      List<Term> terms) {
    this(
        id,
        startDate,
        endDate,
        terminatedDate,
        rentalTaxPaidByTenant,
        null,
        null,
        null,
        null,
        terms);
  }

  public record Term(
      CashFlowTypeModel type, BigDecimal amount, FrequencyModel frequency, boolean paidByTenant) {}
}
