package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.profile.api.model.EconomicBucket;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.profile.api.model.ProfileAllocation;
import com.smartbox.investory.profile.api.model.ProfileAssetProjection;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.ForwardSimulationContext;
import com.smartbox.investory.retirement.api.model.PlanningBuckets;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.retirement.api.model.SimulationLifecyclePhase;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import com.smartbox.investory.retirement.api.model.SimulationYear;
import com.smartbox.investory.retirement.simulation.ForwardSimulationContextFactory;
import com.smartbox.investory.retirement.simulation.RetirementSimulation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Bridges live state to the next projection without reproducing asset calculations. */
@Service
public class CurrentYearProjectionBridge {
  private static final BigDecimal ZERO = BigDecimal.ZERO;
  private final Clock clock;
  private final RetirementSimulation simulations;
  private final ForwardSimulationContextFactory contexts;

  public CurrentYearProjectionBridge(Clock clock, RetirementSimulation simulations) {
    this(clock, simulations, new ForwardSimulationContextFactory(clock));
  }

  @Autowired
  public CurrentYearProjectionBridge(
      Clock clock, RetirementSimulation simulations, ForwardSimulationContextFactory contexts) {
    this.clock = clock;
    this.simulations = simulations;
    this.contexts = contexts;
  }

  public InvestmentProfile projectCurrentYearEnd(
      InvestmentProfile profile, SimulationAssumptions assumptions) {
    return projectCurrentYearEnd(contexts.create(profile, assumptions)).bridgedProfile();
  }

  public CurrentYearBridgeResult projectCurrentYearEnd(ForwardSimulationContext context) {
    InvestmentProfile profile = context.currentProfile();
    SimulationAssumptions assumptions = context.originalAssumptions();
    if (!context.requiresCurrentYearBridge()) {
      return result(
          context,
          profile,
          ZERO,
          null,
          ZERO,
          ZERO,
          ZERO,
          ZERO,
          ZERO,
          ZERO,
          ZERO,
          currentBoundaries(profile));
    }
    int year = context.asOfYear();
    BigDecimal fraction = remainingYearFraction(year);
    SimulationAssumptions currentYearAssumptions =
        assumptions.rebasedTo(context.asOfAge(), year, context.currentYearEvents());
    SimulationYear projected =
        simulations.simulateRemainingYear(
            profile, currentYearAssumptions, SimulationScenario.BASE, context.asOfYear(), fraction);
    BigDecimal spending = projected.totalExpenses().subtract(projected.eventExpenses());
    BigDecimal passive = projected.passiveIncome();
    BigDecimal pension = projected.pensionIncome();
    BigDecimal funding = projected.requiredPortfolioFunding();
    BigDecimal contribution = projected.preRetirementContribution();
    Map<EconomicBucket, CurrentYearBridgeResult.BucketBoundary> boundaries =
        projectedBoundaries(projected);
    InvestmentProfile bridgedProfile = rebaseSpendableState(profile, boundaries);
    return result(
        context,
        bridgedProfile,
        fraction,
        contribution,
        spending,
        funding,
        passive,
        pension,
        projected.rentalIncome().add(projected.bondIncome()),
        ZERO,
        projected.equityGain(),
        boundaries);
  }

