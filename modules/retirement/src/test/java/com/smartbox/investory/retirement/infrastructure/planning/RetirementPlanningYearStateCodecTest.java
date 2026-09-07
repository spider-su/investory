package com.smartbox.investory.retirement.infrastructure.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.retirement.api.model.PlanningMetric;
import com.smartbox.investory.retirement.api.model.PlanningMetricValue;
import com.smartbox.investory.retirement.api.model.PlanningValueKind;
import com.smartbox.investory.retirement.api.model.PlanningValueSource;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RetirementPlanningYearStateCodecTest {
  private final RetirementPlanningYearStateCodec codec =
      new RetirementPlanningYearStateCodec(new ObjectMapper());

  @Test
  void roundTripsTheWholeReviewedYearAggregate() {
    RetirementPlanningYearEntity source = new RetirementPlanningYearEntity();
    source.setBaselinePlanId(9L);
    source.setBaselineCreatedAt(Instant.parse("2026-01-02T00:00:00Z"));
    source.setClosedAt(Instant.parse("2027-01-01T00:00:00Z"));
    source
        .getValues()
        .get(PlanningValueKind.ACTUAL)
        .put(
            PlanningMetric.NET_WORTH,
            new PlanningMetricValue(
                PlanningMetric.NET_WORTH,
                new BigDecimal("123.45"),
                null,
                PlanningValueSource.ACCOUNTING_DERIVED,
                "closed"));

    codec.writeFrom(source);
    RetirementPlanningYearEntity restored = new RetirementPlanningYearEntity();
    restored.setState(source.getState());
    codec.readInto(restored);

    assertEquals(9L, restored.getBaselinePlanId());
    assertEquals(source.getClosedAt(), restored.getClosedAt());
    assertEquals(
        new BigDecimal("123.45"),
        restored.getValues().get(PlanningValueKind.ACTUAL).get(PlanningMetric.NET_WORTH).value());
  }
}
