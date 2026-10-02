package com.smartbox.investory.retirement.api.model;

/** Determines how the simulator funds the portfolio gap; it never creates real transactions. */
public enum SimulationFundingStrategy {
  /** Legacy persisted/API name. Normalized to {@link #RESERVE_AND_HARVEST}. */
  @Deprecated
  SIMPLE_WATERFALL,
  RESERVE_AND_HARVEST
}
