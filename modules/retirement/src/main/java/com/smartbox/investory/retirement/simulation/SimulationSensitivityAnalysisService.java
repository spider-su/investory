package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.retirement.profile.InvestmentProfile;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/** Deterministic one-variable-at-a-time Base-plan sensitivity analysis. */
@Service
public class SimulationSensitivityAnalysisService {
  private static final BigDecimal TEN_PERCENT = new BigDecimal("0.10");
  private static final BigDecimal HALF_PERCENTAGE_POINT = new BigDecimal("0.005");

  private final SimulationEvaluationService evaluations;

  public SimulationSensitivityAnalysisService(SimulationEvaluationService evaluations) {
    this.evaluations = evaluations;
  }

  public SimulationSensitivityAnalysis analyze(
      InvestmentProfile profile, SimulationAssumptions assumptions) {
    SimulationEvaluation baseline =
        evaluations.evaluate(profile, assumptions, SimulationScenario.BASE);
    List<SimulationSensitivityResult> results = new ArrayList<>();
    if (recurringSpending(assumptions).signum() > 0)
      results.add(
          result(
              profile,
              assumptions,
              baseline,
              SensitivityDriver.RECURRING_SPENDING,
              "+10%",
              assumptions.withRecurringSpending(
                  recurringSpending(assumptions).multiply(BigDecimal.ONE.add(TEN_PERCENT))),
              assumptions.withRecurringSpending(
                  recurringSpending(assumptions).multiply(BigDecimal.ONE.subtract(TEN_PERCENT)))));
    results.add(
        result(
            profile,
            assumptions,
            baseline,
            SensitivityDriver.SPENDING_GROWTH,
            "+0.5 pp",
            assumptions.withSpendingGrowthSpread(
                assumptions.spendingGrowthSpread().add(HALF_PERCENTAGE_POINT)),
            assumptions.withSpendingGrowthSpread(
                assumptions.spendingGrowthSpread().subtract(HALF_PERCENTAGE_POINT))));
    if (assumptions.annualPension().signum() > 0
        && assumptions.pensionStartAge() <= assumptions.endAge())
      results.add(
          result(
              profile,
              assumptions,
              baseline,
              SensitivityDriver.PENSION,
              "−10%",
              assumptions.withAnnualPension(
                  assumptions.annualPension().multiply(new BigDecimal("0.90"))),
              assumptions.withAnnualPension(
                  assumptions.annualPension().multiply(new BigDecimal("1.10")))));
    results.sort(worstFirst());
    return new SimulationSensitivityAnalysis(baseline, results, interpretation(results));
  }

  private SimulationSensitivityResult result(
      InvestmentProfile profile,
      SimulationAssumptions assumptions,
      SimulationEvaluation baseline,
      SensitivityDriver driver,
      String perturbation,
      SimulationAssumptions adverseAssumptions,
      SimulationAssumptions favorableAssumptions) {
    return measured(
        driver,
        perturbation,
        baseline,
        evaluations.evaluate(profile, adverseAssumptions, SimulationScenario.BASE),
        evaluations.evaluate(profile, favorableAssumptions, SimulationScenario.BASE));
  }

  private static SimulationSensitivityResult measured(
      SensitivityDriver driver,
      String perturbation,
      SimulationEvaluation baseline,
      SimulationEvaluation adverse,
      SimulationEvaluation favorable) {
    var baseAssessment = baseline.sustainability();
    var adverseAssessment = adverse.sustainability();
    boolean adverseIntroducesGap =
        !baseAssessment.recurringFundingGapRequired()
            && adverseAssessment.recurringFundingGapRequired();
    BigDecimal reserveDelta = reserveDelta(baseAssessment, adverseAssessment);
    BigDecimal spendableDelta =
        adverseAssessment
            .minimumSpendableAssets()
            .subtract(baseAssessment.minimumSpendableAssets());
    BigDecimal wealthDelta =
        adverseAssessment.finalNetWorth().subtract(baseAssessment.finalNetWorth());
    return new SimulationSensitivityResult(
        driver,
        perturbation,
        baseline,
        adverse,
        favorable,
        reserveDelta,
        adverseIntroducesGap,
        spendableDelta,
        wealthDelta,
        classify(baseline, adverse, reserveDelta, spendableDelta, wealthDelta));
  }

