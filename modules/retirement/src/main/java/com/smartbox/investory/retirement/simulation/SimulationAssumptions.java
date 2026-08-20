package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.retirement.profile.InvestmentProfile;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;

public record SimulationAssumptions(
    int currentAge,
    int endAge,
    BigDecimal annualLivingExpenses,
    BigDecimal inflationRate,
    BigDecimal cashReturnRate,
    BigDecimal fixedIncomeReturnRate,
    BigDecimal equityReturnRate,
    BigDecimal realEstateReturnRate,
    BigDecimal otherReturnRate,
    int pensionStartAge,
    BigDecimal annualPension,
    BigDecimal capitalGainTaxRate,
    int startYear,
    BigDecimal annualDiscretionaryExpenses,
    List<SimulationEvent> futureEvents,
    BigDecimal rentalIncomeGrowthRate,
    BigDecimal spendingGrowthRate,
    SimulationFundingStrategy fundingStrategy,
    BigDecimal safeReserveYears,
    BigDecimal equityHarvestMinimumReturnRate,
    BigDecimal equityGainHarvestRate,
    boolean allowEmergencyEquityWithdrawal,
    int retirementAge,
    BigDecimal annualEmploymentIncome,
    BigDecimal annualPreRetirementContribution,
    List<FundingSource> fundingOrder,
    ExpenseProfile expenseProfile) {
  public static final BigDecimal DEFAULT_RENTAL_INCOME_GROWTH_RATE = new BigDecimal("0.020");
  public static final BigDecimal DEFAULT_SPENDING_GROWTH_RATE = new BigDecimal("0.025");
  public static final BigDecimal DEFAULT_SAFE_RESERVE_YEARS = new BigDecimal("5");
  public static final BigDecimal DEFAULT_EQUITY_HARVEST_MINIMUM_RETURN_RATE =
      new BigDecimal("0.07");
  public static final BigDecimal DEFAULT_EQUITY_GAIN_HARVEST_RATE = new BigDecimal("0.75");
  public static final List<FundingSource> DEFAULT_FUNDING_ORDER =
      List.of(FundingSource.CASH, FundingSource.BONDS, FundingSource.STOCKS);

  /** Compatibility constructor for callers that predate configurable funding order. */
  public SimulationAssumptions(
      int currentAge,
      int endAge,
      BigDecimal annualLivingExpenses,
      BigDecimal inflationRate,
      BigDecimal cashReturnRate,
      BigDecimal fixedIncomeReturnRate,
      BigDecimal equityReturnRate,
      BigDecimal realEstateReturnRate,
      BigDecimal otherReturnRate,
      int pensionStartAge,
      BigDecimal annualPension,
      BigDecimal capitalGainTaxRate,
      int startYear,
      BigDecimal annualDiscretionaryExpenses,
      List<SimulationEvent> futureEvents,
      BigDecimal rentalIncomeGrowthRate,
      BigDecimal spendingGrowthRate,
      SimulationFundingStrategy fundingStrategy,
      BigDecimal safeReserveYears,
      BigDecimal equityHarvestMinimumReturnRate,
      BigDecimal equityGainHarvestRate,
      boolean allowEmergencyEquityWithdrawal,
      int retirementAge,
      BigDecimal annualEmploymentIncome,
      BigDecimal annualPreRetirementContribution) {
    this(
        currentAge,
        endAge,
        annualLivingExpenses,
        inflationRate,
        cashReturnRate,
        fixedIncomeReturnRate,
        equityReturnRate,
        realEstateReturnRate,
        otherReturnRate,
        pensionStartAge,
        annualPension,
        capitalGainTaxRate,
        startYear,
        annualDiscretionaryExpenses,
        futureEvents,
        rentalIncomeGrowthRate,
        spendingGrowthRate,
        fundingStrategy,
        safeReserveYears,
        equityHarvestMinimumReturnRate,
        equityGainHarvestRate,
        allowEmergencyEquityWithdrawal,
        retirementAge,
        annualEmploymentIncome,
        annualPreRetirementContribution,
        DEFAULT_FUNDING_ORDER,
        ExpenseProfile.EMPTY);
  }

  /** Compatibility constructor for plans created before retirement-transition fields existed. */
  public SimulationAssumptions(
      int currentAge,
      int endAge,
      BigDecimal annualLivingExpenses,
      BigDecimal inflationRate,
      BigDecimal cashReturnRate,
      BigDecimal fixedIncomeReturnRate,
      BigDecimal equityReturnRate,
      BigDecimal realEstateReturnRate,
      BigDecimal otherReturnRate,
      int pensionStartAge,
      BigDecimal annualPension,
      BigDecimal capitalGainTaxRate,
      int startYear,
      BigDecimal annualDiscretionaryExpenses,
      List<SimulationEvent> futureEvents,
      BigDecimal rentalIncomeGrowthRate,
      BigDecimal spendingGrowthRate,
      SimulationFundingStrategy fundingStrategy,
      BigDecimal safeReserveYears,
      BigDecimal equityHarvestMinimumReturnRate,
      BigDecimal equityGainHarvestRate,
      boolean allowEmergencyEquityWithdrawal) {
    this(
        currentAge,
        endAge,
        annualLivingExpenses,
        inflationRate,
        cashReturnRate,
        fixedIncomeReturnRate,
        equityReturnRate,
        realEstateReturnRate,
        otherReturnRate,
        pensionStartAge,
        annualPension,
        capitalGainTaxRate,
        startYear,
        annualDiscretionaryExpenses,
        futureEvents,
        rentalIncomeGrowthRate,
        spendingGrowthRate,
        fundingStrategy,
        safeReserveYears,
        equityHarvestMinimumReturnRate,
        equityGainHarvestRate,
        allowEmergencyEquityWithdrawal,
        currentAge,
        BigDecimal.ZERO,
        BigDecimal.ZERO);
  }

  public SimulationAssumptions(
      int currentAge,
      int endAge,
      BigDecimal annualLivingExpenses,
      BigDecimal inflationRate,
      BigDecimal cashReturnRate,
      BigDecimal fixedIncomeReturnRate,
      BigDecimal equityReturnRate,
      BigDecimal realEstateReturnRate,
      BigDecimal otherReturnRate,
      int pensionStartAge,
      BigDecimal annualPension,
      BigDecimal capitalGainTaxRate) {
    this(
        currentAge,
        endAge,
        annualLivingExpenses,
        inflationRate,
        cashReturnRate,
        fixedIncomeReturnRate,
        equityReturnRate,
        realEstateReturnRate,
        otherReturnRate,
        pensionStartAge,
        annualPension,
        capitalGainTaxRate,
        2026);
  }

  public SimulationAssumptions(
      int currentAge,
      int endAge,
      BigDecimal annualLivingExpenses,
      BigDecimal inflationRate,
      BigDecimal cashReturnRate,
      BigDecimal fixedIncomeReturnRate,
      BigDecimal equityReturnRate,
      BigDecimal realEstateReturnRate,
      BigDecimal otherReturnRate,
      int pensionStartAge,
      BigDecimal annualPension,
      BigDecimal capitalGainTaxRate,
      int startYear) {
    this(
        currentAge,
        endAge,
        annualLivingExpenses,
        inflationRate,
        cashReturnRate,
        fixedIncomeReturnRate,
        equityReturnRate,
        realEstateReturnRate,
        otherReturnRate,
        pensionStartAge,
        annualPension,
        capitalGainTaxRate,
        startYear,
        BigDecimal.ZERO,
        List.of());
  }

  public SimulationAssumptions(
      int currentAge,
      int endAge,
      BigDecimal annualLivingExpenses,
      BigDecimal inflationRate,
      BigDecimal cashReturnRate,
      BigDecimal fixedIncomeReturnRate,
      BigDecimal equityReturnRate,
      BigDecimal realEstateReturnRate,
      BigDecimal otherReturnRate,
      int pensionStartAge,
      BigDecimal annualPension,
      BigDecimal capitalGainTaxRate,
      int startYear,
      BigDecimal annualDiscretionaryExpenses,
      List<SimulationEvent> futureEvents) {
    this(
        currentAge,
        endAge,
        annualLivingExpenses,
        inflationRate,
        cashReturnRate,
        fixedIncomeReturnRate,
        equityReturnRate,
        realEstateReturnRate,
        otherReturnRate,
        pensionStartAge,
        annualPension,
        capitalGainTaxRate,
        startYear,
        annualDiscretionaryExpenses,
        futureEvents,
        DEFAULT_RENTAL_INCOME_GROWTH_RATE,
        inflationRate);
  }

  /**
   * Compatibility constructor. The supplied inflation is copied once as the spending-growth
   * default.
   */
  public SimulationAssumptions(
      int currentAge,
      int endAge,
      BigDecimal annualLivingExpenses,
      BigDecimal inflationRate,
      BigDecimal cashReturnRate,
      BigDecimal fixedIncomeReturnRate,
      BigDecimal equityReturnRate,
      BigDecimal realEstateReturnRate,
      BigDecimal otherReturnRate,
      int pensionStartAge,
      BigDecimal annualPension,
      BigDecimal capitalGainTaxRate,
      int startYear,
      BigDecimal annualDiscretionaryExpenses,
      List<SimulationEvent> futureEvents,
      BigDecimal rentalIncomeGrowthRate) {
    this(
        currentAge,
        endAge,
        annualLivingExpenses,
        inflationRate,
        cashReturnRate,
        fixedIncomeReturnRate,
        equityReturnRate,
        realEstateReturnRate,
        otherReturnRate,
        pensionStartAge,
        annualPension,
        capitalGainTaxRate,
        startYear,
        annualDiscretionaryExpenses,
        futureEvents,
        rentalIncomeGrowthRate,
        inflationRate);
  }

  /** Compatibility constructor. Existing callers keep the historic simple waterfall policy. */
  public SimulationAssumptions(
      int currentAge,
      int endAge,
      BigDecimal annualLivingExpenses,
      BigDecimal inflationRate,
      BigDecimal cashReturnRate,
      BigDecimal fixedIncomeReturnRate,
      BigDecimal equityReturnRate,
      BigDecimal realEstateReturnRate,
      BigDecimal otherReturnRate,
      int pensionStartAge,
      BigDecimal annualPension,
      BigDecimal capitalGainTaxRate,
      int startYear,
      BigDecimal annualDiscretionaryExpenses,
      List<SimulationEvent> futureEvents,
      BigDecimal rentalIncomeGrowthRate,
      BigDecimal spendingGrowthRate) {
    this(
        currentAge,
        endAge,
        annualLivingExpenses,
        inflationRate,
        cashReturnRate,
        fixedIncomeReturnRate,
        equityReturnRate,
        realEstateReturnRate,
        otherReturnRate,
        pensionStartAge,
        annualPension,
        capitalGainTaxRate,
        startYear,
        annualDiscretionaryExpenses,
        futureEvents,
        rentalIncomeGrowthRate,
        spendingGrowthRate,
        SimulationFundingStrategy.SIMPLE_WATERFALL,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        true);
  }

  public SimulationAssumptions {
    if (currentAge < 0 || endAge < currentAge)
      throw new IllegalArgumentException("Invalid simulation ages");
    // A rebased forward context may begin after the absolute retirement age.
    if (retirementAge < 0 || retirementAge > endAge)
      throw new IllegalArgumentException("Invalid retirement age");
    for (BigDecimal rate :
        new BigDecimal[] {
          inflationRate,
          rentalIncomeGrowthRate,
          spendingGrowthRate,
          cashReturnRate,
          fixedIncomeReturnRate,
          equityReturnRate,
          realEstateReturnRate,
          otherReturnRate,
          capitalGainTaxRate,
          equityHarvestMinimumReturnRate
        })
      if (rate == null || rate.compareTo(BigDecimal.ONE.negate()) < 0)
        throw new IllegalArgumentException("Invalid simulation rate");
    if (fundingStrategy == null
        || safeReserveYears == null
        || safeReserveYears.signum() < 0
        || equityGainHarvestRate == null
        || equityGainHarvestRate.signum() < 0
        || equityGainHarvestRate.compareTo(BigDecimal.ONE) > 0
        || fundingOrder == null
        || fundingOrder.isEmpty()
        || fundingOrder.stream().anyMatch(source -> source == null)
        || new HashSet<>(fundingOrder).size() != fundingOrder.size()
        || expenseProfile == null)
      throw new IllegalArgumentException("Invalid simulation funding strategy");
    if (annualLivingExpenses == null
        || annualLivingExpenses.signum() < 0
        || annualDiscretionaryExpenses == null
        || annualDiscretionaryExpenses.signum() < 0
        || annualPension == null
        || annualPension.signum() < 0
        || annualEmploymentIncome == null
        || annualEmploymentIncome.signum() < 0
        || annualPreRetirementContribution == null
        || annualPreRetirementContribution.signum() < 0
        || futureEvents == null) throw new IllegalArgumentException("Invalid simulation cash flow");
    futureEvents = List.copyOf(futureEvents);
    fundingOrder = List.copyOf(fundingOrder);
  }

  public static SimulationAssumptions defaults(
      InvestmentProfile profile, int currentAge, int endAge) {
    return defaults(profile, currentAge, endAge, 2026);
  }

  public static SimulationAssumptions defaults(
      InvestmentProfile profile, int currentAge, int endAge, int startYear) {
    return new SimulationAssumptions(
        currentAge,
        endAge,
        new BigDecimal("180000"),
        new BigDecimal("0.025"),
        new BigDecimal("0.020"),
        new BigDecimal("0.040"),
        new BigDecimal("0.060"),
        new BigDecimal("0.025"),
        new BigDecimal("0.030"),
        Integer.MAX_VALUE,
        BigDecimal.ZERO,
        new BigDecimal("0.19"),
        startYear,
        BigDecimal.ZERO,
        List.of(),
        DEFAULT_RENTAL_INCOME_GROWTH_RATE,
        DEFAULT_SPENDING_GROWTH_RATE,
        SimulationFundingStrategy.RESERVE_AND_HARVEST,
        DEFAULT_SAFE_RESERVE_YEARS,
        DEFAULT_EQUITY_HARVEST_MINIMUM_RETURN_RATE,
        DEFAULT_EQUITY_GAIN_HARVEST_RATE,
        true,
        currentAge,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        DEFAULT_FUNDING_ORDER,
        ExpenseProfile.EMPTY);
  }

  /** Derive recurring spending while preserving the current living/discretionary proportion. */
  public SimulationAssumptions withRecurringSpending(BigDecimal recurringSpending) {
    if (recurringSpending == null || recurringSpending.signum() < 0)
      throw new IllegalArgumentException("Recurring spending cannot be negative");
    BigDecimal current = annualLivingExpenses.add(annualDiscretionaryExpenses);
    BigDecimal living;
    if (current.signum() == 0) {
      living = recurringSpending;
    } else {
      living =
          recurringSpending
              .multiply(annualLivingExpenses)
              .divide(current, 12, java.math.RoundingMode.HALF_UP);
    }
    return new SimulationAssumptions(
        currentAge,
        endAge,
        living,
        inflationRate,
        cashReturnRate,
        fixedIncomeReturnRate,
        equityReturnRate,
        realEstateReturnRate,
        otherReturnRate,
        pensionStartAge,
        annualPension,
        capitalGainTaxRate,
        startYear,
        recurringSpending.subtract(living),
        futureEvents,
        rentalIncomeGrowthRate,
        spendingGrowthRate,
        fundingStrategy,
        safeReserveYears,
        equityHarvestMinimumReturnRate,
        equityGainHarvestRate,
        allowEmergencyEquityWithdrawal,
        retirementAge,
        annualEmploymentIncome,
        annualPreRetirementContribution,
        fundingOrder,
        expenseProfile);
  }

  public SimulationAssumptions withSpendingGrowthRate(BigDecimal value) {
    return copy(
        value,
        rentalIncomeGrowthRate,
        equityReturnRate,
        fixedIncomeReturnRate,
        realEstateReturnRate,
        safeReserveYears,
        annualPension);
  }

  public SimulationAssumptions withRentalIncomeGrowthRate(BigDecimal value) {
    return copy(
        spendingGrowthRate,
        value,
        equityReturnRate,
        fixedIncomeReturnRate,
        realEstateReturnRate,
        safeReserveYears,
        annualPension);
  }

  public SimulationAssumptions withEquityReturnRate(BigDecimal value) {
    return copy(
        spendingGrowthRate,
        rentalIncomeGrowthRate,
        value,
        fixedIncomeReturnRate,
        realEstateReturnRate,
        safeReserveYears,
        annualPension);
  }

  public SimulationAssumptions withFixedIncomeReturnRate(BigDecimal value) {
    return copy(
        spendingGrowthRate,
        rentalIncomeGrowthRate,
        equityReturnRate,
        value,
        realEstateReturnRate,
        safeReserveYears,
        annualPension);
  }

  public SimulationAssumptions withRealEstateReturnRate(BigDecimal value) {
    return copy(
        spendingGrowthRate,
        rentalIncomeGrowthRate,
        equityReturnRate,
        fixedIncomeReturnRate,
        value,
        safeReserveYears,
        annualPension);
  }

  public SimulationAssumptions withSafeReserveYears(BigDecimal value) {
    return copy(
        spendingGrowthRate,
        rentalIncomeGrowthRate,
        equityReturnRate,
        fixedIncomeReturnRate,
        realEstateReturnRate,
        value,
        annualPension);
  }

  public SimulationAssumptions withAnnualPension(BigDecimal value) {
    return copy(
        spendingGrowthRate,
        rentalIncomeGrowthRate,
        equityReturnRate,
        fixedIncomeReturnRate,
        realEstateReturnRate,
        safeReserveYears,
        value);
  }

  public SimulationAssumptions withAnnualEmploymentIncome(BigDecimal value) {
    return transitionCopy(retirementAge, value, annualPreRetirementContribution);
  }

  public SimulationAssumptions withAnnualPreRetirementContribution(BigDecimal value) {
    return transitionCopy(retirementAge, annualEmploymentIncome, value);
  }

  public SimulationAssumptions withRetirementAge(int value) {
    return transitionCopy(value, annualEmploymentIncome, annualPreRetirementContribution);
  }

  /** Rebase the temporal boundary without changing economic assumptions. */
  public SimulationAssumptions rebasedTo(
      int rebasedCurrentAge, int rebasedStartYear, List<SimulationEvent> remainingEvents) {
    return new SimulationAssumptions(
        rebasedCurrentAge,
        endAge,
        annualLivingExpenses,
        inflationRate,
        cashReturnRate,
        fixedIncomeReturnRate,
        equityReturnRate,
        realEstateReturnRate,
        otherReturnRate,
        pensionStartAge,
        annualPension,
        capitalGainTaxRate,
        rebasedStartYear,
        annualDiscretionaryExpenses,
        remainingEvents,
        rentalIncomeGrowthRate,
        spendingGrowthRate,
        fundingStrategy,
        safeReserveYears,
        equityHarvestMinimumReturnRate,
        equityGainHarvestRate,
        allowEmergencyEquityWithdrawal,
        retirementAge,
        annualEmploymentIncome,
        annualPreRetirementContribution,
        fundingOrder,
        expenseProfile.rebasedAt(rebasedStartYear - startYear));
  }

  public SimulationAssumptions withFundingStrategy(SimulationFundingStrategy value) {
    return new SimulationAssumptions(
        currentAge,
        endAge,
        annualLivingExpenses,
        inflationRate,
        cashReturnRate,
        fixedIncomeReturnRate,
        equityReturnRate,
        realEstateReturnRate,
        otherReturnRate,
        pensionStartAge,
        annualPension,
        capitalGainTaxRate,
        startYear,
        annualDiscretionaryExpenses,
        futureEvents,
        rentalIncomeGrowthRate,
        spendingGrowthRate,
        value,
        safeReserveYears,
        equityHarvestMinimumReturnRate,
        equityGainHarvestRate,
        allowEmergencyEquityWithdrawal,
        retirementAge,
        annualEmploymentIncome,
        annualPreRetirementContribution,
        fundingOrder,
        expenseProfile);
  }

  public SimulationAssumptions withFundingOrder(List<FundingSource> value) {
    return new SimulationAssumptions(
        currentAge,
        endAge,
        annualLivingExpenses,
        inflationRate,
        cashReturnRate,
        fixedIncomeReturnRate,
        equityReturnRate,
        realEstateReturnRate,
        otherReturnRate,
        pensionStartAge,
        annualPension,
        capitalGainTaxRate,
        startYear,
        annualDiscretionaryExpenses,
        futureEvents,
        rentalIncomeGrowthRate,
        spendingGrowthRate,
        fundingStrategy,
        safeReserveYears,
        equityHarvestMinimumReturnRate,
        equityGainHarvestRate,
        allowEmergencyEquityWithdrawal,
        retirementAge,
        annualEmploymentIncome,
        annualPreRetirementContribution,
        value,
        expenseProfile);
  }

  public SimulationAssumptions withExpenseProfile(ExpenseProfile value) {
    return new SimulationAssumptions(
        currentAge,
        endAge,
        annualLivingExpenses,
        inflationRate,
        cashReturnRate,
        fixedIncomeReturnRate,
        equityReturnRate,
        realEstateReturnRate,
        otherReturnRate,
        pensionStartAge,
        annualPension,
        capitalGainTaxRate,
        startYear,
        annualDiscretionaryExpenses,
        futureEvents,
        rentalIncomeGrowthRate,
        spendingGrowthRate,
        fundingStrategy,
        safeReserveYears,
        equityHarvestMinimumReturnRate,
        equityGainHarvestRate,
        allowEmergencyEquityWithdrawal,
        retirementAge,
        annualEmploymentIncome,
        annualPreRetirementContribution,
        fundingOrder,
        value);
  }

  private SimulationAssumptions copy(
      BigDecimal spendingGrowth,
      BigDecimal rentalGrowth,
      BigDecimal equityReturn,
      BigDecimal fixedIncomeReturn,
      BigDecimal realEstateReturn,
      BigDecimal reserveYears,
      BigDecimal pension) {
    return new SimulationAssumptions(
        currentAge,
        endAge,
        annualLivingExpenses,
        inflationRate,
        cashReturnRate,
        fixedIncomeReturn,
        equityReturn,
        realEstateReturn,
        otherReturnRate,
        pensionStartAge,
        pension,
        capitalGainTaxRate,
        startYear,
        annualDiscretionaryExpenses,
        futureEvents,
        rentalGrowth,
        spendingGrowth,
        fundingStrategy,
        reserveYears,
        equityHarvestMinimumReturnRate,
        equityGainHarvestRate,
        allowEmergencyEquityWithdrawal,
        retirementAge,
        annualEmploymentIncome,
        annualPreRetirementContribution,
        fundingOrder,
        expenseProfile);
  }

  private SimulationAssumptions transitionCopy(
      int retirement, BigDecimal employmentIncome, BigDecimal contribution) {
    return new SimulationAssumptions(
        currentAge,
        endAge,
        annualLivingExpenses,
        inflationRate,
        cashReturnRate,
        fixedIncomeReturnRate,
        equityReturnRate,
        realEstateReturnRate,
        otherReturnRate,
        pensionStartAge,
        annualPension,
        capitalGainTaxRate,
        startYear,
        annualDiscretionaryExpenses,
        futureEvents,
        rentalIncomeGrowthRate,
        spendingGrowthRate,
        fundingStrategy,
        safeReserveYears,
        equityHarvestMinimumReturnRate,
        equityGainHarvestRate,
        allowEmergencyEquityWithdrawal,
        retirement,
        employmentIncome,
        contribution,
        fundingOrder,
        expenseProfile);
  }
}
