package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.retirement.profile.InvestmentProfile;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
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

  public SimulationSensitivityAnalysisService(SimulationEvaluationService evaluations) { this.evaluations = evaluations; }
  public SimulationSensitivityAnalysis analyze(InvestmentProfile p, SimulationAssumptions a) {
    return analyzeInternal(p, a, null, evaluations.evaluate(p, a, SimulationScenario.BASE));
  }
  public SimulationSensitivityAnalysis analyze(DeterministicAnalysisContext c) {
    return analyzeInternal(c.profile(), c.assumptions(), c.baselineYear(), c.canonicalBase());
  }
  private SimulationSensitivityAnalysis analyzeInternal(InvestmentProfile p, SimulationAssumptions a, Integer year, SimulationEvaluation base) {
    Inputs inputs = new Inputs(p, a, base);
    List<SimulationSensitivityResult> results = catalogue().stream().filter(d -> d.applicable().test(inputs))
        .map(d -> evaluate(d, p, a, year, base)).sorted(worstFirst()).toList();
    return new SimulationSensitivityAnalysis(base, results, results.isEmpty() ? "No active assumptions were available for sensitivity testing." : "Measured one assumption at a time.");
  }
  private SimulationSensitivityResult evaluate(Definition d, InvestmentProfile p, SimulationAssumptions a, Integer year, SimulationEvaluation base) {
    SimulationEvaluation lower = evaluate(p, d.lower().apply(a), year);
    SimulationEvaluation higher = evaluate(p, d.higher().apply(a), year);
    SimulationEvaluation adverse = worse(base, lower, higher);
    SimulationEvaluation favorable = adverse == lower ? higher : lower;
    BigDecimal reserve = reserveDelta(base.sustainability(), adverse.sustainability());
    BigDecimal spendable = adverse.sustainability().minimumSpendableAssets().subtract(base.sustainability().minimumSpendableAssets());
    BigDecimal wealth = adverse.sustainability().finalNetWorth().subtract(base.sustainability().finalNetWorth());
    return new SimulationSensitivityResult(d.driver(), d.shock(), base, adverse, favorable, reserve,
        !base.sustainability().recurringFundingGapRequired() && adverse.sustainability().recurringFundingGapRequired(),
        spendable, wealth, classify(base, adverse, reserve, spendable, wealth),
        testedValue(d.driver(), lowerAssumptions(d, a)), testedValue(d.driver(), a),
        testedValue(d.driver(), higherAssumptions(d, a)), lower, higher,
        adverse == lower ? "Lower" : "Higher");
  }
  private static SimulationAssumptions lowerAssumptions(Definition d, SimulationAssumptions a) { return d.lower().apply(a); }
  private static SimulationAssumptions higherAssumptions(Definition d, SimulationAssumptions a) { return d.higher().apply(a); }
  private static BigDecimal testedValue(SensitivityDriver driver, SimulationAssumptions a) {
    return switch (driver) {
      case INFLATION -> a.inflationRate();
      case RENTAL_INCOME_GROWTH -> a.rentalIncomeGrowthSpread();
      case FIXED_INCOME_RETURN -> a.fixedIncomeReturnRate();
      case EQUITY_RETURN -> a.equityReturnRate();
      case SPENDING_GROWTH -> a.spendingGrowthSpread();
      case RECURRING_SPENDING -> a.annualLivingExpenses().add(a.annualDiscretionaryExpenses());
      case PENSION -> a.annualPension();
      default -> BigDecimal.ZERO;
    };
  }
  private SimulationEvaluation evaluate(InvestmentProfile p, SimulationAssumptions a, Integer year) {
    return year == null ? evaluations.evaluate(p, a, SimulationScenario.BASE) : evaluations.evaluate(p, a, SimulationScenario.BASE, year);
  }
  private static SimulationEvaluation worse(SimulationEvaluation base, SimulationEvaluation lower, SimulationEvaluation higher) {
    return score(lower, base) >= score(higher, base) ? lower : higher;
  }
  private static int score(SimulationEvaluation c, SimulationEvaluation b) {
    var x = c.sustainability(); var y = b.sustainability(); int score = 0;
    if (y.sustainable() && !x.sustainable()) score += 1_000_000;
    score += x.totalUnfundedAmount().compareTo(y.totalUnfundedAmount()) * 10_000;
    if (!y.recurringFundingGapRequired() && x.recurringFundingGapRequired()) score += 5_000;
    score += y.minimumSpendableAssets().compareTo(x.minimumSpendableAssets());
    score += y.finalNetWorth().compareTo(x.finalNetWorth());
    return score;
  }
  private static BigDecimal reserveDelta(PlanSustainabilityAssessment b, PlanSustainabilityAssessment t) {
    if (!b.recurringFundingGapRequired() || !t.recurringFundingGapRequired()) return BigDecimal.ZERO;
    return t.minimumSafeReserveCoverageYears().subtract(b.minimumSafeReserveCoverageYears());
  }
  static SensitivityImpact classify(SimulationEvaluation baseline, SimulationEvaluation tested, BigDecimal reserve, BigDecimal spendable, BigDecimal wealth) {
    var b = baseline.sustainability(); var t = tested.sustainability();
    if (b.sustainable() && !t.sustainable()) return SensitivityImpact.CRITICAL;
    if (t.totalUnfundedAmount().compareTo(b.totalUnfundedAmount()) > 0 || (!b.recurringFundingGapRequired() && t.recurringFundingGapRequired())) return SensitivityImpact.HIGH;
    if (b.recurringFundingGapRequired() && t.recurringFundingGapRequired() && reserve.abs().compareTo(MATERIAL_RESERVE_COVERAGE_DELTA) >= 0) return SensitivityImpact.MODERATE;
    if (spendable.signum() < 0 && spendable.abs().compareTo(MATERIAL_SPENDABLE_ASSETS_DELTA) >= 0) return SensitivityImpact.MODERATE;
    BigDecimal baseWealth = b.finalNetWorth().abs();
    BigDecimal pct = baseWealth.signum() == 0 ? BigDecimal.ZERO : wealth.abs().divide(baseWealth, 8, RoundingMode.HALF_UP);
    return wealth.signum() < 0 && pct.compareTo(MATERIAL_WEALTH_PERCENT) >= 0 ? SensitivityImpact.WEALTH_ONLY : SensitivityImpact.NEGLIGIBLE;
  }
  private static Comparator<SimulationSensitivityResult> worstFirst() {
    return Comparator.comparingInt((SimulationSensitivityResult r) -> rank(r.impact()))
        .thenComparing(r -> r.adverse().sustainability().firstFailureYear(), Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(SimulationSensitivityResult::spendableAssetsDelta);
  }
  private static int rank(SensitivityImpact i) { return switch (i) { case CRITICAL -> 0; case HIGH -> 1; case MODERATE -> 2; case WEALTH_ONLY -> 3; case NEGLIGIBLE -> 4; }; }

  private static List<Definition> catalogue() {
    return List.of(
        def(SensitivityDriver.INFLATION, "±1 pp", a -> a.withInflationRate(a.inflationRate().subtract(ONE_PP)), a -> a.withInflationRate(a.inflationRate().add(ONE_PP)), Inputs::horizon),
        def(SensitivityDriver.RENTAL_INCOME_GROWTH, "±0.5 pp", a -> a.withRentalIncomeGrowthSpread(a.rentalIncomeGrowthSpread().subtract(HALF_PP)), a -> a.withRentalIncomeGrowthSpread(a.rentalIncomeGrowthSpread().add(HALF_PP)), Inputs::rental),
        def(SensitivityDriver.FIXED_INCOME_RETURN, "±1 pp", a -> a.withFixedIncomeReturnRate(a.fixedIncomeReturnRate().subtract(ONE_PP)), a -> a.withFixedIncomeReturnRate(a.fixedIncomeReturnRate().add(ONE_PP)), Inputs::bond),
        def(SensitivityDriver.EQUITY_RETURN, "±2 pp", a -> a.withEquityReturnRate(a.equityReturnRate().subtract(TWO_PP)), a -> a.withEquityReturnRate(a.equityReturnRate().add(TWO_PP)), Inputs::equity),
        def(SensitivityDriver.SPENDING_GROWTH, "±0.5 pp", a -> a.withSpendingGrowthSpread(a.spendingGrowthSpread().subtract(HALF_PP)), a -> a.withSpendingGrowthSpread(a.spendingGrowthSpread().add(HALF_PP)), Inputs::spendingGrowth),
        def(SensitivityDriver.RECURRING_SPENDING, "±10%", a -> a.withRecurringSpending(spending(a).multiply(BigDecimal.ONE.subtract(TEN_PERCENT))), a -> a.withRecurringSpending(spending(a).multiply(BigDecimal.ONE.add(TEN_PERCENT))), i -> spending(i).signum() > 0),
        def(SensitivityDriver.PENSION, "±10%", a -> a.withAnnualPension(a.annualPension().multiply(BigDecimal.ONE.subtract(TEN_PERCENT))), a -> a.withAnnualPension(a.annualPension().multiply(BigDecimal.ONE.add(TEN_PERCENT))), i -> i.horizon() && i.assumptions().annualPension().signum() > 0));
  }
  private static Definition def(SensitivityDriver d, String shock, Function<SimulationAssumptions, SimulationAssumptions> l, Function<SimulationAssumptions, SimulationAssumptions> h, Predicate<Inputs> p) {
    return new Definition(d, d.label(), d.category(), shock, l, h, p);
  }
  private static BigDecimal spending(Inputs i) { return spending(i.assumptions()); }
  private static BigDecimal spending(SimulationAssumptions a) { return a.annualLivingExpenses().add(a.annualDiscretionaryExpenses()); }
  private record Definition(SensitivityDriver driver, String label, SensitivityDriverCategory category, String unit,
      Function<SimulationAssumptions, SimulationAssumptions> lower, Function<SimulationAssumptions, SimulationAssumptions> higher,
      Predicate<Inputs> applicable) {
    String shock() { return unit; }
  }
  private record Inputs(InvestmentProfile profile, SimulationAssumptions assumptions, SimulationEvaluation base) {
    boolean horizon() { return base.result() == null || !base.result().years().isEmpty(); }
    boolean spendingGrowth() { return horizon() && (base.result() == null || base.result().years().stream().anyMatch(y -> y.coreExpenses().add(y.discretionaryExpenses()).signum() > 0)); }
    boolean rental() { return horizon() && (base.result() == null ? profile.currentRentalIncome() != null && profile.currentRentalIncome().signum() != 0 : base.result().years().stream().anyMatch(y -> y.rentalIncome().signum() != 0)); }
    boolean equity() { return horizon() && (base.result() == null ? profile.allocations().stream().anyMatch(a -> a.bucket() == com.smartbox.investory.retirement.profile.EconomicBucket.EQUITY && a.isNonZero()) : base.result().years().stream().anyMatch(y -> y.equityStart().signum() != 0 || y.equityEnd().signum() != 0)); }
    boolean bond() { return horizon() && (base.result() == null ? profile.allocations().stream().anyMatch(a -> a.bucket() == com.smartbox.investory.retirement.profile.EconomicBucket.FIXED_INCOME && a.isNonZero()) : base.result().years().stream().anyMatch(y -> y.fixedIncomeStart().signum() != 0 || y.fixedIncomeEnd().signum() != 0 || y.capitalizedBondReturn().signum() != 0)); }
  }
}
