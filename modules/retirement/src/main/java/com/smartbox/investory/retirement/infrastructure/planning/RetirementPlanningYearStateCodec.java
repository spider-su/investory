package com.smartbox.investory.retirement.infrastructure.planning;

import com.smartbox.investory.retirement.api.model.PlanningMetric;
import com.smartbox.investory.retirement.api.model.PlanningMetricValue;
import com.smartbox.investory.retirement.api.model.PlanningValueKind;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Serializes the complete reviewed-year aggregate into retirement_planning_years.state. */
@Component
public final class RetirementPlanningYearStateCodec {
  private final ObjectMapper json;

  public RetirementPlanningYearStateCodec(ObjectMapper json) {
    this.json = json;
  }

  public void readInto(RetirementPlanningYearEntity year) {
    try {
      State state =
          year.getState() == null || year.getState().isBlank()
              ? new State(null, null, null, null, null)
              : json.readValue(year.getState(), State.class);
      year.setBaselinePlanId(state.baselinePlanId());
      year.setBaselineCreatedAt(state.baselineCreatedAt());
      year.setClosedAt(state.closedAt());
      year.setReopenedAt(state.reopenedAt());
      Map<PlanningValueKind, Map<PlanningMetric, PlanningMetricValue>> values =
          new EnumMap<>(PlanningValueKind.class);
      for (PlanningValueKind kind : PlanningValueKind.values()) {
        Map<PlanningMetric, PlanningMetricValue> source =
            state.values() == null ? null : state.values().get(kind);
        values.put(
            kind, source == null ? new EnumMap<>(PlanningMetric.class) : new EnumMap<>(source));
      }
      year.setValues(values);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to read retirement planning-year state", e);
    }
  }

  public void writeFrom(RetirementPlanningYearEntity year) {
    try {
      year.setState(
          json.writeValueAsString(
              new State(
                  year.getBaselinePlanId(),
                  year.getBaselineCreatedAt(),
                  year.getClosedAt(),
                  year.getReopenedAt(),
                  year.getValues())));
    } catch (Exception e) {
      throw new IllegalStateException("Unable to write retirement planning-year state", e);
    }
  }

  private record State(
      Long baselinePlanId,
      java.time.Instant baselineCreatedAt,
      java.time.Instant closedAt,
      java.time.Instant reopenedAt,
      Map<PlanningValueKind, Map<PlanningMetric, PlanningMetricValue>> values) {}
}
