package com.smartbox.investory.application.profile;

import com.smartbox.investory.infrastructure.CurrencyType;
import com.smartbox.investory.services.PlanningPresentation;
import java.math.BigDecimal;
import java.util.List;

public record InvestmentProfile(
    Long portfolioId,
    CurrencyType currency,
    BigDecimal marketPortfolioValue,
    BigDecimal longTermAssetValue,
    BigDecimal totalNetWorth,
    BigDecimal historicalMarketInvestmentIncome,
    BigDecimal expectedLongTermAssetIncome,
    BigDecimal totalInvestmentIncome,
    BigDecimal liquidAssets,
    BigDecimal illiquidAssets,
    List<ProfileAllocation> allocations,
    List<ProjectedLongTermAsset> longTermAssets) {
  public InvestmentProfile {
    allocations = allocations == null ? List.of() : List.copyOf(allocations);
    longTermAssets = longTermAssets == null ? List.of() : List.copyOf(longTermAssets);
  }

  public BigDecimal marketPortfolioPercentage() {
    return percentageOfTotal(marketPortfolioValue);
  }

  public BigDecimal longTermAssetPercentage() {
    return percentageOfTotal(longTermAssetValue);
  }

  public BigDecimal liquidAssetsPercentage() {
    return percentageOfTotal(liquidAssets);
  }

  public BigDecimal illiquidAssetsPercentage() {
    return percentageOfTotal(illiquidAssets);
  }

  public String totalNetWorthDisplay() {
    return PlanningPresentation.money(totalNetWorth, currency);
  }

  public String totalNetWorthWholeDisplay() {
    return PlanningPresentation.wholeNumber(totalNetWorth);
  }

  public String getMarketPortfolioValueWholeDisplay() {
    return marketPortfolioValueWholeDisplay();
  }

  public String getLongTermAssetValueWholeDisplay() {
    return longTermAssetValueWholeDisplay();
  }

  public String getLiquidAssetsWholeDisplay() {
    return liquidAssetsWholeDisplay();
  }

  public String getIlliquidAssetsWholeDisplay() {
    return illiquidAssetsWholeDisplay();
  }

  public String getHistoricalMarketInvestmentIncomeWholeDisplay() {
    return historicalMarketInvestmentIncomeWholeDisplay();
  }

  public String getExpectedLongTermAssetIncomeWholeDisplay() {
    return expectedLongTermAssetIncomeWholeDisplay();
  }

  public String getMarketPortfolioMeta() {
    return marketPortfolioPercentageDisplay() + " of net worth";
  }

  public String getLongTermAssetMeta() {
    return longTermAssetPercentageDisplay() + " of net worth";
  }

  public String getLiquidAssetsMeta() {
    return liquidAssetsPercentageDisplay() + " of net worth";
  }

  public String getIlliquidAssetsMeta() {
    return illiquidAssetsPercentageDisplay() + " of net worth";
  }

  public String liquidAssetsDisplay() {
    return PlanningPresentation.money(liquidAssets, currency);
  }

  public String liquidAssetsWholeDisplay() {
    return PlanningPresentation.wholeNumber(liquidAssets);
  }

  public String totalInvestmentIncomeDisplay() {
    return PlanningPresentation.money(totalInvestmentIncome, currency);
  }

  public String marketPortfolioValueDisplay() {
    return PlanningPresentation.money(marketPortfolioValue, currency);
  }

  public String marketPortfolioValueWholeDisplay() {
    return PlanningPresentation.wholeNumber(marketPortfolioValue);
  }

  public String longTermAssetValueDisplay() {
    return PlanningPresentation.money(longTermAssetValue, currency);
  }

  public String longTermAssetValueWholeDisplay() {
    return PlanningPresentation.wholeNumber(longTermAssetValue);
  }

  public String illiquidAssetsDisplay() {
    return PlanningPresentation.money(illiquidAssets, currency);
  }

  public String illiquidAssetsWholeDisplay() {
    return PlanningPresentation.wholeNumber(illiquidAssets);
  }

  public String liquidAssetsPercentageDisplay() {
    return PlanningPresentation.percentage(liquidAssetsPercentage());
  }

  public String marketPortfolioPercentageDisplay() {
    return PlanningPresentation.percentage(marketPortfolioPercentage());
  }

  public String longTermAssetPercentageDisplay() {
    return PlanningPresentation.percentage(longTermAssetPercentage());
  }

  public String illiquidAssetsPercentageDisplay() {
    return PlanningPresentation.percentage(illiquidAssetsPercentage());
  }

  public String historicalMarketInvestmentIncomeDisplay() {
    return PlanningPresentation.money(historicalMarketInvestmentIncome, currency);
  }

  public String historicalMarketInvestmentIncomeWholeDisplay() {
    return PlanningPresentation.wholeNumber(historicalMarketInvestmentIncome);
  }

  public String expectedLongTermAssetIncomeDisplay() {
    return PlanningPresentation.money(expectedLongTermAssetIncome, currency);
  }

  public String expectedLongTermAssetIncomeWholeDisplay() {
    return PlanningPresentation.wholeNumber(expectedLongTermAssetIncome);
  }

  public String longTermIncomeYieldDisplay() {
    if (longTermAssetValue == null
        || longTermAssetValue.signum() == 0
        || expectedLongTermAssetIncome == null) {
      return PlanningPresentation.percentage(BigDecimal.ZERO);
    }
    return PlanningPresentation.percentage(
        expectedLongTermAssetIncome.divide(longTermAssetValue, 8, java.math.RoundingMode.HALF_UP));
  }

  public String largestAllocationLabelDisplay() {
    return allocations.stream()
        .filter(ProfileAllocation::isNonZero)
        .max(java.util.Comparator.comparing(ProfileAllocation::percentage))
        .map(ProfileAllocation::getBucketLabel)
        .orElse("—");
  }

  public String largestAllocationShareDisplay() {
    return allocations.stream()
        .filter(ProfileAllocation::isNonZero)
        .max(java.util.Comparator.comparing(ProfileAllocation::percentage))
        .map(ProfileAllocation::getPercentageDisplay)
        .orElse("—");
  }

  private BigDecimal percentageOfTotal(BigDecimal value) {
    return totalNetWorth == null || totalNetWorth.signum() == 0 || value == null
        ? BigDecimal.ZERO
        : value.divide(totalNetWorth, 8, java.math.RoundingMode.HALF_UP);
  }
}
