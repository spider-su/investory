package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.retirement.simulation.PlanSustainabilityAssessment;
import com.smartbox.investory.retirement.simulation.RetirementAgeAnalysis;
import com.smartbox.investory.retirement.simulation.RetirementTimingResultState;
import com.smartbox.investory.retirement.simulation.SensitivityDriverCategory;
import com.smartbox.investory.retirement.simulation.SensitivityImpact;
import com.smartbox.investory.retirement.simulation.SimulationEvaluation;
import com.smartbox.investory.retirement.simulation.SimulationSensitivityAnalysis;
import com.smartbox.investory.retirement.simulation.SimulationSensitivityResult;
import com.smartbox.investory.retirement.simulation.SustainableSpendingAnalysis;
import com.smartbox.investory.retirement.simulation.SustainableSpendingResultState;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;

/** Formats retirement-analysis results while leaving currency conversion backend-authoritative. */
final class RetirementAnalysisPresentation {
  private final PlanningMoneyConversionService money;

  RetirementAnalysisPresentation(PlanningMoneyConversionService money) {
    this.money = money;
  }

  SustainableSpendingAnalysisMoney displaySustainableSpending(
      SustainableSpendingAnalysis analysis, CurrencyType display) {
    var base = analysis.base();
    var conservative = analysis.conservative();
    BigDecimal current = toDisplay(analysis.currentRecurringSpending(), display);
    BigDecimal baseLimit = toDisplay(base.sustainableSpending(), display);
    BigDecimal conservativeLimit = toDisplay(conservative.sustainableSpending(), display);
    BigDecimal baseHeadroom = toDisplay(base.headroom(), display);
    BigDecimal conservativeHeadroom = toDisplay(conservative.headroom(), display);
    String interpretation;
    if (base.state() == SustainableSpendingResultState.NO_SUSTAINABLE_SPENDING
        && conservative.state() == SustainableSpendingResultState.NO_SUSTAINABLE_SPENDING) {
      interpretation =
          "Even zero total annual costs does not make the plan sustainable in either scenario.";
    } else if (base.state() == SustainableSpendingResultState.NON_MONOTONIC_RESULT
        || conservative.state() == SustainableSpendingResultState.NON_MONOTONIC_RESULT) {
      interpretation =
          "No reliable single spending limit can be determined under the tested assumptions.";
    } else if (base.state() == SustainableSpendingResultState.UPPER_BOUND_NOT_FOUND
        || conservative.state() == SustainableSpendingResultState.UPPER_BOUND_NOT_FOUND) {
      interpretation = "The spending limit exceeds the tested range in at least one scenario.";
    } else if (base.currentSpendingAboveLimit() && conservative.currentSpendingAboveLimit()) {
      interpretation =
          "Planned costs are above the spending limit in both Base and Conservative scenarios.";
    } else if (conservative.currentSpendingAboveLimit()) {
      interpretation =
          "Planned costs are about "
              + displayMoney(display, conservativeHeadroom.abs())
              + " per year over the Conservative spending limit. The Base plan remains within its limit.";
    } else if (base.currentSpendingAboveLimit()) {
      interpretation =
          "Planned costs are about "
              + displayMoney(display, baseHeadroom.abs())
              + " per year over the Base spending limit.";
    } else {
      interpretation =
          "Planned costs are within both Base and Conservative spending limits. Conservative extra capacity is about "
              + displayMoney(display, conservativeHeadroom.abs())
              + " per year.";
    }
    return new SustainableSpendingAnalysisMoney(
        PlanningPresentation.wholeNumber(current),
        spendingLimit(base, baseLimit),
        spendingLimit(conservative, conservativeLimit),
        spendingHeadroom(base, baseHeadroom),
        spendingHeadroom(conservative, conservativeHeadroom),
        spendingPercentage(base),
        spendingPercentage(conservative),
        base.currentSpendingAboveLimit(),
        conservative.currentSpendingAboveLimit(),
        interpretation);
  }

  SimulationSensitivityAnalysisMoney displaySensitivity(
      SimulationSensitivityAnalysis analysis, CurrencyType display) {
    return new SimulationSensitivityAnalysisMoney(
        analysis.interpretation(),
        analysis.topDrivers(3).stream()
            .map(result -> displaySensitivityResult(result, display))
            .toList());
  }

  PlanRiskView displayPlanRisks(SimulationSensitivityAnalysis analysis, CurrencyType display) {
    var riskResults =
        analysis.drivers().stream()
            .filter(
                result -> result.driver().category() == SensitivityDriverCategory.ECONOMIC_DRIVER)
            .toList();
    var leverResults =
        analysis.drivers().stream()
            .filter(
                result -> result.driver().category() == SensitivityDriverCategory.PLANNING_LEVER)
            .toList();
    var risks =
        riskResults.stream().map(result -> displaySensitivityResult(result, display)).toList();
    var levers =
        leverResults.stream().map(result -> displaySensitivityResult(result, display)).toList();
    return new PlanRiskView(
        riskInterpretation(analysis, riskResults), risks.stream().limit(3).toList(), risks, levers);
  }

