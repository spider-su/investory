package com.smartbox.investory.retirement.api.model;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Calendar period used by both partial current-year and full future transitions. */
public record SimulationPeriod(LocalDate startDate, LocalDate endDate, BigDecimal yearFraction) {
  private static final int SCALE = 12;
  private static final int WORKING_PRECISION = 40;
  private static final BigDecimal TWO = BigDecimal.valueOf(2);

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
    // Keep this calculation in decimal arithmetic. Converting financial inputs to double makes
    // replay depend on binary floating-point rounding and can move boundary values.
    MathContext context = new MathContext(WORKING_PRECISION, RoundingMode.HALF_EVEN);
    BigDecimal exponent = yearFraction;
    BigDecimal result =
        exp(ln(BigDecimal.ONE.add(rate, context), context).multiply(exponent, context), context)
            .subtract(BigDecimal.ONE, context);
    return result.setScale(SCALE, RoundingMode.HALF_UP);
  }

  private static BigDecimal ln(BigDecimal value, MathContext context) {
    BigDecimal z =
        value.subtract(BigDecimal.ONE, context).divide(value.add(BigDecimal.ONE, context), context);
    BigDecimal zSquared = z.multiply(z, context);
    BigDecimal term = z;
    BigDecimal sum = BigDecimal.ZERO;
    for (int denominator = 1; denominator <= 2000; denominator += 2) {
      BigDecimal addend = term.divide(BigDecimal.valueOf(denominator), context);
      sum = sum.add(addend, context);
      if (addend.abs().compareTo(BigDecimal.ONE.scaleByPowerOfTen(-context.getPrecision())) < 0)
        break;
      term = term.multiply(zSquared, context);
    }
    return sum.multiply(TWO, context);
  }

  private static BigDecimal exp(BigDecimal value, MathContext context) {
    BigDecimal sum = BigDecimal.ONE;
    BigDecimal term = BigDecimal.ONE;
    for (int n = 1; n <= 2000; n++) {
      term = term.multiply(value, context).divide(BigDecimal.valueOf(n), context);
      sum = sum.add(term, context);
      if (term.abs().compareTo(BigDecimal.ONE.scaleByPowerOfTen(-context.getPrecision())) < 0)
        break;
    }
    return sum;
  }
}
