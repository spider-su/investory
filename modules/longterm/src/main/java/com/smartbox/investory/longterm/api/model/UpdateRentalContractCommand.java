package com.smartbox.investory.longterm.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Public Long-Term API model. */
public record UpdateRentalContractCommand(
    Long portfolioId,
    Long assetId,
    Long contractId,
    String tenantName,
    String tenantEmail,
    String tenantPhone,
    LocalDate startDate,
    LocalDate endDate,
    BigDecimal monthlyTaxBase,
    Boolean rentalTaxPaidByTenant,
    boolean usePropertyTaxPayerDefault,
    List<RentalTermCommand> terms) {}
