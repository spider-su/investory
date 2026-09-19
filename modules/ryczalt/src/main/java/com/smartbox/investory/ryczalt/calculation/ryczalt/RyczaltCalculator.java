package com.smartbox.investory.ryczalt.calculation.ryczalt;

import com.smartbox.investory.ryczalt.calculation.RoundingPolicy;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Pure ryczałt calculation over normalized PLN revenue buckets. */
public final class RyczaltCalculator {
  private final String ruleVersion;

  public RyczaltCalculator() {
    this(RyczaltRules2026.VERSION);
  }

  public RyczaltCalculator(String ruleVersion) {
    this.ruleVersion = Objects.requireNonNull(ruleVersion, "ruleVersion");
  }

  public RyczaltCalculationResult calculate(RyczaltCalculationInput input) {
    Map<BigDecimal, BigDecimal> revenue = new TreeMap<>(input.revenueByRate());
    BigDecimal totalRevenue = revenue.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal healthDeduction =
        RoundingPolicy.roundHealthDeduction(
            input.healthContributionPaid().multiply(RyczaltRules2026.HEALTH_DEDUCTION_RATIO));
    BigDecimal available =
        input
            .socialContributionDeduction()
            .add(healthDeduction)
            .subtract(input.deductionsAlreadyConsumed())
            .max(BigDecimal.ZERO);
    BigDecimal used = available.min(totalRevenue.max(BigDecimal.ZERO));
    BigDecimal carryForward = available.subtract(used);

    Map<BigDecimal, BigDecimal> taxable = allocateAndRound(revenue, used);
    Map<BigDecimal, BigDecimal> tax = new TreeMap<>();
    taxable.forEach(
        (rate, base) -> tax.put(rate, RoundingPolicy.roundRyczaltTax(base.multiply(rate))));
    BigDecimal taxableBase =
        RoundingPolicy.roundRyczaltTaxableBase(
            taxable.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    BigDecimal calculatedTax = tax.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    return new RyczaltCalculationResult(
        totalRevenue,
        revenue,
        input.socialContributionDeduction(),
        input.healthContributionPaid(),
        healthDeduction,
        available,
        used,
        carryForward,
        taxable,
        tax,
        taxableBase,
        calculatedTax,
        ruleVersion);
  }

  private Map<BigDecimal, BigDecimal> allocateAndRound(
      Map<BigDecimal, BigDecimal> revenue, BigDecimal deductions) {
    Map<BigDecimal, BigDecimal> result = new TreeMap<>();
    BigDecimal total = revenue.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal remaining = deductions;
    int index = 0;
    for (var entry : revenue.entrySet()) {
      BigDecimal allocation;
      if (++index == revenue.size()) {
        allocation = RoundingPolicy.roundDeductionAllocation(remaining);
      } else if (total.signum() == 0) {
        allocation = BigDecimal.ZERO;
      } else {
        allocation =
            RoundingPolicy.roundDeductionAllocation(
                deductions
                    .multiply(entry.getValue())
                    .divide(total, 8, java.math.RoundingMode.HALF_UP));
        remaining = remaining.subtract(allocation);
      }
      result.put(
          entry.getKey(),
          RoundingPolicy.roundRyczaltTaxableBase(
              entry.getValue().subtract(allocation).max(BigDecimal.ZERO)));
    }
    return result;
  }
}
