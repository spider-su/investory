package com.smartbox.investory.retirement.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlanningPresentationTest {
  @Test
  void describesSimpleWaterfallFundingStrategy() {
    assertEquals(
        "Fixed funding order: income → reserve → permitted Long-Term capital → Investment",
        PlanningPresentation.fundingStrategy(SimulationFundingStrategy.SIMPLE_WATERFALL));
  }

  @Test
  void describesReserveAndHarvestFundingStrategy() {
    assertEquals(
        "Reserve and harvest funding order: manual reserve → market cash → spendable fixed income → permitted emergency equity",
        PlanningPresentation.fundingStrategy(SimulationFundingStrategy.RESERVE_AND_HARVEST));
  }
}
