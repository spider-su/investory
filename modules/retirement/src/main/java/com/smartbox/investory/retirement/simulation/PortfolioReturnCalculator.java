package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.retirement.profile.EconomicBucket;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Objects;

/** Applies deterministic scenario returns to liquid market buckets. */
public final class PortfolioReturnCalculator {
  private static final BigDecimal ZERO = BigDecimal.ZERO;

  private PortfolioReturnCalculator() {}

  public static EnumMap<EconomicBucket, BigDecimal> applyFullYear(
      EnumMap<EconomicBucket, BigDecimal> source, SimulationScenarioSettings settings) {
    return apply(source, settings, BigDecimal.ONE);
  }

  public static EnumMap<EconomicBucket, BigDecimal> apply(
      EnumMap<EconomicBucket, BigDecimal> source,
      SimulationScenarioSettings settings,
      BigDecimal fraction) {
    EnumMap<EconomicBucket, BigDecimal> result = new EnumMap<>(source);
    result.replaceAll(
        (bucket, value) ->
            value.multiply(BigDecimal.ONE.add(rateFor(bucket, settings).multiply(fraction))));
    return result;
  }

  public static BigDecimal rateFor(EconomicBucket bucket, SimulationScenarioSettings settings) {
    Objects.requireNonNull(bucket, "bucket");
    return switch (bucket) {
      case LIQUID_CASH -> settings.cashReturnRate();
      case FIXED_INCOME -> settings.fixedIncomeReturnRate();
      case EQUITY -> settings.equityReturnRate();
      case REAL_ESTATE -> ZERO;
      case OTHER -> settings.otherReturnRate();
    };
  }
}
