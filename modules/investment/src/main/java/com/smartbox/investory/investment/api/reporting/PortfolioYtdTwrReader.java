package com.smartbox.investory.investment.api.reporting;

import com.smartbox.investory.investment.api.reporting.model.ReturnMetric;

/** Public read boundary for Investment's canonical current-year portfolio TWR. */
public interface PortfolioYtdTwrReader {
  ReturnMetric ytdTwr(Long portfolioId);
}
