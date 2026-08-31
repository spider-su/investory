package com.smartbox.investory.longterm.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Public Long-Term API model. */
public record RentalContractCommand(
    Long portfolioId,
    Long assetId,
    String tenantName,
    String tenantEmail,
    String tenantPhone,
    LocalDate startDate,
    LocalDate endDate,
    BigDecimal monthlyTaxBase,
    Boolean rentalTaxPaidByTenant,
    boolean endCurrentContractBeforeStart,
    List<RentalTermCommand> terms) {}
