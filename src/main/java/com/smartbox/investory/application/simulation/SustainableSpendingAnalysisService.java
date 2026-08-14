package com.smartbox.investory.application.simulation;

import com.smartbox.investory.application.profile.InvestmentProfile;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/** Finds recurring spending headroom by repeatedly using the canonical simulator. */
@Service
public class SustainableSpendingAnalysisService {
  static final BigDecimal SEARCH_TOLERANCE = new BigDecimal("100");
  static final int MAX_EVALUATIONS = 80;
  private static final BigDecimal INITIAL_STEP = new BigDecimal("1000");
  private static final BigDecimal MAX_SPENDING = new BigDecimal("1000000000");

  private final SimulationEvaluationService evaluations;

  public SustainableSpendingAnalysisService(SimulationEvaluationService evaluations) {
    this.evaluations = evaluations;
  }

  public SustainableSpendingAnalysis analyze(
      InvestmentProfile profile, SimulationAssumptions assumptions) {
    BigDecimal current =
        assumptions.annualLivingExpenses().add(assumptions.annualDiscretionaryExpenses());
    return new SustainableSpendingAnalysis(
        current,
        find(profile, assumptions, SimulationScenario.BASE, current),
        find(profile, assumptions, SimulationScenario.CONSERVATIVE, current));
  }

  private SustainableSpendingAnalysis.ScenarioResult find(
      InvestmentProfile profile,
      SimulationAssumptions assumptions,
      SimulationScenario scenario,
      BigDecimal current) {
    Search search = new Search(profile, assumptions, scenario);
    EvaluationAt currentEvaluation = search.evaluate(current);
    BigDecimal sustainable;
    SustainableSpendingResultState state;
    if (!currentEvaluation.sustainable()) {
      EvaluationAt zero = search.evaluate(BigDecimal.ZERO);
      if (!zero.sustainable()) {
        sustainable = BigDecimal.ZERO;
        state = SustainableSpendingResultState.NO_SUSTAINABLE_SPENDING;
      } else {
        Boundary boundary = search.boundary(zero.spending(), current);
        sustainable = boundary.sustainable().spending();
        state =
            boundary.verified()
                ? SustainableSpendingResultState.BOUNDARY_FOUND
                : SustainableSpendingResultState.NON_MONOTONIC_RESULT;
      }
    } else {
      BigDecimal low = current;
      BigDecimal high = current.signum() == 0 ? INITIAL_STEP : current.multiply(BigDecimal.TWO);
      high = high.min(MAX_SPENDING);
      while (search.evaluate(high).sustainable() && high.compareTo(MAX_SPENDING) < 0) {
        low = high;
        high = high.multiply(BigDecimal.TWO).min(MAX_SPENDING);
      }
      if (high.compareTo(MAX_SPENDING) == 0 && search.evaluate(high).sustainable()) {
        sustainable = high;
        state = SustainableSpendingResultState.UPPER_BOUND_NOT_FOUND;
      } else {
        Boundary boundary = search.boundary(low, high);
        sustainable = boundary.sustainable().spending();
        state =
            boundary.verified()
                ? SustainableSpendingResultState.BOUNDARY_FOUND
                : SustainableSpendingResultState.NON_MONOTONIC_RESULT;
      }
    }
    BigDecimal headroom = sustainable.subtract(current);
    BigDecimal percentage =
        current.signum() == 0 ? null : headroom.divide(current, 6, RoundingMode.HALF_UP);
    return new SustainableSpendingAnalysis.ScenarioResult(
        sustainable, headroom, percentage, headroom.signum() < 0, state);
  }

  private final class Search {
    private final InvestmentProfile profile;
    private final SimulationAssumptions assumptions;
    private final SimulationScenario scenario;
    private int evaluationCount;

    private Search(
        InvestmentProfile profile, SimulationAssumptions assumptions, SimulationScenario scenario) {
      this.profile = profile;
      this.assumptions = assumptions;
      this.scenario = scenario;
    }

    private EvaluationAt evaluate(BigDecimal spending) {
      if (++evaluationCount > MAX_EVALUATIONS)
        throw new IllegalStateException("Sustainable spending search exceeded evaluation limit");
      return new EvaluationAt(
          spending,
          SustainableSpendingAnalysisService.this.evaluations.evaluate(
              profile, assumptions.withRecurringSpending(spending), scenario));
    }

    private Boundary boundary(BigDecimal low, BigDecimal high) {
      while (high.subtract(low).compareTo(SEARCH_TOLERANCE) > 0) {
        BigDecimal middle = low.add(high).divide(BigDecimal.TWO, 0, RoundingMode.HALF_UP);
        if (evaluate(middle).sustainable()) low = middle;
        else high = middle;
      }
      EvaluationAt sustainable = evaluate(low.setScale(0, RoundingMode.DOWN));
      EvaluationAt above = evaluate(high);
      return new Boundary(
          sustainable,
          above,
          sustainable.sustainable()
              && !above.sustainable()
              && above.spending().compareTo(sustainable.spending()) > 0);
    }
  }

  private record EvaluationAt(BigDecimal spending, SimulationEvaluation evaluation) {
    private boolean sustainable() {
      return evaluation.sustainable();
    }
  }

  private record Boundary(EvaluationAt sustainable, EvaluationAt above, boolean verified) {}
}
