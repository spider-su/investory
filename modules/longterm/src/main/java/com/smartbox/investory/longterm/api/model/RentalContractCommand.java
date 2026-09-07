package com.smartbox.investory.longterm.api.model;

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
    boolean endCurrentContractBeforeStart,
    List<RentalTermCommand> terms) {}
