package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.retirement.profile.EconomicBucket;
import com.smartbox.investory.retirement.profile.InvestmentProfile;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Analysis-owned, deterministic one-driver-at-a-time catalogue. */
@Service
public class SimulationSensitivityAnalysisService {
  private static final BigDecimal ONE_PP = new BigDecimal("0.01");
  private static final BigDecimal TWO_PP = new BigDecimal("0.02");
  private static final BigDecimal HALF_PP = new BigDecimal("0.005");
  private static final BigDecimal TEN_PERCENT = new BigDecimal("0.10");
  static final BigDecimal MATERIAL_SPENDABLE_ASSETS_DELTA = new BigDecimal("1000");
  static final BigDecimal MATERIAL_RESERVE_COVERAGE_DELTA = new BigDecimal("0.5");
  static final BigDecimal MATERIAL_WEALTH_PERCENT = new BigDecimal("0.01");
  private final SimulationEvaluationService evaluations;
  private final FrozenBondCashFlowProjection bondProjection;

  public SimulationSensitivityAnalysisService(SimulationEvaluationService evaluations) {
    this(evaluations, new FrozenBondCashFlowProjection());
  }

  @Autowired
  public SimulationSensitivityAnalysisService(
      SimulationEvaluationService evaluations, FrozenBondCashFlowProjection bondProjection) {
    this.evaluations = evaluations;
    this.bondProjection = bondProjection;
  }

  public SimulationSensitivityAnalysis analyze(
      InvestmentProfile profile, SimulationAssumptions assumptions) {
    return analyzeInternal(
        profile,
        assumptions,
        assumptions.startYear(),
        false,
        evaluations.evaluate(profile, assumptions, SimulationScenario.BASE));
  }

  public SimulationSensitivityAnalysis analyze(DeterministicAnalysisContext context) {
    return analyzeInternal(
        context.profile(),
        context.assumptions(),
        context.baselineYear(),
        true,
        context.canonicalBase());
  }

  private SimulationSensitivityAnalysis analyzeInternal(
      InvestmentProfile profile,
      SimulationAssumptions assumptions,
      int baselineYear,
      boolean explicitBaseline,
      SimulationEvaluation base) {
    Inputs inputs = new Inputs(profile, assumptions, base, baselineYear, explicitBaseline);
    List<SimulationSensitivityResult> results =
        catalogue().stream()
            .filter(definition -> definition.applicable().test(inputs))
            .map(definition -> evaluate(definition, inputs))
            .filter(result -> result.lowerEvaluation() != null || result.higherEvaluation() != null)
            .sorted(worstFirst())
            .toList();
    return new SimulationSensitivityAnalysis(
        base,
        results,
        results.isEmpty()
            ? "No active assumptions were available for sensitivity testing."
            : "Measured one assumption at a time.");
  }

  private SimulationSensitivityResult evaluate(Definition definition, Inputs inputs) {
    SimulationEvaluation lower =
        safeEvaluate(inputs, definition.lower().apply(inputs.assumptions()));
    SimulationEvaluation higher =
        safeEvaluate(inputs, definition.higher().apply(inputs.assumptions()));
    SimulationEvaluation adverse;
    SimulationEvaluation favorable;
    String direction;
    if (lower == null) {
      adverse = higher;
      favorable = null;
      direction = "Higher";
    } else if (higher == null) {
      adverse = lower;
      favorable = null;
      direction = "Lower";
    } else {
      int comparison = compareHarm(lower, higher, inputs.base());
      adverse = comparison >= 0 ? lower : higher;
      favorable = comparison >= 0 ? higher : lower;
      direction = comparison == 0 ? "Equivalent" : adverse == lower ? "Lower" : "Higher";
    }
    BigDecimal reserve = reserveDelta(inputs.base().sustainability(), adverse.sustainability());
    BigDecimal spendable =
        adverse
            .sustainability()
            .minimumSpendableAssets()
            .subtract(inputs.base().sustainability().minimumSpendableAssets());
    BigDecimal wealth =
        adverse
            .sustainability()
            .finalNetWorth()
            .subtract(inputs.base().sustainability().finalNetWorth());
    return new SimulationSensitivityResult(
        definition.driver(),
        definition.shock(),
        inputs.base(),
        adverse,
        favorable,
        reserve,
        !inputs.base().sustainability().recurringFundingGapRequired()
            && adverse.sustainability().recurringFundingGapRequired(),
        spendable,
        wealth,
        classify(inputs.base(), adverse, reserve, spendable, wealth),
        definition.testedValue().apply(definition.lower().apply(inputs.assumptions())),
        definition.testedValue().apply(inputs.assumptions()),
        definition.testedValue().apply(definition.higher().apply(inputs.assumptions())),
        lower,
        higher,
        direction);
  }

