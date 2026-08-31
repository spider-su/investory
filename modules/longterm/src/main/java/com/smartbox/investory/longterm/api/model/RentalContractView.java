package com.smartbox.investory.longterm.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Public Long-Term API model. */
public record RentalContractView(
    Long id,
    String tenantName,
    String tenantEmail,
    String tenantPhone,
    LocalDate startDate,
    LocalDate endDate,
    LocalDate terminatedDate,
    LocalDate effectiveEndDate,
    RentalContractStatusModel status,
    Boolean rentalTaxPaidByTenant,
    BigDecimal monthlyTaxBase,
    List<RentalTermView> terms) {}
