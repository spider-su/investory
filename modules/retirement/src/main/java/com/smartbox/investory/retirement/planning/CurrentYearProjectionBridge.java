package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.retirement.simulation.RetirementSimulation;
import com.smartbox.investory.retirement.simulation.SimulationEvent;
import com.smartbox.investory.retirement.simulation.SimulationLifecyclePhase;
import com.smartbox.investory.retirement.simulation.SimulationScenario;
import com.smartbox.investory.retirement.simulation.SimulationAssumptions;
import com.smartbox.investory.retirement.simulation.SimulationYear;
import com.smartbox.investory.retirement.simulation.ForwardSimulationContext;
import com.smartbox.investory.retirement.simulation.ForwardSimulationContextFactory;
import com.smartbox.investory.retirement.simulation.BucketType;
import com.smartbox.investory.retirement.simulation.PlanningBuckets;
import com.smartbox.investory.retirement.profile.EconomicBucket;
import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.profile.ProfileAllocation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.math.RoundingMode;
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
  public CurrentYearProjectionBridge(Clock clock, RetirementSimulation simulations,
      ForwardSimulationContextFactory contexts) {
    this.clock = clock;
    this.simulations = simulations;
    this.contexts = contexts;
  }

  public InvestmentProfile projectCurrentYearEnd(InvestmentProfile profile, SimulationAssumptions assumptions) {
    return projectCurrentYearEnd(contexts.create(profile, assumptions)).bridgedProfile();
  }

  public CurrentYearBridgeResult projectCurrentYearEnd(ForwardSimulationContext context) {
    InvestmentProfile profile = context.currentProfile();
    SimulationAssumptions assumptions = context.originalAssumptions();
    if (!context.requiresCurrentYearBridge()) {
      return result(context, profile, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, null,
          currentBoundaries(profile));
    }
    int year = context.asOfYear();
    BigDecimal fraction = remainingYearFraction(year);
    SimulationYear projected = simulations.simulate(profile, assumptions, SimulationScenario.BASE, context.asOfYear())
        .years().stream().findFirst().orElse(null);
    if (projected == null)
      return result(context, profile, fraction, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, null,
          currentBoundaries(profile));
    BigDecimal spending = projected.totalExpenses().multiply(fraction);
    BigDecimal passive = projected.passiveIncome().multiply(fraction);
    BigDecimal pension = projected.pensionIncome().multiply(fraction);
    BigDecimal income = passive.add(pension).add(projected.employmentIncome().multiply(fraction));
    BigDecimal funding = spending.subtract(income).add(projected.eventExpenses()).subtract(projected.eventIncome())
        .max(ZERO);
    BigDecimal contribution = projected.preRetirementContribution().multiply(fraction);
    Map<BucketType, CurrentYearBridgeResult.BucketBoundary> boundaries =
        projectedBoundaries(projected, fraction);
    InvestmentProfile bridgedProfile = rebaseSpendableState(profile, boundaries);
    return result(context, bridgedProfile, fraction, contribution, spending, funding, passive, pension,
        projected.rentalIncome().add(projected.bondIncome()).multiply(fraction), ZERO,
        projected.equityGain(), boundaries);
  }

  /** Carry the returned four-bucket expected end state into the next projected year. */
  private static InvestmentProfile rebaseSpendableState(
      InvestmentProfile profile,
      Map<BucketType, CurrentYearBridgeResult.BucketBoundary> boundaries) {
    BigDecimal cashStart = boundaries.get(BucketType.CASH).startValue();
    BigDecimal cashEnd = boundaries.get(BucketType.CASH).expectedEndValue();
    BigDecimal bondStart = boundaries.get(BucketType.BONDS).startValue();
    BigDecimal bondEnd = boundaries.get(BucketType.BONDS).expectedEndValue();
    BigDecimal equityStart = boundaries.get(BucketType.EQUITIES).startValue();
    BigDecimal equityEnd = boundaries.get(BucketType.EQUITIES).expectedEndValue();
    BigDecimal realEstateStart = boundaries.get(BucketType.REAL_ESTATE).startValue();
    BigDecimal realEstateEnd = boundaries.get(BucketType.REAL_ESTATE).expectedEndValue();
    BigDecimal marketDelta = cashEnd.subtract(cashStart).add(equityEnd.subtract(equityStart));
    BigDecimal totalDelta = marketDelta
        .add(bondEnd.subtract(bondStart))
        .add(realEstateEnd.subtract(realEstateStart));
    BigDecimal marketEnd = zero(profile.marketPortfolioValue()).add(marketDelta);
    BigDecimal liquidEnd = zero(profile.liquidAssets())
        .add(cashEnd.subtract(cashStart))
        .add(bondEnd.subtract(bondStart))
        .add(equityEnd.subtract(equityStart));
    BigDecimal illiquidEnd = zero(profile.illiquidAssets()).add(realEstateEnd.subtract(realEstateStart));
    return new InvestmentProfile(
        profile.portfolioId(), profile.currency(), marketEnd, profile.longTermAssetValue(),
        zero(profile.totalNetWorth()).add(totalDelta), profile.historicalMarketInvestmentIncome(),
        profile.expectedLongTermAssetIncome(), profile.totalInvestmentIncome(), cashEnd,
        illiquidEnd, rebaseAllocations(profile.allocations(), boundaries),
        profile.longTermAssets(), profile.currentRentalIncome(), profile.currentBondIncome(),
        profile.longTermPlanningState(), cashEnd, equityEnd);
  }

  private static List<ProfileAllocation> rebaseAllocations(
      List<ProfileAllocation> allocations,
      Map<BucketType, CurrentYearBridgeResult.BucketBoundary> boundaries) {
    if (allocations.isEmpty()) return allocations;
    EnumMap<EconomicBucket, BigDecimal> values = new EnumMap<>(EconomicBucket.class);
    allocations.forEach(a -> values.merge(a.bucket(), zero(a.value()), BigDecimal::add));
    values.put(EconomicBucket.LIQUID_CASH, boundaries.get(BucketType.CASH).expectedEndValue());
    values.put(EconomicBucket.FIXED_INCOME, boundaries.get(BucketType.BONDS).expectedEndValue());
    values.put(EconomicBucket.EQUITY, boundaries.get(BucketType.EQUITIES).expectedEndValue());
    values.put(EconomicBucket.REAL_ESTATE, boundaries.get(BucketType.REAL_ESTATE).expectedEndValue());
    BigDecimal total = values.values().stream().reduce(ZERO, BigDecimal::add);
    return allocations.stream()
        .map(a -> new ProfileAllocation(a.bucket(), values.getOrDefault(a.bucket(), ZERO),
            total.signum() == 0 ? ZERO : values.getOrDefault(a.bucket(), ZERO)
                .divide(total, 8, RoundingMode.HALF_UP), a.liquidity()))
        .toList();
  }

  private static Map<BucketType, CurrentYearBridgeResult.BucketBoundary> currentBoundaries(
      InvestmentProfile profile) {
    PlanningBuckets buckets = PlanningBuckets.fromProfileWithBondYield(profile, ZERO, ZERO);
    EnumMap<BucketType, CurrentYearBridgeResult.BucketBoundary> result = new EnumMap<>(BucketType.class);
    buckets.asMap().forEach((bucket, value) -> result.put(bucket,
        new CurrentYearBridgeResult.BucketBoundary(value.startValue(), value.startValue())));
    return result;
  }

  private static Map<BucketType, CurrentYearBridgeResult.BucketBoundary> projectedBoundaries(
      SimulationYear projected, BigDecimal fraction) {
    EnumMap<BucketType, CurrentYearBridgeResult.BucketBoundary> result = new EnumMap<>(BucketType.class);
    result.put(BucketType.CASH, boundary(projected.cashStart(), projected.cashEnd(), fraction));
    result.put(BucketType.BONDS, boundary(projected.fixedIncomeStart(), projected.fixedIncomeEnd(), fraction));
    result.put(BucketType.EQUITIES, boundary(projected.equityStart(), projected.equityEnd(), fraction));
    result.put(BucketType.REAL_ESTATE, boundary(projected.realEstateStart(), projected.realEstateEnd(), fraction));
    return result;
  }

  private static CurrentYearBridgeResult.BucketBoundary boundary(
      BigDecimal start, BigDecimal end, BigDecimal fraction) {
    BigDecimal actualStart = zero(start);
    return new CurrentYearBridgeResult.BucketBoundary(actualStart, interpolate(actualStart, end, fraction));
  }

  private static BigDecimal interpolate(BigDecimal start, BigDecimal end, BigDecimal fraction) {
    return start.add(zero(end).subtract(start).multiply(fraction));
  }

  private static BigDecimal zero(BigDecimal value) {
    return value == null ? ZERO : value;
  }

  BigDecimal remainingYearFraction(int year) {
    // The current date belongs to live/current facts; forward projection starts tomorrow.
    // Consequently 31 December has no remaining projected fraction.
    LocalDate today = LocalDate.now(clock);
    if (today.getYear() != year) return today.isAfter(Year.of(year).atDay(1)) ? ZERO : BigDecimal.ONE;
    LocalDate yearEnd = Year.of(year).atDay(Year.of(year).length());
    if (today.equals(yearEnd)) return ZERO;
    return SimulationPeriod.of(today.plusDays(1), yearEnd).yearFraction();
  }

  private static CurrentYearBridgeResult result(ForwardSimulationContext context, InvestmentProfile profile,
      BigDecimal fraction, BigDecimal contribution, BigDecimal spending, BigDecimal funding,
      BigDecimal passive, BigDecimal pension, BigDecimal contractualIncome, BigDecimal redemption,
      BigDecimal investmentAnnualReturn,
      Map<BucketType, CurrentYearBridgeResult.BucketBoundary> bucketBoundaries) {
    return new CurrentYearBridgeResult(profile, context.asOfYear(), context.firstProjectedYear(),
        context.asOfAge() >= context.originalAssumptions().retirementAge()
            ? SimulationLifecyclePhase.RETIRED : SimulationLifecyclePhase.WORKING,
        fraction, contribution, spending, funding, passive, pension, contractualIncome, redemption,
        investmentAnnualReturn,
        context.currentYearEvents(), bucketBoundaries);
  }
}
