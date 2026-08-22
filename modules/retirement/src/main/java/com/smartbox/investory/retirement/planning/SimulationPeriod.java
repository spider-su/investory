package com.smartbox.investory.retirement.planning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Calendar period used by both partial current-year and full future transitions. */
public record SimulationPeriod(LocalDate startDate, LocalDate endDate, BigDecimal yearFraction) {
  public SimulationPeriod {
    if (startDate == null || endDate == null || endDate.isBefore(startDate))
      throw new IllegalArgumentException("Invalid simulation period");
    yearFraction = yearFraction == null ? fraction(startDate, endDate) : yearFraction;
  }

  public static SimulationPeriod of(LocalDate start, LocalDate end) {
    return new SimulationPeriod(start, end, fraction(start, end));
  }

  public static BigDecimal fraction(LocalDate start, LocalDate end) {
    long days = ChronoUnit.DAYS.between(start, end.plusDays(1));
    int daysInYear = start.lengthOfYear();
    return BigDecimal.valueOf(days).divide(BigDecimal.valueOf(daysInYear), 12, RoundingMode.HALF_UP);
  }

  public BigDecimal prorate(BigDecimal annualAmount) {
    return (annualAmount == null ? BigDecimal.ZERO : annualAmount).multiply(yearFraction);
  }

  public BigDecimal compoundRate(BigDecimal annualRate) {
    double rate = annualRate == null ? 0d : annualRate.doubleValue();
    return BigDecimal.valueOf(Math.pow(1d + rate, yearFraction.doubleValue()) - 1d);
  }
}