  /** Carry the returned four-bucket expected end state into the next projected year. */
  private static InvestmentProfile rebaseSpendableState(
      InvestmentProfile profile,
      Map<EconomicBucket, CurrentYearBridgeResult.BucketBoundary> boundaries) {
    BigDecimal cashStart = boundaries.get(EconomicBucket.LIQUID_CASH).startValue();
    BigDecimal cashEnd = boundaries.get(EconomicBucket.LIQUID_CASH).expectedEndValue();
    BigDecimal bondStart = boundaries.get(EconomicBucket.FIXED_INCOME).startValue();
    BigDecimal bondEnd = boundaries.get(EconomicBucket.FIXED_INCOME).expectedEndValue();
    BigDecimal equityStart = boundaries.get(EconomicBucket.EQUITY).startValue();
    BigDecimal equityEnd = boundaries.get(EconomicBucket.EQUITY).expectedEndValue();
    BigDecimal realEstateStart = boundaries.get(EconomicBucket.REAL_ESTATE).startValue();
    BigDecimal realEstateEnd = boundaries.get(EconomicBucket.REAL_ESTATE).expectedEndValue();
    BigDecimal marketDelta = cashEnd.subtract(cashStart).add(equityEnd.subtract(equityStart));
    BigDecimal totalDelta =
        marketDelta.add(bondEnd.subtract(bondStart)).add(realEstateEnd.subtract(realEstateStart));
    BigDecimal marketEnd = zero(profile.marketPortfolioValue()).add(marketDelta);
    BigDecimal liquidEnd =
        zero(profile.liquidAssets())
            .add(cashEnd.subtract(cashStart))
            .add(bondEnd.subtract(bondStart))
            .add(equityEnd.subtract(equityStart));
    BigDecimal illiquidEnd =
        zero(profile.illiquidAssets()).add(realEstateEnd.subtract(realEstateStart));
    return new InvestmentProfile(
        profile.portfolioId(),
        profile.currency(),
        marketEnd,
        profile.longTermAssetValue(),
        zero(profile.totalNetWorth()).add(totalDelta),
        liquidEnd,
        illiquidEnd,
        rebaseAllocations(profile.allocations(), boundaries),
        profile.currentRentalIncome(),
        profile.currentBondIncome(),
        rebasePlanningState(profile.longTermPlanningState(), boundaries),
        cashEnd,
        equityEnd,
        profile.incomeSummary(),
        profile.allocationReconciliation());
  }

  /** Rebase frozen asset facts so aggregate bucket consumers see the bridged end state. */
  private static ProfileAssetProjection rebasePlanningState(
      ProfileAssetProjection state,
      Map<EconomicBucket, CurrentYearBridgeResult.BucketBoundary> boundaries) {
    if (state.assets().isEmpty()) return state;
    BigDecimal bondTotal =
        state.assets().stream()
            .filter(
                asset ->
                    asset.type() == LongTermAssetType.BOND
                        || asset.type() == LongTermAssetType.DEPOSIT)
            .map(asset -> zero(asset.currentValue()))
            .reduce(ZERO, BigDecimal::add);
    BigDecimal realEstateTotal =
        state.assets().stream()
            .filter(asset -> asset.type() == LongTermAssetType.REAL_ESTATE)
            .map(asset -> zero(asset.currentValue()))
            .reduce(ZERO, BigDecimal::add);
    return new ProfileAssetProjection(
        state.assets().stream()
            .map(
                asset -> {
                  BigDecimal total =
                      asset.type() == LongTermAssetType.REAL_ESTATE
                          ? realEstateTotal
                          : asset.type() == LongTermAssetType.BOND
                                  || asset.type() == LongTermAssetType.DEPOSIT
                              ? bondTotal
                              : ZERO;
                  if (total.signum() == 0) return asset;
                  EconomicBucket bucket =
                      asset.type() == LongTermAssetType.REAL_ESTATE
                          ? EconomicBucket.REAL_ESTATE
                          : asset.type() == LongTermAssetType.BOND
                                  || asset.type() == LongTermAssetType.DEPOSIT
                              ? EconomicBucket.FIXED_INCOME
                              : null;
                  if (bucket == null) return asset;
                  BigDecimal target = boundaries.get(bucket).expectedEndValue();
                  return asset.withCurrentValue(
                      target
                          .multiply(zero(asset.currentValue()))
                          .divide(total, 8, RoundingMode.HALF_UP));
                })
            .toList(),
        state.rentalIncomeGrowthRate(),
        state.rentalIncomeBaseYear(),
        state.source());
  }

  private static List<ProfileAllocation> rebaseAllocations(
      List<ProfileAllocation> allocations,
      Map<EconomicBucket, CurrentYearBridgeResult.BucketBoundary> boundaries) {
    if (allocations.isEmpty()) return allocations;
    EnumMap<EconomicBucket, BigDecimal> values = new EnumMap<>(EconomicBucket.class);
    allocations.forEach(a -> values.merge(a.bucket(), zero(a.value()), BigDecimal::add));
    values.put(
        EconomicBucket.LIQUID_CASH, boundaries.get(EconomicBucket.LIQUID_CASH).expectedEndValue());
    values.put(
        EconomicBucket.FIXED_INCOME,
        boundaries.get(EconomicBucket.FIXED_INCOME).expectedEndValue());
    values.put(EconomicBucket.EQUITY, boundaries.get(EconomicBucket.EQUITY).expectedEndValue());
    values.put(
        EconomicBucket.REAL_ESTATE, boundaries.get(EconomicBucket.REAL_ESTATE).expectedEndValue());
    BigDecimal total = values.values().stream().reduce(ZERO, BigDecimal::add);
    return allocations.stream()
        .map(
            a -> {
              BigDecimal originalBucketTotal =
                  allocations.stream()
                      .filter(candidate -> candidate.bucket() == a.bucket())
                      .map(candidate -> zero(candidate.value()))
                      .reduce(ZERO, BigDecimal::add);
              BigDecimal bucketValue = values.getOrDefault(a.bucket(), ZERO);
              BigDecimal value =
                  originalBucketTotal.signum() == 0
                      ? ZERO
                      : bucketValue
                          .multiply(zero(a.value()))
                          .divide(originalBucketTotal, 8, RoundingMode.HALF_UP);
              return new ProfileAllocation(
                  a.bucket(),
                  value,
                  total.signum() == 0 ? ZERO : value.divide(total, 8, RoundingMode.HALF_UP),
                  a.liquidity(),
                  a.assetHorizon());
            })
        .toList();
  }

