package com.smartbox.investory.retirement.web;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.RetirementProjectionContext;
import com.smartbox.investory.retirement.api.model.SimulationDecisionSummary;
import com.smartbox.investory.retirement.api.model.SimulationResult;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import com.smartbox.investory.retirement.api.model.SimulationYear;
import com.smartbox.investory.shared.currency.CurrencyType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

public final class RetirementProjectionContracts {
  private RetirementProjectionContracts() {}

  /** Shared request contract for projection and analysis endpoints. */
  public record ProjectionParameters(
      Long planId,
      @Min(0) @Max(150) Integer defaultCurrentAge,
      @Min(0) @Max(150) Integer defaultEndAge) {}

  public record ProjectionResponse(
      Long portfolioId,
      Long planId,
      CurrencyType currency,
      int startYear,
      int currentAge,
      int endAge,
      Map<SimulationScenario, ScenarioProjectionDto> scenarios) {
    static ProjectionResponse from(
        Long portfolioId, Long effectivePlanId, RetirementProjectionContext projection) {
      return new ProjectionResponse(
          portfolioId,
          effectivePlanId,
          projection.projectedProfile().currency(),
          projection.projectedAssumptions().startYear(),
          projection.projectedAssumptions().currentAge(),
          projection.projectedAssumptions().endAge(),
          projection.scenarioResults().entrySet().stream()
              .collect(
                  Collectors.toUnmodifiableMap(
                      Map.Entry::getKey,
                      entry ->
                          ScenarioProjectionDto.from(
                              entry.getValue(), projection.summaries().get(entry.getKey())))));
    }
  }

  public record ScenarioProjectionDto(
      boolean failed,
      Integer firstFailureAge,
      BigDecimal firstFailureShortfall,
      BigDecimal totalUnfunded,
      BigDecimal finalNetWorth,
      BigDecimal finalLiquidAssets,
      BigDecimal finalIlliquidAssets,
      java.util.List<ProjectionYearDto> years) {
    static ScenarioProjectionDto from(SimulationResult result, SimulationDecisionSummary summary) {
      return new ScenarioProjectionDto(
          result.simulationFailed(),
          result.failureAge(),
          result.firstFailureShortfall(),
          result.totalUnfundedAmount(),
          summary == null ? BigDecimal.ZERO : summary.finalNetWorth(),
          summary == null ? BigDecimal.ZERO : summary.finalLiquidAssets(),
          summary == null ? BigDecimal.ZERO : summary.finalIlliquidAssets(),
          result.years().stream().map(ProjectionYearDto::from).toList());
    }
  }

  public record ProjectionYearDto(
      int year,
      int age,
      BigDecimal totalExpenses,
      BigDecimal totalIncome,
      BigDecimal actualWithdrawal,
      BigDecimal endNetWorth,
      BigDecimal unfunded,
      boolean failed) {
    static ProjectionYearDto from(SimulationYear source) {
      return new ProjectionYearDto(
          source.year(),
          source.age(),
          source.totalExpenses(),
          source.totalIncome(),
          source.actualPortfolioWithdrawal(),
          source.endNetWorth(),
          source.unfundedAmount(),
          source.failed());
    }
  }
}