  private SimulationEvaluation safeEvaluate(Inputs inputs, SimulationAssumptions assumptions) {
    try {
      return inputs.explicitBaseline()
          ? evaluations.evaluate(
              inputs.profile(), assumptions, SimulationScenario.BASE, inputs.baselineYear())
          : evaluations.evaluate(inputs.profile(), assumptions, SimulationScenario.BASE);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  /** Positive means a is more harmful than b. */
  static int compareHarm(
      SimulationEvaluation a, SimulationEvaluation b, SimulationEvaluation base) {
    var x = a.sustainability();
    var y = b.sustainability();
    int c =
        Boolean.compare(
            base.sustainable() && !y.sustainable(), base.sustainable() && !x.sustainable());
    if (c != 0) return c;
    c = x.totalUnfundedAmount().compareTo(y.totalUnfundedAmount());
    if (c != 0) return c;
    c = Integer.compare(firstFailure(y), firstFailure(x));
    if (c != 0) return c;
    c = Boolean.compare(x.recurringFundingGapRequired(), y.recurringFundingGapRequired());
    if (c != 0) return c;
    c = y.minimumSpendableAssets().compareTo(x.minimumSpendableAssets());
    if (c != 0) return c;
    c = y.minimumSafeReserveCoverageYears().compareTo(x.minimumSafeReserveCoverageYears());
    if (c != 0) return c;
    return y.finalNetWorth().compareTo(x.finalNetWorth());
  }

  private static int firstFailure(PlanSustainabilityAssessment assessment) {
    return assessment.firstFailureYear() == null
        ? Integer.MAX_VALUE
        : assessment.firstFailureYear();
  }

  static SensitivityImpact classify(
      SimulationEvaluation baseline,
      SimulationEvaluation tested,
      BigDecimal reserve,
      BigDecimal spendable,
      BigDecimal wealth) {
    var b = baseline.sustainability();
    var t = tested.sustainability();
    if (b.sustainable() && !t.sustainable()) return SensitivityImpact.CRITICAL;
    if (t.totalUnfundedAmount().compareTo(b.totalUnfundedAmount()) > 0
        || (!b.recurringFundingGapRequired() && t.recurringFundingGapRequired()))
      return SensitivityImpact.HIGH;
    if (reserve.signum() < 0 && reserve.abs().compareTo(MATERIAL_RESERVE_COVERAGE_DELTA) >= 0)
      return SensitivityImpact.MODERATE;
    if (spendable.signum() < 0 && spendable.abs().compareTo(MATERIAL_SPENDABLE_ASSETS_DELTA) >= 0)
      return SensitivityImpact.MODERATE;
    BigDecimal baseWealth = b.finalNetWorth().abs();
    BigDecimal pct =
        baseWealth.signum() == 0
            ? BigDecimal.ZERO
            : wealth.negate().max(BigDecimal.ZERO).divide(baseWealth, 8, RoundingMode.HALF_UP);
    return wealth.signum() < 0 && pct.compareTo(MATERIAL_WEALTH_PERCENT) >= 0
        ? SensitivityImpact.WEALTH_ONLY
        : SensitivityImpact.NEGLIGIBLE;
  }

  private static BigDecimal reserveDelta(
      PlanSustainabilityAssessment base, PlanSustainabilityAssessment tested) {
    if (!base.recurringFundingGapRequired() || !tested.recurringFundingGapRequired())
      return BigDecimal.ZERO;
    return tested
        .minimumSafeReserveCoverageYears()
        .subtract(base.minimumSafeReserveCoverageYears());
  }

  private static Comparator<SimulationSensitivityResult> worstFirst() {
    return (left, right) -> {
      int c = Integer.compare(rank(left.impact()), rank(right.impact()));
      if (c != 0) return c;
      c = compareHarm(left.adverse(), right.adverse(), left.baseline());
      return c != 0 ? -c : left.driver().name().compareTo(right.driver().name());
    };
  }

  private static int rank(SensitivityImpact impact) {
    return switch (impact) {
      case CRITICAL -> 0;
      case HIGH -> 1;
      case MODERATE -> 2;
      case WEALTH_ONLY -> 3;
      case NEGLIGIBLE -> 4;
    };
  }

  /** Stable catalogue: id, label/category, shock, transformations, applicability, tested value. */
  private List<Definition> catalogue() {
    return List.of(
        def(
            SensitivityDriver.INFLATION,
            "±1 pp",
            a -> a.withInflationRate(a.inflationRate().subtract(ONE_PP)),
            a -> a.withInflationRate(a.inflationRate().add(ONE_PP)),
            Inputs::horizon,
            SimulationAssumptions::inflationRate),
        def(
            SensitivityDriver.RENTAL_INCOME_GROWTH,
            "±0.5 pp",
            a -> a.withRentalIncomeGrowthSpread(a.rentalIncomeGrowthSpread().subtract(HALF_PP)),
            a -> a.withRentalIncomeGrowthSpread(a.rentalIncomeGrowthSpread().add(HALF_PP)),
            Inputs::rental,
            SimulationAssumptions::effectiveRentalIncomeGrowthRate),
        def(
            SensitivityDriver.FIXED_INCOME_RETURN,
            "±1 pp",
            a -> a.withFixedIncomeReturnRate(a.fixedIncomeReturnRate().subtract(ONE_PP)),
            a -> a.withFixedIncomeReturnRate(a.fixedIncomeReturnRate().add(ONE_PP)),
            Inputs::bond,
            SimulationAssumptions::fixedIncomeReturnRate),
        def(
            SensitivityDriver.EQUITY_RETURN,
            "±2 pp",
            a -> a.withEquityReturnRate(a.equityReturnRate().subtract(TWO_PP)),
            a -> a.withEquityReturnRate(a.equityReturnRate().add(TWO_PP)),
            Inputs::equity,
            SimulationAssumptions::equityReturnRate),
        def(
            SensitivityDriver.SPENDING_GROWTH,
            "±0.5 pp",
            a -> a.withSpendingGrowthSpread(a.spendingGrowthSpread().subtract(HALF_PP)),
            a -> a.withSpendingGrowthSpread(a.spendingGrowthSpread().add(HALF_PP)),
            Inputs::spendingGrowth,
            SimulationAssumptions::effectiveSpendingGrowthRate),
        def(
            SensitivityDriver.RECURRING_SPENDING,
            "±10%",
            a ->
                a.withRecurringSpending(spending(a).multiply(BigDecimal.ONE.subtract(TEN_PERCENT))),
            a -> a.withRecurringSpending(spending(a).multiply(BigDecimal.ONE.add(TEN_PERCENT))),
            i -> spending(i).signum() > 0,
            a -> spending(a)),
        def(
            SensitivityDriver.PENSION,
            "±10%",
            a ->
                a.withAnnualPension(
                    a.annualPension().multiply(BigDecimal.ONE.subtract(TEN_PERCENT))),
            a -> a.withAnnualPension(a.annualPension().multiply(BigDecimal.ONE.add(TEN_PERCENT))),
            i -> i.horizon() && i.assumptions().annualPension().signum() > 0,
            SimulationAssumptions::annualPension));
  }

  private static Definition def(
      SensitivityDriver driver,
      String shock,
      Function<SimulationAssumptions, SimulationAssumptions> lower,
      Function<SimulationAssumptions, SimulationAssumptions> higher,
      Predicate<Inputs> applicable,
      Function<SimulationAssumptions, BigDecimal> testedValue) {
    return new Definition(driver, shock, lower, higher, applicable, testedValue);
  }

  private static BigDecimal spending(Inputs inputs) {
    return spending(inputs.assumptions());
  }

  private static BigDecimal spending(SimulationAssumptions assumptions) {
    return assumptions.annualLivingExpenses().add(assumptions.annualDiscretionaryExpenses());
  }

  private record Definition(
      SensitivityDriver driver,
      String shock,
      Function<SimulationAssumptions, SimulationAssumptions> lower,
      Function<SimulationAssumptions, SimulationAssumptions> higher,
      Predicate<Inputs> applicable,
      Function<SimulationAssumptions, BigDecimal> testedValue) {}

  private record Inputs(
      InvestmentProfile profile,
      SimulationAssumptions assumptions,
      SimulationEvaluation base,
      int baselineYear,
      boolean explicitBaseline) {
    boolean horizon() {
      return base.result() == null || !base.result().years().isEmpty();
    }

    boolean spendingGrowth() {
      return horizon()
          && (base.result() == null
              || base.result().years().stream()
                  .anyMatch(y -> y.coreExpenses().add(y.discretionaryExpenses()).signum() > 0));
    }

    boolean rental() {
      return horizon()
          && (base.result() == null
              ? profile.currentRentalIncome() != null && profile.currentRentalIncome().signum() != 0
              : base.result().years().stream().anyMatch(y -> y.rentalIncome().signum() != 0));
    }

    boolean equity() {
      return horizon()
          && (base.result() == null
              ? profile.allocations().stream()
                  .anyMatch(a -> a.bucket() == EconomicBucket.EQUITY && a.isNonZero())
              : base.result().years().stream()
                  .anyMatch(y -> y.equityStart().signum() != 0 || y.equityEnd().signum() != 0));
    }

    boolean bond() {
      if (!horizon()) return false;
      boolean allocationExposure =
          profile.allocations().stream()
              .anyMatch(a -> a.bucket() == EconomicBucket.FIXED_INCOME && a.isNonZero());
      int first =
          base.result() == null ? assumptions.startYear() : base.result().years().getFirst().year();
      int last =
          base.result() == null
              ? assumptions.startYear() + assumptions.endAge() - assumptions.currentAge()
              : base.result().years().getLast().year();
      return allocationExposure
          || new FrozenBondCashFlowProjection().hasCapitalizedBondYield(profile, first, last);
    }
  }
}
