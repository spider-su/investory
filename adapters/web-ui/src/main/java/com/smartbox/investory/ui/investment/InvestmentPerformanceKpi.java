package com.smartbox.investory.ui.investment;

import java.math.BigDecimal;

/** Page-facing investment performance data. */
public record InvestmentPerformanceKpi(
    boolean available,
    BigDecimal totalReturn,
    String totalReturnDisplay,
    String kpiStartDate,
    BigDecimal historicalAnnualizedReturn,
    String historicalAnnualizedReturnDisplay,
    BigDecimal expectedAnnualReturn,
    String expectedAnnualReturnDisplay,
    BigDecimal historyYears,
    String historyContext) {}
