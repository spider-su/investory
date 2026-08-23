package com.smartbox.investory.retirement.profile;

import com.smartbox.investory.longterm.api.LongTermAnnualProjectionApi;
import com.smartbox.investory.longterm.api.model.LongTermAssetProjectionModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetTypeModel;
import com.smartbox.investory.shared.currency.CurrencyType;
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
    List<ProjectedLongTermAsset> longTermAssets,
    BigDecimal currentRentalIncome,
    BigDecimal currentBondIncome,
    LongTermAnnualProjectionApi.PlanningState longTermPlanningState,
    BigDecimal retirementReserve,
    BigDecimal investmentCapital) {
  /**
   * Compatibility constructor for profile read models not yet carrying Long-Term planning state.
   */
  public InvestmentProfile(
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
      List<ProjectedLongTermAsset> longTermAssets,
      BigDecimal currentRentalIncome,
      BigDecimal currentBondIncome) {
    this(
        portfolioId,
        currency,
        marketPortfolioValue,
        longTermAssetValue,
        totalNetWorth,
        historicalMarketInvestmentIncome,
        expectedLongTermAssetIncome,
        totalInvestmentIncome,
        liquidAssets,
        illiquidAssets,
        allocations,
        longTermAssets,
        currentRentalIncome,
        currentBondIncome,
        legacyPlanningState(longTermAssets, currentRentalIncome),
        liquidAssets == null ? BigDecimal.ZERO : liquidAssets,
        marketPortfolioValue
            .subtract(liquidAssets == null ? BigDecimal.ZERO : liquidAssets)
            .max(BigDecimal.ZERO));
  }

  /** Compatibility constructor for callers that do not yet provide annual Long-Term facts. */
  public InvestmentProfile(
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
    this(
        portfolioId,
        currency,
        marketPortfolioValue,
        longTermAssetValue,
        totalNetWorth,
        historicalMarketInvestmentIncome,
        expectedLongTermAssetIncome,
        totalInvestmentIncome,
        liquidAssets,
        illiquidAssets,
        allocations,
        longTermAssets,
        null,
        null,
        LongTermAnnualProjectionApi.PlanningState.EMPTY,
        liquidAssets == null ? BigDecimal.ZERO : liquidAssets,
        marketPortfolioValue
            .subtract(liquidAssets == null ? BigDecimal.ZERO : liquidAssets)
            .max(BigDecimal.ZERO));
  }

  public InvestmentProfile {
    allocations = allocations == null ? List.of() : List.copyOf(allocations);
    longTermAssets = longTermAssets == null ? List.of() : List.copyOf(longTermAssets);
    longTermPlanningState =
        longTermPlanningState == null
            ? LongTermAnnualProjectionApi.PlanningState.EMPTY
            : longTermPlanningState;
    retirementReserve = retirementReserve == null ? BigDecimal.ZERO : retirementReserve;
    investmentCapital = investmentCapital == null ? BigDecimal.ZERO : investmentCapital;
  }

  /** Returns an immutable economic view with the reviewed planning baseline applied. */
  /**
   * @deprecated Use PlanningBaseline as an explicit future input; retained for compatibility.
   */
  @Deprecated
  public InvestmentProfile withPlanningBaseline(
      BigDecimal reserve,
      BigDecimal investmentCapital,
      BigDecimal longTermCapital,
      BigDecimal rentalAnnualIncome,
      BigDecimal bondAnnualIncome) {
    return withPlanningBaseline(
        reserve,
        investmentCapital,
        longTermCapital,
        rentalAnnualIncome,
        bondAnnualIncome,
        longTermPlanningState);
  }

  public InvestmentProfile withPlanningBaseline(
      BigDecimal reserve,
      BigDecimal investmentCapital,
      BigDecimal longTermCapital,
      BigDecimal rentalAnnualIncome,
      BigDecimal bondAnnualIncome,
      LongTermAnnualProjectionApi.PlanningState planningState) {
    BigDecimal frozenReserve = reserve == null ? BigDecimal.ZERO : reserve;
    BigDecimal frozenInvestment = investmentCapital == null ? BigDecimal.ZERO : investmentCapital;
    BigDecimal frozenLongTerm = longTermCapital == null ? BigDecimal.ZERO : longTermCapital;
    return new InvestmentProfile(
        portfolioId,
        currency,
        frozenReserve.add(frozenInvestment),
        frozenLongTerm,
        frozenReserve.add(frozenInvestment).add(frozenLongTerm),
        historicalMarketInvestmentIncome,
        expectedLongTermAssetIncome,
        totalInvestmentIncome,
        frozenReserve,
        illiquidAssets,
        allocations,
        longTermAssets,
        rentalAnnualIncome,
        bondAnnualIncome,
        planningState,
        frozenReserve,
        frozenInvestment);
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
    return ProfilePresentation.money(totalNetWorth, currency);
  }

  public String totalNetWorthWholeDisplay() {
    return ProfilePresentation.wholeNumber(totalNetWorth);
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
    return ProfilePresentation.money(liquidAssets, currency);
  }

  public String liquidAssetsWholeDisplay() {
    return ProfilePresentation.wholeNumber(liquidAssets);
  }

  public String totalInvestmentIncomeDisplay() {
    return ProfilePresentation.money(totalInvestmentIncome, currency);
  }

  public String marketPortfolioValueDisplay() {
    return ProfilePresentation.money(marketPortfolioValue, currency);
  }

  public String marketPortfolioValueWholeDisplay() {
    return ProfilePresentation.wholeNumber(marketPortfolioValue);
  }

  public String longTermAssetValueDisplay() {
    return ProfilePresentation.money(longTermAssetValue, currency);
  }

  public String longTermAssetValueWholeDisplay() {
    return ProfilePresentation.wholeNumber(longTermAssetValue);
  }

  public String illiquidAssetsDisplay() {
    return ProfilePresentation.money(illiquidAssets, currency);
  }

  public String illiquidAssetsWholeDisplay() {
    return ProfilePresentation.wholeNumber(illiquidAssets);
  }

  public String liquidAssetsPercentageDisplay() {
    return ProfilePresentation.percentage(liquidAssetsPercentage());
  }

  public String marketPortfolioPercentageDisplay() {
    return ProfilePresentation.percentage(marketPortfolioPercentage());
  }

  public String longTermAssetPercentageDisplay() {
    return ProfilePresentation.percentage(longTermAssetPercentage());
  }

  public String illiquidAssetsPercentageDisplay() {
    return ProfilePresentation.percentage(illiquidAssetsPercentage());
  }

  public String historicalMarketInvestmentIncomeDisplay() {
    return ProfilePresentation.money(historicalMarketInvestmentIncome, currency);
  }

  public String historicalMarketInvestmentIncomeWholeDisplay() {
    return ProfilePresentation.wholeNumber(historicalMarketInvestmentIncome);
  }

  public String expectedLongTermAssetIncomeDisplay() {
    return ProfilePresentation.money(expectedLongTermAssetIncome, currency);
  }

  public String expectedLongTermAssetIncomeWholeDisplay() {
    return ProfilePresentation.wholeNumber(expectedLongTermAssetIncome);
  }

  public String longTermIncomeYieldDisplay() {
    if (longTermAssetValue == null
        || longTermAssetValue.signum() == 0
        || expectedLongTermAssetIncome == null) {
      return ProfilePresentation.percentage(BigDecimal.ZERO);
    }
    return ProfilePresentation.percentage(
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

  /**
   * Read compatibility only for profiles created before the Long-Term planning state existed. The
   * canonical profile is populated by {@code InvestmentProfileFacade} directly from the Long-Term
   * public projection model.
   */
  @Deprecated
  private static LongTermAnnualProjectionApi.PlanningState legacyPlanningState(
      List<ProjectedLongTermAsset> assets, BigDecimal currentRentalIncome) {
    List<LongTermAssetProjectionModel> models =
        (assets == null ? List.<ProjectedLongTermAsset>of() : assets)
            .stream()
                .map(
                    asset ->
                        new LongTermAssetProjectionModel(
                            asset.id(),
                            asset.name(),
                            asset.type(),
                            asset.currency(),
                            asset.currentValue(),
                            asset.periods().stream()
                                .map(
                                    period ->
                                        new LongTermAssetProjectionModel.Period(
                                            period.validFrom(),
                                            period.validTo(),
                                            period.annualIncome(),
                                            period.annualExpense(),
                                            period.annualReturnRate(),
                                            period.cashFlowType(),
                                            period.paidByTenant()))
                                .toList(),
                            asset.rentalContracts(),
                            asset.maturityDate(),
                            asset.redemptionValue(),
                            asset.interestTreatment(),
                            asset.taxRate(),
                            asset.taxBase(),
                            asset.rentalTaxPaidByTenant()))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    if (currentRentalIncome != null
        && currentRentalIncome.signum() != 0
        && models.stream().noneMatch(asset -> asset.type() == LongTermAssetTypeModel.REAL_ESTATE))
      models.add(
          new LongTermAssetProjectionModel(
              null,
              "legacy-rental",
              LongTermAssetTypeModel.REAL_ESTATE,
              null,
              BigDecimal.ZERO,
              List.of(
                  new LongTermAssetProjectionModel.Period(
                      null, null, currentRentalIncome, BigDecimal.ZERO, BigDecimal.ZERO)),
              null,
              null,
              null,
              BigDecimal.ZERO));
    return new LongTermAnnualProjectionApi.PlanningState(
        models, BigDecimal.ZERO, 0, LongTermAnnualProjectionApi.Source.PROJECTED);
  }
}
