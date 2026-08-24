package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.retirement.profile.InvestmentProfile;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Year;
import java.util.HashSet;
import java.util.List;

/**
 * Retirement-owned lifecycle and cash-flow assumptions.
 *
 * <p>The asset-specific return, funding-strategy, and harvest components are retained only for
 * persisted-plan read compatibility. The canonical orchestrator does not use them.
 */
public record SimulationAssumptions(
    int currentAge,
    int endAge,
    BigDecimal annualLivingExpenses,
    BigDecimal inflationRate,
    @Deprecated BigDecimal cashReturnRate,
    @Deprecated BigDecimal fixedIncomeReturnRate,
    BigDecimal equityReturnRate,
    @Deprecated BigDecimal realEstateReturnRate,
    @Deprecated BigDecimal otherReturnRate,
    int pensionStartAge,
    BigDecimal annualPension,
    BigDecimal capitalGainTaxRate,
    int startYear,
    BigDecimal annualDiscretionaryExpenses,
    List<SimulationEvent> futureEvents,
    BigDecimal rentalIncomeGrowthSpread,
    BigDecimal spendingGrowthSpread,
    @Deprecated SimulationFundingStrategy fundingStrategy,
    BigDecimal safeReserveYears,
    @Deprecated BigDecimal equityHarvestMinimumReturnRate,
    @Deprecated BigDecimal equityGainHarvestRate,
    @Deprecated boolean allowEmergencyEquityWithdrawal,
    int retirementAge,
    BigDecimal annualEmploymentIncome,
    BigDecimal annualPreRetirementContribution,
    @Deprecated List<FundingSource> fundingOrder,
    ExpenseProfile expenseProfile,
    ProjectedIncomePolicy projectedIncomePolicy) {
  public static final BigDecimal DEFAULT_RENTAL_INCOME_GROWTH_SPREAD = new BigDecimal("0.020");
  public static final BigDecimal DEFAULT_SPENDING_GROWTH_SPREAD = new BigDecimal("0.025");
  public static final BigDecimal DEFAULT_SAFE_RESERVE_YEARS = new BigDecimal("5");
  public static final BigDecimal DEFAULT_EQUITY_HARVEST_MINIMUM_RETURN_RATE =
      new BigDecimal("0.07");
  public static final BigDecimal DEFAULT_EQUITY_GAIN_HARVEST_RATE = new BigDecimal("0.75");
  public static final List<FundingSource> DEFAULT_FUNDING_ORDER =
      List.of(FundingSource.CASH, FundingSource.BONDS, FundingSource.STOCKS);

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
      BigDecimal rentalIncomeGrowthSpread,
      BigDecimal spendingGrowthSpread,
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
        rentalIncomeGrowthSpread,
        spendingGrowthSpread,
        fundingStrategy,
        safeReserveYears,
        equityHarvestMinimumReturnRate,
        equityGainHarvestRate,
        allowEmergencyEquityWithdrawal,
        retirementAge,
        annualEmploymentIncome,
        annualPreRetirementContribution,
        fundingOrder,
        expenseProfile,
        ProjectedIncomePolicy.SOURCE);
  }

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
      BigDecimal rentalIncomeGrowthSpread,
      BigDecimal spendingGrowthSpread,
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
        rentalIncomeGrowthSpread,
        spendingGrowthSpread,
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
      BigDecimal rentalIncomeGrowthSpread,
      BigDecimal spendingGrowthSpread,
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
        rentalIncomeGrowthSpread,
        spendingGrowthSpread,
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
        DEFAULT_RENTAL_INCOME_GROWTH_SPREAD,
        DEFAULT_SPENDING_GROWTH_SPREAD);
  }

  /** Compatibility constructor using the configured spread defaults. */
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
      BigDecimal rentalIncomeGrowthSpread) {
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
        rentalIncomeGrowthSpread,
        DEFAULT_SPENDING_GROWTH_SPREAD);
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
      BigDecimal rentalIncomeGrowthSpread,
      BigDecimal spendingGrowthSpread) {
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
        rentalIncomeGrowthSpread,
        spendingGrowthSpread,
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
    if (rentalIncomeGrowthSpread == null
        || spendingGrowthSpread == null
        || SimulationScenarioSettings.effectiveGrowthRate(inflationRate, rentalIncomeGrowthSpread)
                .compareTo(BigDecimal.ONE.negate())
            < 0
        || SimulationScenarioSettings.effectiveGrowthRate(inflationRate, spendingGrowthSpread)
                .compareTo(BigDecimal.ONE.negate())
            < 0) throw new IllegalArgumentException("Invalid effective growth rate");
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
    projectedIncomePolicy =
        projectedIncomePolicy == null ? ProjectedIncomePolicy.SOURCE : projectedIncomePolicy;
  }

  /** The persisted age is the age at the plan start year, not an independently changing age. */
  public int ageAtPlanStart() {
    return currentAge;
  }

  /** The persisted start year is the temporal anchor for every derived planning year. */
  public int planStartYear() {
    return startYear;
  }

  /** Active withdrawal and reserve-replenishment policy, backed by legacy persisted fields. */
  public RetirementFundingPolicy fundingPolicy() {
    return RetirementFundingPolicy.fromLegacy(this);
  }

  public ProjectedIncomePolicy projectedIncomePolicy() {
    return projectedIncomePolicy == null ? ProjectedIncomePolicy.SOURCE : projectedIncomePolicy;
  }

  /** Named copy boundary. Use this instead of reconstructing this record positionally. */
  public Builder toBuilder() {
    return new Builder(this);
  }

  public SimulationAssumptions withProjectedIncomePolicy(ProjectedIncomePolicy policy) {
    return toBuilder().projectedIncomePolicy(policy).build();
  }

  /** Nominal rental growth: economy-wide inflation plus the persisted rental spread. */
  public BigDecimal effectiveRentalIncomeGrowthRate() {
    return SimulationScenarioSettings.effectiveGrowthRate(inflationRate, rentalIncomeGrowthSpread);
  }

  /** Nominal spending growth: economy-wide inflation plus the persisted spending spread. */
  public BigDecimal effectiveSpendingGrowthRate() {
    return SimulationScenarioSettings.effectiveGrowthRate(inflationRate, spendingGrowthSpread);
  }

  public static SimulationAssumptions defaults(
      InvestmentProfile profile, int currentAge, int endAge) {
    return defaults(profile, currentAge, endAge, Year.now(Clock.systemDefaultZone()).getValue());
  }

  public static SimulationAssumptions defaults(
      InvestmentProfile profile, int currentAge, int endAge, int startYear) {
    return defaults(currentAge, endAge, startYear);
  }

  public static SimulationAssumptions defaults(int currentAge, int endAge, int startYear) {
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
        DEFAULT_RENTAL_INCOME_GROWTH_SPREAD,
        DEFAULT_SPENDING_GROWTH_SPREAD,
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
    return toBuilder().recurringSpending(recurringSpending).build();
  }

  public SimulationAssumptions withSpendingGrowthSpread(BigDecimal value) {
    return toBuilder().spendingGrowthSpread(value).build();
  }

  public SimulationAssumptions withInflationRate(BigDecimal value) {
    return toBuilder().inflationRate(value).build();
  }

  public SimulationAssumptions withRentalIncomeGrowthSpread(BigDecimal value) {
    return toBuilder().rentalIncomeGrowthSpread(value).build();
  }

  public SimulationAssumptions withEquityReturnRate(BigDecimal value) {
    return toBuilder().equityReturnRate(value).build();
  }

  public SimulationAssumptions withFixedIncomeReturnRate(BigDecimal value) {
    return toBuilder().fixedIncomeReturnRate(value).build();
  }

  public SimulationAssumptions withAnnualPension(BigDecimal value) {
    return toBuilder().annualPension(value).build();
  }

  public SimulationAssumptions withAnnualEmploymentIncome(BigDecimal value) {
    return toBuilder().annualEmploymentIncome(value).build();
  }

  public SimulationAssumptions withAnnualPreRetirementContribution(BigDecimal value) {
    return toBuilder().annualPreRetirementContribution(value).build();
  }

  public SimulationAssumptions withRetirementAge(int value) {
    return toBuilder().retirementAge(value).build();
  }

  public SimulationAssumptions withPensionStartAge(int value) {
    return toBuilder().pensionStartAge(value).build();
  }

  /** Rebase the temporal boundary without changing economic assumptions. */
  public SimulationAssumptions rebasedTo(
      int rebasedCurrentAge, int rebasedStartYear, List<SimulationEvent> remainingEvents) {
    int elapsedRetiredYears = Math.max(0, rebasedCurrentAge - Math.max(currentAge, retirementAge));
    BigDecimal accumulatedSpendingFactor =
        BigDecimal.ONE.add(effectiveSpendingGrowthRate()).pow(elapsedRetiredYears);
    return toBuilder()
        .currentAge(rebasedCurrentAge)
        .annualLivingExpenses(annualLivingExpenses.multiply(accumulatedSpendingFactor))
        .startYear(rebasedStartYear)
        .annualDiscretionaryExpenses(
            annualDiscretionaryExpenses.multiply(accumulatedSpendingFactor))
        .futureEvents(remainingEvents)
        .expenseProfile(expenseProfile.rebasedAt(rebasedStartYear - startYear))
        .build();
  }

  public SimulationAssumptions withExpenseProfile(ExpenseProfile value) {
    return toBuilder().expenseProfile(value).build();
  }

  /** Calendar context for an expense stage. The persisted stage offset starts at plan start. */
  public int expenseProfileStageYear(ExpenseProfileStep stage) {
    return planStartYear() + stage.fromYear();
  }

  public int expenseProfileStageAge(ExpenseProfileStep stage) {
    return ageAtPlanStart() + stage.fromYear();
  }

  public BigDecimal expenseProfileFactorForCalendarYear(int calendarYear) {
    return expenseProfile.factorForYear(calendarYear - planStartYear());
  }

  public static final class Builder {
    private int currentAge;
    private int endAge;
    private BigDecimal annualLivingExpenses;
    private BigDecimal inflationRate;
    private BigDecimal cashReturnRate;
    private BigDecimal fixedIncomeReturnRate;
    private BigDecimal equityReturnRate;
    private BigDecimal realEstateReturnRate;
    private BigDecimal otherReturnRate;
    private int pensionStartAge;
    private BigDecimal annualPension;
    private BigDecimal capitalGainTaxRate;
    private int startYear;
    private BigDecimal annualDiscretionaryExpenses;
    private List<SimulationEvent> futureEvents;
    private BigDecimal rentalIncomeGrowthSpread;
    private BigDecimal spendingGrowthSpread;
    private SimulationFundingStrategy fundingStrategy;
    private BigDecimal safeReserveYears;
    private BigDecimal equityHarvestMinimumReturnRate;
    private BigDecimal equityGainHarvestRate;
    private boolean allowEmergencyEquityWithdrawal;
    private int retirementAge;
    private BigDecimal annualEmploymentIncome;
    private BigDecimal annualPreRetirementContribution;
    private List<FundingSource> fundingOrder;
    private ExpenseProfile expenseProfile;
    private ProjectedIncomePolicy projectedIncomePolicy;

    private Builder(SimulationAssumptions source) {
      currentAge = source.currentAge;
      endAge = source.endAge;
      annualLivingExpenses = source.annualLivingExpenses;
      inflationRate = source.inflationRate;
      cashReturnRate = source.cashReturnRate;
      fixedIncomeReturnRate = source.fixedIncomeReturnRate;
      equityReturnRate = source.equityReturnRate;
      realEstateReturnRate = source.realEstateReturnRate;
      otherReturnRate = source.otherReturnRate;
      pensionStartAge = source.pensionStartAge;
      annualPension = source.annualPension;
      capitalGainTaxRate = source.capitalGainTaxRate;
      startYear = source.startYear;
      annualDiscretionaryExpenses = source.annualDiscretionaryExpenses;
      futureEvents = source.futureEvents;
      rentalIncomeGrowthSpread = source.rentalIncomeGrowthSpread;
      spendingGrowthSpread = source.spendingGrowthSpread;
      fundingStrategy = source.fundingStrategy;
      safeReserveYears = source.safeReserveYears;
      equityHarvestMinimumReturnRate = source.equityHarvestMinimumReturnRate;
      equityGainHarvestRate = source.equityGainHarvestRate;
      allowEmergencyEquityWithdrawal = source.allowEmergencyEquityWithdrawal;
      retirementAge = source.retirementAge;
      annualEmploymentIncome = source.annualEmploymentIncome;
      annualPreRetirementContribution = source.annualPreRetirementContribution;
      fundingOrder = source.fundingOrder;
      expenseProfile = source.expenseProfile;
      projectedIncomePolicy = source.projectedIncomePolicy();
    }

    public Builder currentAge(int value) {
      currentAge = value;
      return this;
    }

    public Builder endAge(int value) {
      endAge = value;
      return this;
    }

    public Builder annualLivingExpenses(BigDecimal value) {
      annualLivingExpenses = value;
      return this;
    }

    public Builder inflationRate(BigDecimal value) {
      inflationRate = value;
      return this;
    }

    public Builder cashReturnRate(BigDecimal value) {
      cashReturnRate = value;
      return this;
    }

    public Builder fixedIncomeReturnRate(BigDecimal value) {
      fixedIncomeReturnRate = value;
      return this;
    }

    public Builder equityReturnRate(BigDecimal value) {
      equityReturnRate = value;
      return this;
    }

    public Builder realEstateReturnRate(BigDecimal value) {
      realEstateReturnRate = value;
      return this;
    }

    public Builder otherReturnRate(BigDecimal value) {
      otherReturnRate = value;
      return this;
    }

    public Builder pensionStartAge(int value) {
      pensionStartAge = value;
      return this;
    }

    public Builder annualPension(BigDecimal value) {
      annualPension = value;
      return this;
    }

    public Builder capitalGainTaxRate(BigDecimal value) {
      capitalGainTaxRate = value;
      return this;
    }

    public Builder startYear(int value) {
      startYear = value;
      return this;
    }

    public Builder annualDiscretionaryExpenses(BigDecimal value) {
      annualDiscretionaryExpenses = value;
      return this;
    }

    /** Adjust total recurring spending while preserving the living/discretionary proportion. */
    public Builder recurringSpending(BigDecimal value) {
      if (value == null || value.signum() < 0)
        throw new IllegalArgumentException("Recurring spending cannot be negative");
      BigDecimal current = annualLivingExpenses.add(annualDiscretionaryExpenses);
      BigDecimal living =
          current.signum() == 0
              ? value
              : value
                  .multiply(annualLivingExpenses)
                  .divide(current, 12, java.math.RoundingMode.HALF_UP);
      annualLivingExpenses = living;
      annualDiscretionaryExpenses = value.subtract(living);
      return this;
    }

    public Builder futureEvents(List<SimulationEvent> value) {
      futureEvents = value;
      return this;
    }

    public Builder rentalIncomeGrowthSpread(BigDecimal value) {
      rentalIncomeGrowthSpread = value;
      return this;
    }

    public Builder spendingGrowthSpread(BigDecimal value) {
      spendingGrowthSpread = value;
      return this;
    }

    public Builder fundingStrategy(SimulationFundingStrategy value) {
      fundingStrategy = value;
      return this;
    }

    public Builder safeReserveYears(BigDecimal value) {
      safeReserveYears = value;
      return this;
    }

    public Builder equityHarvestMinimumReturnRate(BigDecimal value) {
      equityHarvestMinimumReturnRate = value;
      return this;
    }

    public Builder equityGainHarvestRate(BigDecimal value) {
      equityGainHarvestRate = value;
      return this;
    }

    public Builder allowEmergencyEquityWithdrawal(boolean value) {
      allowEmergencyEquityWithdrawal = value;
      return this;
    }

    public Builder retirementAge(int value) {
      retirementAge = value;
      return this;
    }

    public Builder annualEmploymentIncome(BigDecimal value) {
      annualEmploymentIncome = value;
      return this;
    }

    public Builder annualPreRetirementContribution(BigDecimal value) {
      annualPreRetirementContribution = value;
      return this;
    }

    public Builder fundingOrder(List<FundingSource> value) {
      fundingOrder = value;
      return this;
    }

    public Builder expenseProfile(ExpenseProfile value) {
      expenseProfile = value;
      return this;
    }

    public Builder projectedIncomePolicy(ProjectedIncomePolicy value) {
      projectedIncomePolicy = value;
      return this;
    }

    public SimulationAssumptions build() {
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
          rentalIncomeGrowthSpread,
          spendingGrowthSpread,
          fundingStrategy,
          safeReserveYears,
          equityHarvestMinimumReturnRate,
          equityGainHarvestRate,
          allowEmergencyEquityWithdrawal,
          retirementAge,
          annualEmploymentIncome,
          annualPreRetirementContribution,
          fundingOrder,
          expenseProfile,
          projectedIncomePolicy);
    }
  }
}
