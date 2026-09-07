package com.smartbox.investory.longterm.api.model;

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
    List<RentalTermCommand> terms) {}
