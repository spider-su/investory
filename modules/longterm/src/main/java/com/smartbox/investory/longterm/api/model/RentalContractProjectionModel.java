package com.smartbox.investory.longterm.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Rental terms needed by projections; tenant contact data is intentionally excluded. */
public record RentalContractProjectionModel(
    Long id,
    LocalDate startDate,
    LocalDate endDate,
    LocalDate terminatedDate,
    Boolean rentalTaxPaidByTenant,
    BigDecimal monthlyTaxBase,
    List<RentalContractModel.Term> terms) {
  public RentalContractProjectionModel {
    terms = com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(terms);
  }
}