  RetirementAgeAnalysisMoney displayRetirementAgeAnalysis(RetirementAgeAnalysis analysis) {
    var base = displayRetirementScenario(analysis.base());
    var conservative = displayRetirementScenario(analysis.conservative());
    String interpretation;
    if (analysis.conservative().state() == RetirementTimingResultState.NO_SUSTAINABLE_AGE
        && analysis.base().state() == RetirementTimingResultState.NO_SUSTAINABLE_AGE) {
      interpretation =
          "No sustainable retirement age was found within the configured planning horizon in Base or Conservative scenarios.";
    } else if (analysis.conservative().state() == RetirementTimingResultState.NON_MONOTONIC_RESULT
        || analysis.base().state() == RetirementTimingResultState.NON_MONOTONIC_RESULT) {
      interpretation =
          "Retirement timing results are non-monotonic under the current assumptions; life events or funding-policy timing affect individual ages.";
    } else if (analysis.conservative().state() == RetirementTimingResultState.DELAY_REQUIRED) {
      interpretation =
          "The planned retirement age is not sustainable under Conservative assumptions. Sustainability begins at "
              + conservative.earliest()
              + ".";
    } else if (analysis.conservative().state() == RetirementTimingResultState.ALREADY_RETIRED) {
      interpretation = "The planned retirement age has passed the current planning boundary.";
    } else if (analysis.conservative().state()
        == RetirementTimingResultState.IMMEDIATE_RETIREMENT_AVAILABLE) {
      interpretation =
          "The plan supports immediate retirement under the Conservative scenario. Base: "
              + base.earliest()
              + ".";
    } else if (analysis.conservative().headroomYears() > 0) {
      interpretation =
          "The Conservative scenario supports retirement "
              + analysis.conservative().headroomYears()
              + " years earlier than planned. Base: "
              + analysis.base().headroomYears()
              + " years earlier.";
    } else {
      interpretation =
          "The planned retirement age is the earliest sustainable age under Conservative assumptions. Base: "
              + analysis.base().headroomYears()
              + " years earlier.";
    }
    return new RetirementAgeAnalysisMoney(interpretation, base, conservative);
  }

  PlanningFlexibilityMoney displayPlanningFlexibility(
      SustainableSpendingAnalysis spending,
      RetirementAgeAnalysis retirement,
      CurrencyType display) {
    return new PlanningFlexibilityMoney(
        displaySustainableSpending(spending, display), displayRetirementAgeAnalysis(retirement));
  }

  private static String spendingLimit(
      SustainableSpendingAnalysis.ScenarioResult result, BigDecimal value) {
    return switch (result.state()) {
      case BOUNDARY_FOUND -> PlanningPresentation.wholeNumber(value);
      case UPPER_BOUND_NOT_FOUND -> "Above tested range";
      case NO_SUSTAINABLE_SPENDING -> "None found";
      case NON_MONOTONIC_RESULT -> "No reliable spending limit";
    };
  }

  private static String spendingHeadroom(
      SustainableSpendingAnalysis.ScenarioResult result, BigDecimal value) {
    return result.state() == SustainableSpendingResultState.BOUNDARY_FOUND
        ? (value.signum() > 0 ? "+" : "") + PlanningPresentation.wholeNumber(value.abs())
        : "Not determined";
  }

  private static String spendingPercentage(SustainableSpendingAnalysis.ScenarioResult result) {
    if (result.state() != SustainableSpendingResultState.BOUNDARY_FOUND
        || result.headroomPercentage() == null) {
      return "Not determined";
    }
    return (result.headroomPercentage().signum() > 0 ? "+" : "")
        + PlanningPresentation.percentage(result.headroomPercentage().abs());
  }

  private static RetirementAgeAnalysisMoney.Scenario displayRetirementScenario(
      RetirementAgeAnalysis.ScenarioResult result) {
    String earliest =
        result.earliestSustainableRetirementAge() == null
            ? "None"
            : "Age "
                + result.earliestSustainableRetirementAge()
                + " · "
                + result.earliestSustainableRetirementYear();
    String headroom =
        switch (result.state()) {
          case IMMEDIATE_RETIREMENT_AVAILABLE -> "Now";
          case ALREADY_RETIRED -> "Planned age has passed";
          case EARLIER_RETIREMENT_AVAILABLE -> result.headroomYears() + " years earlier";
          case PLANNED_AGE_IS_BOUNDARY -> "0 years";
          case DELAY_REQUIRED -> result.delayYears() + " years delay";
          case NO_SUSTAINABLE_AGE -> "—";
          case NON_MONOTONIC_RESULT -> "Non-monotonic result";
        };
    String state =
        switch (result.state()) {
          case IMMEDIATE_RETIREMENT_AVAILABLE -> "Immediate retirement available";
          case ALREADY_RETIRED -> "Planned retirement age has passed";
          case EARLIER_RETIREMENT_AVAILABLE -> "Earlier retirement available";
          case PLANNED_AGE_IS_BOUNDARY -> "Planned age is boundary";
          case DELAY_REQUIRED -> "Delay required";
          case NO_SUSTAINABLE_AGE -> "No sustainable age";
          case NON_MONOTONIC_RESULT -> "Non-monotonic result";
        };
    return new RetirementAgeAnalysisMoney.Scenario(
        "Age " + result.plannedRetirementAge() + " · " + result.plannedRetirementYear(),
        earliest,
        headroom,
        state,
        result.plannedRetirementSustainable(),
        result.earliestSustainableRetirementAge() != null);
  }

