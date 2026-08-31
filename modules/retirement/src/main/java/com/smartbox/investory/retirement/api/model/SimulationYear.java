package com.smartbox.investory.retirement.api.model;

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

  /** Canonical aggregate-bucket timeline row used by the current simulator. */
  public static SimulationYear bucket(
      int age,
      int year,
      boolean retired,
      BigDecimal expenses,
      BigDecimal eventExpenses,
      BigDecimal employment,
      BigDecimal pension,
      BigDecimal eventIncome,
      BigDecimal rental,
      BigDecimal cashIncome,
      BucketResult cash,
      BucketResult bonds,
      BucketResult equities,
      BucketResult realEstate,
      BigDecimal unfunded,
      BigDecimal contribution) {
    return bucket(
        age,
        year,
        retired,
        expenses,
        eventExpenses,
        employment,
        pension,
        eventIncome,
        rental,
        cashIncome,
        BigDecimal.ZERO,
        cash,
        bonds,
        equities,
        realEstate,
        unfunded,
        contribution);
  }

  /** Canonical bucket row with separate bond cash income. */
  public static SimulationYear bucket(
      int age,
      int year,
      boolean retired,
      BigDecimal expenses,
      BigDecimal eventExpenses,
      BigDecimal employment,
      BigDecimal pension,
      BigDecimal eventIncome,
      BigDecimal rental,
      BigDecimal cashIncome,
      BigDecimal bondIncome,
      BucketResult cash,
      BucketResult bonds,
      BucketResult equities,
      BucketResult realEstate,
      BigDecimal unfunded,
      BigDecimal contribution) {
    BigDecimal totalExpenses = expenses.add(eventExpenses);
    BigDecimal withdrawals =
        cash.withdrawal()
            .add(bonds.withdrawal())
            .add(equities.withdrawal())
            .add(realEstate.withdrawal());
    BigDecimal endLiquid =
        cash.expectedEndValue().add(bonds.expectedEndValue()).add(equities.expectedEndValue());
    BigDecimal endNetWorth = endLiquid.add(realEstate.expectedEndValue());
    BigDecimal gap = totalExpenses.subtract(cashIncome).max(BigDecimal.ZERO);
    return new SimulationYear(
        age,
        year,
        endNetWorth
            .subtract(cash.expectedEndValue())
            .subtract(bonds.expectedEndValue())
            .subtract(equities.expectedEndValue())
            .subtract(realEstate.expectedEndValue())
            .add(cash.startValue())
            .add(bonds.startValue())
            .add(equities.startValue())
            .add(realEstate.startValue()),
        retired ? expenses : BigDecimal.ZERO,
        BigDecimal.ZERO,
        eventExpenses,
        totalExpenses,
        rental.add(bondIncome),
        pension,
        eventIncome,
        cashIncome,
        gap,
        withdrawals,
        cash.withdrawal(),
        gap,
        cash.startValue(),
        BigDecimal.ZERO,
        cash.expectedEndValue(),
        BigDecimal.ZERO,
        equities.startValue().signum() == 0
            ? BigDecimal.ZERO
            : equities
                .returnAmount()
                .divide(equities.startValue(), 12, java.math.RoundingMode.HALF_UP),
        equities.returnAmount(),
        equities.refill().negate().max(BigDecimal.ZERO),
        equities.withdrawal(),
        cash.startValue(),
        cash.expectedEndValue(),
        bonds.startValue(),
        bonds.expectedEndValue(),
        equities.startValue(),
        equities.expectedEndValue(),
        realEstate.startValue(),
        realEstate.expectedEndValue(),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        endLiquid,
        endLiquid,
        endLiquid,
        realEstate.expectedEndValue(),
        endNetWorth,
        unfunded.signum() > 0,
        unfunded,
        retired ? SimulationLifecyclePhase.RETIRED : SimulationLifecyclePhase.WORKING,
        employment,
        contribution,
        false,
        rental,
        gap,
        bonds.expectedEndValue(),
        bondIncome,
        new SimulationFunding(
            gap,
            cash.startValue(),
            BigDecimal.ZERO,
            cash.withdrawal(),
            cash.expectedEndValue(),
            bonds.withdrawal(),
            bonds.expectedEndValue(),
            equities.startValue(),
            equities.returnAmount(),
            equities.withdrawal(),
            equities.expectedEndValue(),
            equities.refill().negate().max(BigDecimal.ZERO),
            unfunded,
            bonds.returnAmount()));
  }

  /** Spendable fixed-income cash paid by Long-Term; capitalized return is separate. */
  public BigDecimal bondCashIncome() {
    return bondIncome;
  }

  /** Long-Term bond return retained in capital, never spendable cash income. */
  public BigDecimal capitalizedBondReturn() {
    return funding.capitalizedBondReturn();
  }
}
