package com.smartbox.investory.retirement.planning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Calendar period used by both partial current-year and full future transitions. */
public record SimulationPeriod(LocalDate startDate, LocalDate endDate, BigDecimal yearFraction) {
  private static final int SCALE = 12;

  public SimulationPeriod {
    if (startDate == null || endDate == null || endDate.isBefore(startDate))
      throw new IllegalArgumentException("Invalid simulation period");
    if (startDate.getYear() != endDate.getYear())
      throw new IllegalArgumentException("Simulation period must stay within one calendar year");
    yearFraction = yearFraction == null ? fraction(startDate, endDate) : yearFraction;
    if (yearFraction.signum() < 0)
      throw new IllegalArgumentException("Invalid simulation year fraction");
  }

  public static SimulationPeriod of(LocalDate start, LocalDate end) {
    return new SimulationPeriod(start, end, fraction(start, end));
  }

  public static BigDecimal fraction(LocalDate start, LocalDate end) {
    if (start == null || end == null || end.isBefore(start) || start.getYear() != end.getYear())
      throw new IllegalArgumentException("Simulation period must stay within one calendar year");
    long days = ChronoUnit.DAYS.between(start, end.plusDays(1));
    int daysInYear = start.lengthOfYear();
    return BigDecimal.valueOf(days)
        .divide(BigDecimal.valueOf(daysInYear), SCALE, RoundingMode.HALF_UP);
  }

  public BigDecimal prorate(BigDecimal annualAmount) {
    return (annualAmount == null ? BigDecimal.ZERO : annualAmount).multiply(yearFraction);
  }

  public BigDecimal compoundRate(BigDecimal annualRate) {
    BigDecimal rate = annualRate == null ? BigDecimal.ZERO : annualRate;
    if (rate.compareTo(BigDecimal.ONE.negate()) <= 0)
      throw new IllegalArgumentException("Annual rate must be greater than -100%");
    // Fractional powers are deliberately isolated here. This helper is presentation/planning
    // math, not an asset-domain transition; valueOf plus explicit rounding keeps replay stable.
    double compounded = Math.pow(1d + rate.doubleValue(), yearFraction.doubleValue()) - 1d;
    return BigDecimal.valueOf(compounded).setScale(SCALE, RoundingMode.HALF_UP);
  }
}