  private static BigDecimal reserveDelta(
      PlanSustainabilityAssessment baseline, PlanSustainabilityAssessment adverse) {
    if (!baseline.recurringFundingGapRequired() && !adverse.recurringFundingGapRequired()) {
      return BigDecimal.ZERO;
    }
    if (!baseline.recurringFundingGapRequired() && adverse.recurringFundingGapRequired()) {
      return BigDecimal.ZERO;
    }
    if (baseline.recurringFundingGapRequired() && !adverse.recurringFundingGapRequired()) {
      return BigDecimal.ZERO;
    }
    return adverse
        .minimumSafeReserveCoverageYears()
        .subtract(baseline.minimumSafeReserveCoverageYears());
  }

  private static SensitivityImpact classify(
      SimulationEvaluation baseline,
      SimulationEvaluation adverse,
      BigDecimal reserveDelta,
      BigDecimal spendableDelta,
      BigDecimal wealthDelta) {
    if (baseline.sustainable() && !adverse.sustainable()) return SensitivityImpact.CRITICAL;
    if (adverse
            .sustainability()
            .totalUnfundedAmount()
            .compareTo(baseline.sustainability().totalUnfundedAmount())
        > 0) return SensitivityImpact.HIGH;
    if (!baseline.sustainability().recurringFundingGapRequired()
        && adverse.sustainability().recurringFundingGapRequired()) return SensitivityImpact.HIGH;
    if (spendableDelta.signum() < 0) return SensitivityImpact.MODERATE;
    BigDecimal baselineWealth = baseline.sustainability().finalNetWorth().abs();
    if (wealthDelta.signum() < 0
        && (baselineWealth.signum() == 0
            || wealthDelta
                    .abs()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(baselineWealth, 4, java.math.RoundingMode.HALF_UP)
                    .compareTo(BigDecimal.ONE)
                >= 0)) return SensitivityImpact.WEALTH_ONLY;
    return SensitivityImpact.NEGLIGIBLE;
  }

  private static Comparator<SimulationSensitivityResult> worstFirst() {
    return Comparator.comparingInt((SimulationSensitivityResult r) -> impactRank(r.impact()))
        .thenComparing(
            r -> r.adverse().sustainability().firstFailureYear(),
            Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(SimulationSensitivityResult::reserveCoverageDelta)
        .thenComparing(SimulationSensitivityResult::spendableAssetsDelta)
        .thenComparing(SimulationSensitivityResult::finalNetWorthDelta);
  }

  private static int impactRank(SensitivityImpact impact) {
    return switch (impact) {
      case CRITICAL -> 0;
      case HIGH -> 1;
      case MODERATE -> 2;
      case WEALTH_ONLY -> 3;
      case NEGLIGIBLE -> 4;
    };
  }

  private static String interpretation(List<SimulationSensitivityResult> results) {
    if (results.isEmpty()) return "No active assumptions were available for sensitivity testing.";
    SimulationSensitivityResult top = results.get(0);
    if (top.impact() == SensitivityImpact.CRITICAL)
      return top.driver().label() + " is the largest tested threat to plan sustainability.";
    if (top.impact() == SensitivityImpact.WEALTH_ONLY)
      return top.driver().label() + " has the largest tested effect, mainly on ending wealth.";
    return "Of the tested assumptions, "
        + top.driver().label().toLowerCase()
        + " has the largest impact on plan margin.";
  }

  private static BigDecimal recurringSpending(SimulationAssumptions assumptions) {
    return assumptions.annualLivingExpenses().add(assumptions.annualDiscretionaryExpenses());
  }

}
