package com.smartbox.investory.testsupport.happyinvestor;

import java.math.BigDecimal;

/** Cross-module simulation intent, kept independent of any retirement implementation details. */
public record HappyInvestorSimulationSpec(
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
        HappyInvestorTestData.CASH_RESERVE,
        25,
        HappyInvestorTestData.INFLATION,
        new BigDecimal("4000.00"),
        true,
        true,
        true,
        true,
        true,
        "FINANCIAL_INDEPENDENCE");
  }
}
