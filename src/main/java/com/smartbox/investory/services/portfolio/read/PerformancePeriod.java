package com.smartbox.investory.services.portfolio.read;

import java.time.LocalDate;

/** Reporting period represented by the first and last source observations included. */
public record PerformancePeriod(LocalDate startDate, LocalDate endDate) {}
