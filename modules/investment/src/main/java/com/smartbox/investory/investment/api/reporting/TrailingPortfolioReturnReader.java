package com.smartbox.investory.investment.api.reporting;

import java.math.BigDecimal;
import java.time.YearMonth;

/** Narrow performance boundary for consumers that need one aggregate return. */
public interface TrailingPortfolioReturnReader {
  /** Returns the trailing return for one portfolio, or {@code null} when no observation exists. */
  BigDecimal returnPercentage(Long portfolioId, YearMonth from, YearMonth to);
}
