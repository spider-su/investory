package com.smartbox.investory.retirement.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlanningPresentationTest {
  @Test
  void presentsLegacyFundingStrategyAsTheOnlySupportedPolicy() {
    assertEquals(
        "Reserve and harvest: Cash → Bonds above reserve → permitted Equities → Bonds below reserve → Real Estate",
        PlanningPresentation.fundingStrategy(SimulationFundingStrategy.SIMPLE_WATERFALL));
  }

  @Test
  void describesReserveAndHarvestFundingStrategy() {
    assertEquals(
        "Reserve and harvest: Cash → Bonds above reserve → permitted Equities → Bonds below reserve → Real Estate",
        PlanningPresentation.fundingStrategy(SimulationFundingStrategy.RESERVE_AND_HARVEST));
  }
}
