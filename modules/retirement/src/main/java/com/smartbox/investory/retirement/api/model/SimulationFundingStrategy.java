package com.smartbox.investory.retirement.api.model;

/** Determines how the simulator funds the portfolio gap; it never creates real transactions. */
public enum SimulationFundingStrategy {
  SIMPLE_WATERFALL,
  RESERVE_AND_HARVEST
}
