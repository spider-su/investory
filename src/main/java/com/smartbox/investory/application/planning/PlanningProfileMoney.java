package com.smartbox.investory.application.planning;

import java.math.BigDecimal;

/** Display-currency-only profile amounts for the planning pages. */
public record PlanningProfileMoney(
    BigDecimal marketPortfolioValue,
    BigDecimal longTermAssetValue,
    BigDecimal totalNetWorth,
    BigDecimal expectedLongTermAssetIncome,
    BigDecimal liquidAssets,
    BigDecimal illiquidAssets) {
  public String marketPortfolioValueWholeDisplay() {
    return PlanningPresentation.wholeNumber(marketPortfolioValue);
  }

  public String longTermAssetValueWholeDisplay() {
    return PlanningPresentation.wholeNumber(longTermAssetValue);
  }

  public String totalNetWorthWholeDisplay() {
    return PlanningPresentation.wholeNumber(totalNetWorth);
  }

  public String expectedLongTermAssetIncomeWholeDisplay() {
    return PlanningPresentation.wholeNumber(expectedLongTermAssetIncome);
  }

  public String liquidAssetsWholeDisplay() {
    return PlanningPresentation.wholeNumber(liquidAssets);
  }

  public String illiquidAssetsWholeDisplay() {
    return PlanningPresentation.wholeNumber(illiquidAssets);
  }
}
