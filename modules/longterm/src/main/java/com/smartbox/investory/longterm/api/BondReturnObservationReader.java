package com.smartbox.investory.longterm.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Public factual Bond-return observation boundary for reporting consumers. */
public interface BondReturnObservationReader {
  /** Returns the current weighted effective Bond return, or {@code null} when unavailable. */
  BigDecimal currentWeightedEffectiveReturn(Long portfolioId, LocalDate date);
}
