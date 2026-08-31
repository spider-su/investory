package com.smartbox.investory.services.portfolio.read;

import java.math.BigDecimal;

/** Immutable position value needed by higher-level application composition. */
public record BrokeragePositionSnapshot(String symbol, BigDecimal value) {}
