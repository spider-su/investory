package com.smartbox.investory.investment.api;

import java.math.BigDecimal;

/** Stable black-box annual investment projection boundary. */
public interface InvestmentAnnualProjectionApi {
  AnnualProjection project(ProjectionRequest request);

  record ProjectionRequest(
      int year, BigDecimal startValue, BigDecimal annualReturnRate, BigDecimal withdrawal, Source source) {
    public ProjectionRequest {
      startValue = nz(startValue);
      annualReturnRate = nz(annualReturnRate);
      withdrawal = nz(withdrawal).max(BigDecimal.ZERO);
      source = source == null ? Source.PROJECTED : source;
    }
  }

  record AnnualProjection(
      int year,
      BigDecimal startValue,
      BigDecimal annualReturnAmount,
      BigDecimal withdrawal,
      BigDecimal endValue,
      Source source) {}

  enum Source {
    ACTUAL,
    PROJECTED
  }

  private static BigDecimal nz(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
