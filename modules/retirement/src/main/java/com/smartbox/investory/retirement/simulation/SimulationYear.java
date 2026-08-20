package com.smartbox.investory.retirement.simulation;

import java.math.BigDecimal;

public record SimulationYear(
    int age,
    int year,
    BigDecimal startNetWorth,
    BigDecimal coreExpenses,
    BigDecimal discretionaryExpenses,
    BigDecimal eventExpenses,
    BigDecimal totalExpenses,
    BigDecimal passiveIncome,
    BigDecimal pensionIncome,
    BigDecimal eventIncome,
    BigDecimal totalIncome,
    BigDecimal requiredPortfolioFunding,
    BigDecimal actualPortfolioWithdrawal,
    BigDecimal manualLiquidReserveWithdrawal,
    BigDecimal recurringFundingGap,
    BigDecimal safeReserveStart,
    BigDecimal safeReserveTarget,
    BigDecimal safeReserveEnd,
    BigDecimal safeReserveCoverageYears,
    BigDecimal equityReturnRate,
    BigDecimal equityGain,
    BigDecimal equityToFixedIncomeTransfer,
    BigDecimal emergencyEquityWithdrawal,
    BigDecimal cashStart,
    BigDecimal cashEnd,
    BigDecimal fixedIncomeStart,
    BigDecimal fixedIncomeEnd,
    BigDecimal equityStart,
    BigDecimal equityEnd,
    BigDecimal realEstateStart,
    BigDecimal realEstateEnd,
    BigDecimal otherStart,
    BigDecimal otherEnd,
    BigDecimal manualLiquidReserveStart,
    BigDecimal manualLiquidReserveEnd,
    BigDecimal contractualAssetsStart,
    BigDecimal contractualAssetsEnd,
    BigDecimal spendableAssetsEnd,
    BigDecimal financialAssetsEnd,
    BigDecimal totalLiquidAssets,
    BigDecimal totalIlliquidAssets,
    BigDecimal endNetWorth,
    boolean failed,
    BigDecimal unfundedAmount,
    SimulationLifecyclePhase lifecyclePhase,
    BigDecimal employmentIncome,
    BigDecimal preRetirementContribution,
    boolean retirementTransitionYear,
    BigDecimal rentalIncome,
    BigDecimal incomeGap,
    BigDecimal bondValueEnd,
    BigDecimal bondIncome) {
  /** Compatibility constructor for the pre-retirement-lifecycle yearly result shape. */
  public SimulationYear(
      int age,
      int year,
      BigDecimal startNetWorth,
      BigDecimal coreExpenses,
      BigDecimal discretionaryExpenses,
      BigDecimal eventExpenses,
      BigDecimal totalExpenses,
      BigDecimal passiveIncome,
      BigDecimal pensionIncome,
      BigDecimal eventIncome,
      BigDecimal totalIncome,
      BigDecimal requiredPortfolioFunding,
      BigDecimal actualPortfolioWithdrawal,
      BigDecimal manualLiquidReserveWithdrawal,
      BigDecimal recurringFundingGap,
      BigDecimal safeReserveStart,
      BigDecimal safeReserveTarget,
      BigDecimal safeReserveEnd,
      BigDecimal safeReserveCoverageYears,
      BigDecimal equityReturnRate,
      BigDecimal equityGain,
      BigDecimal equityToFixedIncomeTransfer,
      BigDecimal emergencyEquityWithdrawal,
      BigDecimal cashStart,
      BigDecimal cashEnd,
      BigDecimal fixedIncomeStart,
      BigDecimal fixedIncomeEnd,
      BigDecimal equityStart,
      BigDecimal equityEnd,
      BigDecimal realEstateStart,
      BigDecimal realEstateEnd,
      BigDecimal otherStart,
      BigDecimal otherEnd,
      BigDecimal manualLiquidReserveStart,
      BigDecimal manualLiquidReserveEnd,
      BigDecimal contractualAssetsStart,
      BigDecimal contractualAssetsEnd,
      BigDecimal spendableAssetsEnd,
      BigDecimal financialAssetsEnd,
      BigDecimal totalLiquidAssets,
      BigDecimal totalIlliquidAssets,
      BigDecimal endNetWorth,
      boolean failed,
      BigDecimal unfundedAmount) {
    this(
        age,
        year,
        startNetWorth,
        coreExpenses,
        discretionaryExpenses,
        eventExpenses,
        totalExpenses,
        passiveIncome,
        pensionIncome,
        eventIncome,
        totalIncome,
        requiredPortfolioFunding,
        actualPortfolioWithdrawal,
        manualLiquidReserveWithdrawal,
        recurringFundingGap,
        safeReserveStart,
        safeReserveTarget,
        safeReserveEnd,
        safeReserveCoverageYears,
        equityReturnRate,
        equityGain,
        equityToFixedIncomeTransfer,
        emergencyEquityWithdrawal,
        cashStart,
        cashEnd,
        fixedIncomeStart,
        fixedIncomeEnd,
        equityStart,
        equityEnd,
        realEstateStart,
        realEstateEnd,
        otherStart,
        otherEnd,
        manualLiquidReserveStart,
        manualLiquidReserveEnd,
        contractualAssetsStart,
        contractualAssetsEnd,
        spendableAssetsEnd,
        financialAssetsEnd,
        totalLiquidAssets,
        totalIlliquidAssets,
        endNetWorth,
        failed,
        unfundedAmount,
        SimulationLifecyclePhase.RETIRED,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        false,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO);
  }

  /**
   * Compatibility constructor for callers built before contractual assets were reported separately.
   */
  public SimulationYear(
      int age,
      int year,
      BigDecimal startNetWorth,
      BigDecimal coreExpenses,
      BigDecimal discretionaryExpenses,
      BigDecimal eventExpenses,
      BigDecimal totalExpenses,
      BigDecimal passiveIncome,
      BigDecimal pensionIncome,
      BigDecimal eventIncome,
      BigDecimal totalIncome,
      BigDecimal requiredPortfolioWithdrawal,
      BigDecimal cashStart,
      BigDecimal cashEnd,
      BigDecimal fixedIncomeStart,
      BigDecimal fixedIncomeEnd,
      BigDecimal equityStart,
      BigDecimal equityEnd,
      BigDecimal realEstateStart,
      BigDecimal realEstateEnd,
      BigDecimal otherStart,
      BigDecimal otherEnd,
      BigDecimal totalLiquidAssets,
      BigDecimal totalIlliquidAssets,
      BigDecimal endNetWorth,
      boolean failed,
      BigDecimal unfundedAmount) {
    this(
        age,
        year,
        startNetWorth,
        coreExpenses,
        discretionaryExpenses,
        eventExpenses,
        totalExpenses,
        passiveIncome,
        pensionIncome,
        eventIncome,
        totalIncome,
        requiredPortfolioWithdrawal,
        requiredPortfolioWithdrawal.subtract(unfundedAmount).max(BigDecimal.ZERO),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        cashStart,
        cashEnd,
        fixedIncomeStart,
        fixedIncomeEnd,
        equityStart,
        equityEnd,
        realEstateStart,
        realEstateEnd,
        otherStart,
        otherEnd,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        totalLiquidAssets,
        totalLiquidAssets,
        totalLiquidAssets,
        totalIlliquidAssets,
        endNetWorth,
        failed,
        unfundedAmount,
        SimulationLifecyclePhase.RETIRED,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        false,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO);
  }

  public BigDecimal livingExpenses() {
    return coreExpenses;
  }

  /**
   * @deprecated Use {@link #requiredPortfolioFunding()}.
   */
  @Deprecated
  public BigDecimal requiredPortfolioWithdrawal() {
    return requiredPortfolioFunding;
  }

  /**
   * @deprecated Events, not a recurring simulation assumption.
   */
  @Deprecated
  public BigDecimal oneOffExpenses() {
    return eventExpenses;
  }

  /**
   * @deprecated Events, not a recurring simulation assumption.
   */
  @Deprecated
  public BigDecimal oneOffIncome() {
    return eventIncome;
  }

  /** Assets that can fund spending under the active strategy. */
  public BigDecimal spendableLiquidAssets() {
    return spendableAssetsEnd;
  }

  /**
   * @deprecated Ambiguous historic name; use {@link #spendableAssetsEnd()} or {@link
   *     #financialAssetsEnd()}.
   */
  @Deprecated
  public BigDecimal totalLiquidAssets() {
    return spendableAssetsEnd;
  }

  public BigDecimal lockedContractualAssets() {
    return contractualAssetsEnd;
  }
}
