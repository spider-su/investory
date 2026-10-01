package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.profile.api.model.EconomicBucket;
import com.smartbox.investory.retirement.api.model.*;
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
    policy = policy == null ? RetirementFundingPolicy.defaults() : policy;
    BigDecimal reserveTarget =
        nz(policy.reserveTargetYears())
            .multiply(nz(annualCosts).subtract(nz(cashIncome)).max(ZERO));
    return simulate(
        start, annualCosts, cashIncome, policy, bondReturnRate, equityReturnRate, reserveTarget);
  }

  /** Simulates one year using the caller's canonical, year-specific Bond reserve floor. */
  public Result simulate(
      PlanningBuckets start,
      BigDecimal annualCosts,
      BigDecimal cashIncome,
      RetirementFundingPolicy policy,
      BigDecimal bondReturnRate,
      BigDecimal equityReturnRate,
      BigDecimal safeReserveTargetAmount) {
    return simulate(
        start,
        annualCosts,
        cashIncome,
        policy,
        bondReturnRate,
        equityReturnRate,
        safeReserveTargetAmount,
        ZERO,
        new BigDecimal("0.70"),
        new BigDecimal("0.30"));
  }

  /** A one-time real-estate sale can fund the remaining gap and reinvest its surplus. */
  public Result simulate(
      PlanningBuckets start,
      BigDecimal annualCosts,
      BigDecimal cashIncome,
      RetirementFundingPolicy policy,
      BigDecimal bondReturnRate,
      BigDecimal equityReturnRate,
      BigDecimal safeReserveTargetAmount,
      BigDecimal realEstateSaleFraction,
      BigDecimal bondReinvestmentShare,
      BigDecimal equityReinvestmentShare) {
    annualCosts = nz(annualCosts);
    cashIncome = nz(cashIncome);
    policy = policy == null ? RetirementFundingPolicy.defaults() : policy;
    safeReserveTargetAmount = nz(safeReserveTargetAmount).max(ZERO);
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
    BigDecimal bondNormalWithdrawal = gap.min(bonds.subtract(safeReserveTargetAmount).max(ZERO));
    bonds = bonds.subtract(bondNormalWithdrawal);
    gap = gap.subtract(bondNormalWithdrawal);
    BigDecimal equityWithdrawal =
        policy.allowEmergencyEquityWithdrawal() ? gap.min(equities) : ZERO;
    equities = equities.subtract(equityWithdrawal);
    gap = gap.subtract(equityWithdrawal);
    BigDecimal bondEmergencyWithdrawal = gap.min(bonds);
    bonds = bonds.subtract(bondEmergencyWithdrawal);
    gap = gap.subtract(bondEmergencyWithdrawal);
    BigDecimal realEstateWithdrawal = ZERO;
    BigDecimal realEstateSaleTransfer = ZERO;
    BigDecimal saleProceeds = ZERO;
    if (gap.signum() > 0 && nz(realEstateSaleFraction).signum() > 0) {
      saleProceeds = realEstate.multiply(realEstateSaleFraction.min(BigDecimal.ONE));
      realEstate = realEstate.subtract(saleProceeds);
      realEstateWithdrawal = gap.min(saleProceeds);
      gap = gap.subtract(realEstateWithdrawal);
      realEstateSaleTransfer = saleProceeds.subtract(realEstateWithdrawal);
      bonds = bonds.add(realEstateSaleTransfer.multiply(nz(bondReinvestmentShare)));
      equities = equities.add(realEstateSaleTransfer.multiply(nz(equityReinvestmentShare)));
    } else {
      realEstateWithdrawal = gap.min(realEstate);
      realEstate = realEstate.subtract(realEstateWithdrawal);
      gap = gap.subtract(realEstateWithdrawal);
    }
    BigDecimal harvest = ZERO;
    if (equityReturn.signum() > 0
        && nz(equityReturnRate).compareTo(policy.equityHarvestThresholdRate()) >= 0) {
      BigDecimal eligible = equityReturn.multiply(policy.equityHarvestShare());
      BigDecimal targetGap = safeReserveTargetAmount.subtract(bonds).max(ZERO);
      harvest = eligible.min(targetGap).min(equities).max(ZERO);
      equities = equities.subtract(harvest);
      bonds = bonds.add(harvest);
    }
    var rows = new EnumMap<EconomicBucket, BucketResult>(EconomicBucket.class);
    rows.put(
        EconomicBucket.LIQUID_CASH,
        new BucketResult(
            EconomicBucket.LIQUID_CASH,
            start.cash().startValue(),
            ZERO,
            ZERO,
            cashWithdrawal,
            cash.max(ZERO)));
    rows.put(
        EconomicBucket.FIXED_INCOME,
        new BucketResult(
            EconomicBucket.FIXED_INCOME,
            bondsStart,
            bondReturn,
            harvest.add(realEstateSaleTransfer.multiply(nz(bondReinvestmentShare))),
            bondNormalWithdrawal.add(bondEmergencyWithdrawal),
            bonds.max(ZERO)));
    rows.put(
        EconomicBucket.EQUITY,
        new BucketResult(
            EconomicBucket.EQUITY,
            equitiesStart,
            equityReturn,
            harvest.negate().add(realEstateSaleTransfer.multiply(nz(equityReinvestmentShare))),
            equityWithdrawal,
            equities.max(ZERO)));
    rows.put(
        EconomicBucket.REAL_ESTATE,
        new BucketResult(
            EconomicBucket.REAL_ESTATE,
            realEstateStart,
            realEstateReturn,
            realEstateSaleTransfer.negate(),
            realEstateWithdrawal,
            realEstate.max(ZERO)));
    return new Result(
        Map.copyOf(rows),
        gap,
        cashIncome,
        harvest,
        safeReserveTargetAmount,
        bondNormalWithdrawal,
        bondEmergencyWithdrawal,
        saleProceeds);
  }

  public record Result(
      Map<EconomicBucket, BucketResult> buckets,
      BigDecimal unfunded,
      BigDecimal cashIncome,
      BigDecimal equityHarvestToBonds,
      BigDecimal safeReserveTargetAmount,
      BigDecimal normalBondWithdrawal,
      BigDecimal emergencyBondWithdrawal,
      BigDecimal realEstateSaleProceeds) {
    public Result {
      buckets = Map.copyOf(buckets);
      unfunded = nz(unfunded);
      cashIncome = nz(cashIncome);
      equityHarvestToBonds = nz(equityHarvestToBonds);
      safeReserveTargetAmount = nz(safeReserveTargetAmount).max(ZERO);
      normalBondWithdrawal = nz(normalBondWithdrawal);
      emergencyBondWithdrawal = nz(emergencyBondWithdrawal);
      realEstateSaleProceeds = nz(realEstateSaleProceeds);
    }
  }

  private static BigDecimal nz(BigDecimal v) {
    return v == null ? ZERO : v;
  }
}
