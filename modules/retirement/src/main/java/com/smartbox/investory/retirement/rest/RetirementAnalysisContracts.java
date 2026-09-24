package com.smartbox.investory.retirement.rest;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.AnalysisAvailability;
import com.smartbox.investory.retirement.api.model.RetirementAgeAnalysis;
import com.smartbox.investory.retirement.api.model.RetirementAnalysisResult;
import com.smartbox.investory.retirement.api.model.RetirementAnalysisState;
import com.smartbox.investory.retirement.api.model.SimulationChartData;
import com.smartbox.investory.retirement.api.model.SimulationSensitivityAnalysis;
import com.smartbox.investory.retirement.api.model.SustainableSpendingAnalysis;

/** HTTP input for analysis. The server owns projection construction. */
public final class RetirementAnalysisContracts {
  private RetirementAnalysisContracts() {}

  /** Stable HTTP envelope; domain analysis internals are not serialized as the root response. */
  public record AnalysisResponse(
      RetirementAnalysisState state,
      boolean available,
      AnalysisValue<SustainableSpendingAnalysis> sustainableSpending,
      AnalysisValue<RetirementAgeAnalysis> retirementAge,
      AnalysisValue<SimulationSensitivityAnalysis> sensitivity,
      SimulationChartData charts) {
    static AnalysisResponse from(RetirementAnalysisResult source) {
      return new AnalysisResponse(
          source.state(),
          source.available(),
          AnalysisValue.from(source.sustainableSpending()),
          AnalysisValue.from(source.retirementAge()),
          AnalysisValue.from(source.sensitivity()),
          source.charts());
    }

    public RetirementAnalysisResult toDomain() {
      return RetirementAnalysisContracts.toDomain(this);
    }
  }

  public record AnalysisValue<T>(boolean available, T value, String reason) {
    static <T> AnalysisValue<T> from(AnalysisAvailability<T> source) {
      return new AnalysisValue<>(source.available(), source.value().orElse(null), source.reason());
    }
  }

  public static RetirementAnalysisResult toDomain(AnalysisResponse source) {
    return new RetirementAnalysisResult(
        source.state(),
        toAvailability(source.sustainableSpending()),
        toAvailability(source.retirementAge()),
        toAvailability(source.sensitivity()),
        source.charts());
  }

  private static <T> AnalysisAvailability<T> toAvailability(AnalysisValue<T> source) {
    return source.available()
        ? new AnalysisAvailability.Available<>(source.value())
        : new AnalysisAvailability.Unavailable<>(source.reason());
  }
}
