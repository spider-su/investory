package com.smartbox.investory.testsupport.happyinvestor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Cross-module simulation intent, kept independent of any retirement implementation details. */
public record HappyInvestorSimulationSpec(
    LocalDate asOfDate,
    BigDecimal reportingCurrencyReserve,
    int horizonYears,
    BigDecimal inflationRate,
    BigDecimal monthlyContribution,
    boolean retainApartments,
    boolean includeRentalIncome,
    boolean carIsPersonalAsset,
    boolean annualRebalancing,
    boolean rolloverTreasuryMaturity,
    String goal) {
  public static HappyInvestorSimulationSpec defaults() {
    return new HappyInvestorSimulationSpec(
        HappyInvestorTestData.REFERENCE_DATE,
        HappyInvestorTestData.CASH_RESERVE,
        HappyInvestorPlanFacts.END_AGE - HappyInvestorPlanFacts.CURRENT_AGE,
        HappyInvestorPlanFacts.INFLATION,
        HappyInvestorPlanFacts.ANNUAL_PRE_RETIREMENT_CONTRIBUTION.divide(BigDecimal.valueOf(12)),
        true,
        true,
        true,
        true,
        true,
        "FINANCIAL_INDEPENDENCE");
  }
}