  private SimulationSensitivityAnalysisMoney.Driver displaySensitivityResult(
      SimulationSensitivityResult result, CurrencyType display) {
    var baseline = result.baseline().sustainability();
    var adverse = result.adverse().sustainability();
    String status;
    if (!result.baseline().sustainable()) {
      int compared = adverse.totalUnfundedAmount().compareTo(baseline.totalUnfundedAmount());
      status =
          compared > 0
              ? "Worsens existing plan shortfall"
              : compared == 0 ? "Unchanged from Base" : "Improves Base shortfall";
    } else if (result.adverseCausesFailure()) {
      status =
          "Plan fails"
              + (adverse.firstFailureYear() == null ? "" : " in " + adverse.firstFailureYear());
    } else {
      status = "Plan remains sustainable";
    }
    return new SimulationSensitivityAnalysisMoney.Driver(
        result.driver().label(),
        result.perturbationLabel(),
        result.impact().name().replace('_', ' '),
        reserveCoverageDisplay(baseline) + " → " + reserveCoverageDisplay(adverse),
        signedMoney(toDisplay(result.finalNetWorthDelta(), display)),
        status,
        cell(result, result.lowerTestedValue(), result.lowerEvaluation(), display),
        cell(result, result.baseTestedValue(), result.baseline(), display),
        cell(result, result.higherTestedValue(), result.higherEvaluation(), display),
        result.moreHarmfulDirection().equals("Equivalent")
            ? "Equivalent outcomes."
            : result.moreHarmfulDirection()
                + " is more harmful; "
                + result.impact().name().replace('_', ' ').toLowerCase(),
        result.moreHarmfulDirection());
  }

  private SimulationSensitivityAnalysisMoney.Cell cell(
      SimulationSensitivityResult result,
      BigDecimal value,
      SimulationEvaluation evaluation,
      CurrencyType display) {
    if (value == null || evaluation == null) {
      return SimulationSensitivityAnalysisMoney.Cell.unavailable("Unavailable");
    }
    String tested =
        switch (result.driver()) {
          case INFLATION,
              RENTAL_INCOME_GROWTH,
              FIXED_INCOME_RETURN,
              EQUITY_RETURN,
              SPENDING_GROWTH ->
              PlanningPresentation.percentage(value);
          case RECURRING_SPENDING, PENSION ->
              toDisplay(value, display).stripTrailingZeros().toPlainString() + " " + display;
          default -> value.stripTrailingZeros().toPlainString();
        };
    var assessment = evaluation.sustainability();
    String state =
        assessment.sustainable()
            ? "Sustainable"
            : "Fails in "
                + (assessment.firstFailureYear() == null ? "—" : assessment.firstFailureYear());
    return new SimulationSensitivityAnalysisMoney.Cell(
        tested,
        true,
        state,
        assessment.firstFailureYear() == null ? "—" : assessment.firstFailureYear().toString(),
        toDisplay(assessment.minimumSpendableAssets(), display).stripTrailingZeros().toPlainString()
            + " "
            + display);
  }

  private BigDecimal toDisplay(BigDecimal amount, CurrencyType display) {
    return money.toDisplay(amount, display);
  }

  private static String signedMoney(BigDecimal amount) {
    String value = PlanningPresentation.wholeNumber(amount.abs());
    return (amount.signum() < 0 ? "−" : "+") + value;
  }

  private static String reserveCoverageDisplay(PlanSustainabilityAssessment assessment) {
    return assessment.recurringFundingGapRequired()
        ? PlanningPresentation.years(assessment.minimumSafeReserveCoverageYears()) + " years"
        : "Not required";
  }

  private static String riskInterpretation(
      SimulationSensitivityAnalysis analysis, List<SimulationSensitivityResult> risks) {
    if (risks.isEmpty()) return "No active external risk assumptions were available for testing.";
    if (risks.stream().allMatch(result -> result.impact() == SensitivityImpact.NEGLIGIBLE)) {
      return "None of the tested risk assumptions materially threatens plan sustainability.";
    }
    var top = risks.getFirst();
    if (analysis.baseline() != null && !analysis.baseline().sustainable()) {
      return top.driver().label()
          + " worsens the existing plan shortfall the most among tested risks.";
    }
    if (top.impact() == SensitivityImpact.CRITICAL) {
      return top.driver().label() + " is the largest tested risk to plan sustainability.";
    }
    if (top.isWealthOnly()) {
      return top.driver().label()
          + " is the largest tested wealth effect; plan sustainability remains intact.";
    }
    return top.driver().label() + " is the largest tested external risk to plan margin.";
  }

  private static String displayMoney(CurrencyType currency, BigDecimal amount) {
    return currency + " " + PlanningPresentation.wholeNumber(amount);
  }
}
