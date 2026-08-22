package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.investment.api.InvestmentAnnualProjectionApi;
import java.math.BigDecimal;

/** Moves eligible positive investment gains to an under-target reserve after funding. */
public final class RetirementReserveRebalancer {
  private static final BigDecimal ZERO = BigDecimal.ZERO;

  public Result rebalance(BigDecimal reserveBeforeHarvest, BigDecimal reserveTarget,
      InvestmentAnnualProjectionApi.AnnualProjection investment, BigDecimal annualReturnRate,
      RetirementFundingPolicy policy) {
    BigDecimal shortfall = nz(reserveTarget).subtract(nz(reserveBeforeHarvest)).max(ZERO);
    BigDecimal gain = nz(investment.annualReturnAmount());
    if (shortfall.signum() == 0 || gain.signum() <= 0
        || annualReturnRate.compareTo(policy.equityHarvestThresholdRate()) < 0) {
      return new Result(investment, ZERO);
    }
    BigDecimal harvest = gain.multiply(policy.equityHarvestShare())
        .min(shortfall).min(nz(investment.endValue()).max(ZERO));
    if (harvest.signum() == 0) return new Result(investment, ZERO);
    var adjusted = new InvestmentAnnualProjectionApi.AnnualProjection(
        investment.year(), investment.startValue(), investment.externalContribution(),
        investment.annualReturnAmount(), investment.withdrawal(),
        investment.endValue().subtract(harvest).max(ZERO), investment.source());
    return new Result(adjusted, harvest);
  }

  public record Result(InvestmentAnnualProjectionApi.AnnualProjection investment,
      BigDecimal harvestToReserve) {}

  private static BigDecimal nz(BigDecimal value) { return value == null ? ZERO : value; }
}