  private static Map<EconomicBucket, CurrentYearBridgeResult.BucketBoundary> currentBoundaries(
      InvestmentProfile profile) {
    PlanningBuckets buckets = PlanningBuckets.fromProfileWithBondYield(profile, ZERO, ZERO);
    EnumMap<EconomicBucket, CurrentYearBridgeResult.BucketBoundary> result =
        new EnumMap<>(EconomicBucket.class);
    buckets
        .asMap()
        .forEach(
            (bucket, value) ->
                result.put(
                    bucket,
                    new CurrentYearBridgeResult.BucketBoundary(
                        value.startValue(), value.startValue())));
    return result;
  }

  private static Map<EconomicBucket, CurrentYearBridgeResult.BucketBoundary> projectedBoundaries(
      SimulationYear projected) {
    EnumMap<EconomicBucket, CurrentYearBridgeResult.BucketBoundary> result =
        new EnumMap<>(EconomicBucket.class);
    result.put(EconomicBucket.LIQUID_CASH, boundary(projected.cashStart(), projected.cashEnd()));
    result.put(
        EconomicBucket.FIXED_INCOME,
        boundary(projected.fixedIncomeStart(), projected.fixedIncomeEnd()));
    result.put(EconomicBucket.EQUITY, boundary(projected.equityStart(), projected.equityEnd()));
    result.put(
        EconomicBucket.REAL_ESTATE,
        boundary(projected.realEstateStart(), projected.realEstateEnd()));
    return result;
  }

  private static CurrentYearBridgeResult.BucketBoundary boundary(BigDecimal start, BigDecimal end) {
    BigDecimal actualStart = zero(start);
    return new CurrentYearBridgeResult.BucketBoundary(actualStart, zero(end));
  }

  private static BigDecimal zero(BigDecimal value) {
    return value == null ? ZERO : value;
  }

  BigDecimal remainingYearFraction(int year) {
    // The current date belongs to live/current facts; forward projection starts tomorrow.
    // Consequently 31 December has no remaining projected fraction.
    LocalDate today = LocalDate.now(clock);
    if (today.getYear() != year)
      return today.isAfter(Year.of(year).atDay(1)) ? ZERO : BigDecimal.ONE;
    LocalDate yearEnd = Year.of(year).atDay(Year.of(year).length());
    if (today.equals(yearEnd)) return ZERO;
    return SimulationPeriod.of(today.plusDays(1), yearEnd).yearFraction();
  }

  private static CurrentYearBridgeResult result(
      ForwardSimulationContext context,
      InvestmentProfile profile,
      BigDecimal fraction,
      BigDecimal contribution,
      BigDecimal spending,
      BigDecimal funding,
      BigDecimal passive,
      BigDecimal pension,
      BigDecimal contractualIncome,
      BigDecimal redemption,
      BigDecimal investmentAnnualReturn,
      Map<EconomicBucket, CurrentYearBridgeResult.BucketBoundary> bucketBoundaries) {
    return new CurrentYearBridgeResult(
        profile,
        context.asOfYear(),
        context.firstProjectedYear(),
        context.asOfAge() >= context.originalAssumptions().retirementAge()
            ? SimulationLifecyclePhase.RETIRED
            : SimulationLifecyclePhase.WORKING,
        fraction,
        contribution,
        spending,
        funding,
        passive,
        pension,
        contractualIncome,
        redemption,
        investmentAnnualReturn,
        context.currentYearEvents(),
        bucketBoundaries);
  }
}
