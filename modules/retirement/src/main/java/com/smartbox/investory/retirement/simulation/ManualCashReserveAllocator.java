package com.smartbox.investory.retirement.simulation;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deterministic withdrawal from factual manual cash reserves, ordered by configured return rate.
 */
public final class ManualCashReserveAllocator {
  private static final BigDecimal ZERO = BigDecimal.ZERO;

  private ManualCashReserveAllocator() {}

  public static Result withdraw(
      Map<Long, BigDecimal> source, Map<Long, BigDecimal> reserveRates, BigDecimal amount) {
    Map<Long, BigDecimal> result = new LinkedHashMap<>(source);
    BigDecimal left = amount.max(ZERO);
    BigDecimal funded = ZERO;
    for (Long id :
        reserveRates.keySet().stream()
            .sorted(
                Comparator.comparing((Long id) -> reserveRates.getOrDefault(id, ZERO))
                    .thenComparing(Long::compareTo))
            .toList()) {
      BigDecimal used = result.getOrDefault(id, ZERO).min(left).max(ZERO);
      result.put(id, result.getOrDefault(id, ZERO).subtract(used));
      left = left.subtract(used);
      funded = funded.add(used);
    }
    return new Result(result, funded);
  }

  public record Result(Map<Long, BigDecimal> values, BigDecimal fundedAmount) {}
}
