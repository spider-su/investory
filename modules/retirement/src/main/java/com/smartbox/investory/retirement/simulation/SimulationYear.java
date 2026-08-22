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
    BigDecimal bondIncome,
    SimulationFunding funding) {

  /** Spendable fixed-income cash paid by Long-Term; capitalized return is separate. */
  public BigDecimal bondCashIncome() {
    return bondIncome;
  }

  /** Long-Term bond return retained in capital, never spendable cash income. */
  public BigDecimal capitalizedBondReturn() {
    return funding.capitalizedBondReturn();
  }
  /**
   * Creates the compatibility-shaped timeline row from the generic orchestrator result.
   * Asset-specific legacy buckets remain zero; reserve and Investment are the only
   * spendable sources represented by the canonical simulator.
   */
  public static SimulationYear generic(
      int age, int year, boolean retired, BigDecimal expenses, BigDecimal eventExpenses,
      BigDecimal employmentIncome, BigDecimal pensionIncome, BigDecimal eventIncome,
      BigDecimal rentalIncome, BigDecimal bondIncome, BigDecimal reserveStart,
      BigDecimal reserveWithdrawal, BigDecimal reserveEnd, BigDecimal investmentStart,
      BigDecimal investmentReturn, BigDecimal investmentWithdrawal, BigDecimal investmentEnd,
      BigDecimal unfundedAmount, BigDecimal preRetirementContribution) {
    return generic(age, year, retired, expenses, eventExpenses, employmentIncome, pensionIncome,
        eventIncome, rentalIncome, bondIncome, reserveStart, reserveWithdrawal, reserveEnd,
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, investmentStart, investmentReturn,
        investmentWithdrawal, investmentEnd, unfundedAmount, preRetirementContribution);
  }

  public static SimulationYear generic(
      int age, int year, boolean retired, BigDecimal expenses, BigDecimal eventExpenses,
      BigDecimal employmentIncome, BigDecimal pensionIncome, BigDecimal eventIncome,
      BigDecimal rentalIncome, BigDecimal bondIncome, BigDecimal reserveStart,
      BigDecimal reserveWithdrawal, BigDecimal reserveEnd, BigDecimal reserveTransfer,
      BigDecimal longTermFunding, BigDecimal longTermCapitalEnd, BigDecimal investmentStart,
      BigDecimal investmentReturn, BigDecimal investmentWithdrawal, BigDecimal investmentEnd,
      BigDecimal unfundedAmount, BigDecimal preRetirementContribution) {
    return generic(age, year, retired, expenses, eventExpenses, employmentIncome, pensionIncome,
        eventIncome, rentalIncome, bondIncome, reserveStart, reserveWithdrawal, reserveEnd,
        reserveTransfer, longTermFunding, longTermCapitalEnd, investmentStart, investmentReturn,
        investmentWithdrawal, investmentEnd, unfundedAmount, preRetirementContribution, BigDecimal.ZERO);
  }

  public static SimulationYear generic(
      int age,
      int year,
      boolean retired,
      BigDecimal expenses,
      BigDecimal eventExpenses,
      BigDecimal employmentIncome,
      BigDecimal pensionIncome,
      BigDecimal eventIncome,
      BigDecimal rentalIncome,
      BigDecimal bondIncome,
      BigDecimal reserveStart,
      BigDecimal reserveWithdrawal,
      BigDecimal reserveEnd,
      BigDecimal reserveTransfer,
      BigDecimal longTermFunding,
      BigDecimal longTermCapitalEnd,
      BigDecimal investmentStart,
      BigDecimal investmentReturn,
      BigDecimal investmentWithdrawal,
      BigDecimal investmentEnd,
      BigDecimal unfundedAmount,
      BigDecimal preRetirementContribution,
      BigDecimal equityHarvestToReserve) {
    return generic(age, year, retired, expenses, eventExpenses, employmentIncome, pensionIncome,
        eventIncome, rentalIncome, bondIncome, reserveStart, reserveWithdrawal, reserveEnd,
        reserveTransfer, longTermFunding, longTermCapitalEnd, investmentStart, investmentReturn,
        investmentWithdrawal, investmentEnd, unfundedAmount, preRetirementContribution,
        equityHarvestToReserve, BigDecimal.ZERO);
  }

  public static SimulationYear generic(
      int age, int year, boolean retired, BigDecimal expenses, BigDecimal eventExpenses,
      BigDecimal employmentIncome, BigDecimal pensionIncome, BigDecimal eventIncome,
      BigDecimal rentalIncome, BigDecimal bondIncome, BigDecimal reserveStart,
      BigDecimal reserveWithdrawal, BigDecimal reserveEnd, BigDecimal reserveTransfer,
      BigDecimal longTermFunding, BigDecimal longTermCapitalEnd, BigDecimal investmentStart,
      BigDecimal investmentReturn, BigDecimal investmentWithdrawal, BigDecimal investmentEnd,
      BigDecimal unfundedAmount, BigDecimal preRetirementContribution,
      BigDecimal equityHarvestToReserve, BigDecimal capitalizedBondReturn) {
    BigDecimal totalExpenses = expenses.add(eventExpenses);
    BigDecimal passiveIncome = rentalIncome.add(bondIncome);
    BigDecimal totalIncome = passiveIncome.add(pensionIncome).add(employmentIncome).add(eventIncome);
    BigDecimal netCashFlow = totalIncome.subtract(totalExpenses);
    BigDecimal gap = netCashFlow.negate().max(BigDecimal.ZERO);
    BigDecimal spendableEnd = reserveEnd.add(investmentEnd);
    return new SimulationYear(
        age, year, reserveStart.add(investmentStart),
        retired ? expenses : BigDecimal.ZERO, BigDecimal.ZERO, eventExpenses, totalExpenses,
        passiveIncome, pensionIncome, eventIncome, totalIncome,
        gap, reserveWithdrawal.add(investmentWithdrawal), reserveWithdrawal, gap,
        reserveStart, BigDecimal.ZERO, reserveEnd, BigDecimal.ZERO,
        BigDecimal.ZERO, investmentReturn, BigDecimal.ZERO, BigDecimal.ZERO,
        reserveStart, reserveEnd, BigDecimal.ZERO, BigDecimal.ZERO,
        investmentStart, investmentEnd, BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, reserveStart, reserveEnd,
        BigDecimal.ZERO, BigDecimal.ZERO, spendableEnd, spendableEnd,
        spendableEnd, BigDecimal.ZERO, spendableEnd, unfundedAmount.signum() > 0,
        unfundedAmount, retired ? SimulationLifecyclePhase.RETIRED : SimulationLifecyclePhase.WORKING,
        employmentIncome, preRetirementContribution, false,
        rentalIncome,
        gap,
        BigDecimal.ZERO,
        bondIncome,
        new SimulationFunding(
            gap,
            reserveStart,
            reserveTransfer,
            reserveWithdrawal,
            reserveEnd,
            longTermFunding,
            longTermCapitalEnd,
            investmentStart,
            investmentReturn,
            investmentWithdrawal,
            investmentEnd,
            equityHarvestToReserve,
            unfundedAmount,
            capitalizedBondReturn));
  }

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
        BigDecimal.ZERO,
        SimulationFunding.legacy());
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
        BigDecimal.ZERO,
        SimulationFunding.legacy());
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
