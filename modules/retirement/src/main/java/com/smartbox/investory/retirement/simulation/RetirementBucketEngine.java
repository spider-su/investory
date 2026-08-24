package com.smartbox.investory.retirement.simulation;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/** One-year aggregate bucket economics. It never reads source-domain state. */
public final class RetirementBucketEngine {
  private static final BigDecimal ZERO = BigDecimal.ZERO;

  public Result simulate(
      PlanningBuckets start,
      BigDecimal annualCosts,
      BigDecimal cashIncome,
      RetirementFundingPolicy policy) {
    return simulate(
        start,
        annualCosts,
        cashIncome,
        policy,
        start.bonds().plannedYieldRate(),
        start.equities().plannedYieldRate());
  }

  public Result simulate(
      PlanningBuckets start,
      BigDecimal annualCosts,
      BigDecimal cashIncome,
      RetirementFundingPolicy policy,
      BigDecimal bondReturnRate,
      BigDecimal equityReturnRate) {
    annualCosts = nz(annualCosts);
    cashIncome = nz(cashIncome);
    policy = policy == null ? RetirementFundingPolicy.defaults() : policy;
    BigDecimal cash = start.cash().startValue();
    BigDecimal bondsStart = start.bonds().startValue(),
        equitiesStart = start.equities().startValue(),
        realEstateStart = start.realEstate().startValue();
    BigDecimal bondReturn = bondsStart.multiply(nz(bondReturnRate));
    BigDecimal equityReturn = equitiesStart.multiply(nz(equityReturnRate));
    BigDecimal realEstateReturn = realEstateStart.multiply(start.realEstateGrowthRate());
    BigDecimal bonds = bondsStart.add(bondReturn),
        equities = equitiesStart.add(equityReturn),
        realEstate = realEstateStart.add(realEstateReturn);
    BigDecimal gap = annualCosts.subtract(cashIncome).max(ZERO);
    BigDecimal cashWithdrawal = gap.min(cash);
    cash = cash.subtract(cashWithdrawal);
    gap = gap.subtract(cashWithdrawal);
    BigDecimal bondWithdrawal = gap.min(bonds);
    bonds = bonds.subtract(bondWithdrawal);
    gap = gap.subtract(bondWithdrawal);
    BigDecimal equityWithdrawal =
        policy.allowEmergencyEquityWithdrawal() ? gap.min(equities) : ZERO;
    equities = equities.subtract(equityWithdrawal);
    gap = gap.subtract(equityWithdrawal);
    BigDecimal realEstateWithdrawal = gap.min(realEstate);
    realEstate = realEstate.subtract(realEstateWithdrawal);
    gap = gap.subtract(realEstateWithdrawal);
    BigDecimal harvest = ZERO;
    if (equityReturn.signum() > 0
        && nz(equityReturnRate).compareTo(policy.equityHarvestThresholdRate())
            >= 0) {
      BigDecimal eligible = equityReturn.multiply(policy.equityHarvestShare());
      BigDecimal targetGap = start.bonds().targetValue().subtract(bonds).max(ZERO);
      harvest = eligible.min(targetGap).min(equities).max(ZERO);
      equities = equities.subtract(harvest);
      bonds = bonds.add(harvest);
    }
    var rows = new EnumMap<BucketType, BucketResult>(BucketType.class);
    rows.put(
        BucketType.CASH,
        new BucketResult(
            BucketType.CASH,
            start.cash().startValue(),
            ZERO,
            ZERO,
            cashWithdrawal,
            cash.max(ZERO)));
    rows.put(
        BucketType.BONDS,
        new BucketResult(
            BucketType.BONDS, bondsStart, bondReturn, harvest, bondWithdrawal, bonds.max(ZERO)));
    rows.put(
        BucketType.EQUITIES,
        new BucketResult(
            BucketType.EQUITIES,
            equitiesStart,
            equityReturn,
            harvest.negate(),
            equityWithdrawal,
            equities.max(ZERO)));
    rows.put(
        BucketType.REAL_ESTATE,
        new BucketResult(
            BucketType.REAL_ESTATE,
            realEstateStart,
            realEstateReturn,
            ZERO,
            realEstateWithdrawal,
            realEstate.max(ZERO)));
    return new Result(Map.copyOf(rows), gap, cashIncome, harvest);
  }

  public record BucketResult(
      BucketType bucket,
      BigDecimal startValue,
      BigDecimal returnAmount,
      BigDecimal refill,
      BigDecimal withdrawal,
      BigDecimal expectedEndValue) {
    /** Signed net internal transfer. Kept separate from the legacy component name. */
    public BigDecimal transfer() {
      return refill;
    }
  }

  public record Result(
      Map<BucketType, BucketResult> buckets,
      BigDecimal unfunded,
      BigDecimal cashIncome,
      BigDecimal equityHarvestToBonds) {
    public Result {
      buckets = Map.copyOf(buckets);
      unfunded = nz(unfunded);
    }
  }

  private static BigDecimal nz(BigDecimal v) {
    return v == null ? ZERO : v;
  }
}
