package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.simulation.RetirementSimulation;
import com.smartbox.investory.retirement.simulation.SimulationEvent;
import com.smartbox.investory.retirement.simulation.SimulationLifecyclePhase;
import com.smartbox.investory.retirement.simulation.SimulationScenario;
import com.smartbox.investory.retirement.simulation.SimulationAssumptions;
import com.smartbox.investory.retirement.simulation.SimulationYear;
import com.smartbox.investory.retirement.simulation.ForwardSimulationContext;
import com.smartbox.investory.retirement.simulation.ForwardSimulationContextFactory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
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
      return result(context, profile, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO);
    }
    int year = context.asOfYear();
    BigDecimal fraction = remainingYearFraction(year);
    SimulationYear projected = simulations.simulate(profile, assumptions, SimulationScenario.BASE, true)
        .years().stream().findFirst().orElse(null);
    if (projected == null) return result(context, profile, fraction, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO);
    BigDecimal spending = projected.totalExpenses().multiply(fraction);
    BigDecimal passive = projected.passiveIncome().multiply(fraction);
    BigDecimal pension = projected.pensionIncome().multiply(fraction);
    BigDecimal income = passive.add(pension).add(projected.employmentIncome().multiply(fraction));
    BigDecimal funding = spending.subtract(income).add(projected.eventExpenses()).subtract(projected.eventIncome())
        .max(ZERO);
    BigDecimal contribution = projected.preRetirementContribution().multiply(fraction);
    InvestmentProfile bridgedProfile = rebaseSpendableState(profile, projected, fraction);
    return result(context, bridgedProfile, fraction, contribution, spending, funding, passive, pension,
        projected.rentalIncome().add(projected.bondIncome()).multiply(fraction), ZERO);
  }

  /** Carry the returned reserve/Investment end state into the next projected year. */
  private static InvestmentProfile rebaseSpendableState(
      InvestmentProfile profile, SimulationYear projected, BigDecimal fraction) {
    BigDecimal reserveStart = zero(profile.liquidAssets());
    BigDecimal investmentStart = zero(profile.marketPortfolioValue()).subtract(reserveStart).max(ZERO);
    BigDecimal reserveEnd = interpolate(reserveStart, projected.manualLiquidReserveEnd(), fraction);
    BigDecimal investmentEnd = interpolate(investmentStart, projected.equityEnd(), fraction);
    BigDecimal marketEnd = reserveEnd.add(investmentEnd);
    BigDecimal marketDelta = marketEnd.subtract(zero(profile.marketPortfolioValue()));
    return new InvestmentProfile(
        profile.portfolioId(), profile.currency(), marketEnd, profile.longTermAssetValue(),
        zero(profile.totalNetWorth()).add(marketDelta), profile.historicalMarketInvestmentIncome(),
        profile.expectedLongTermAssetIncome(), profile.totalInvestmentIncome(), reserveEnd,
        profile.illiquidAssets(), profile.allocations(), profile.longTermAssets(),
        profile.currentRentalIncome(), profile.currentBondIncome(), profile.longTermPlanningState());
  }

  private static BigDecimal interpolate(BigDecimal start, BigDecimal end, BigDecimal fraction) {
    return start.add(zero(end).subtract(start).multiply(fraction));
  }

  private static BigDecimal zero(BigDecimal value) {
    return value == null ? ZERO : value;
  }

  BigDecimal remainingYearFraction(int year) {
    LocalDate today = LocalDate.now(clock);
    if (today.getYear() != year) return today.isAfter(Year.of(year).atDay(1)) ? ZERO : BigDecimal.ONE;
    return BigDecimal.valueOf(Year.of(year).length() - today.getDayOfYear())
        .divide(BigDecimal.valueOf(Year.of(year).length()), 12, java.math.RoundingMode.HALF_UP);
  }

  private static CurrentYearBridgeResult result(ForwardSimulationContext context, InvestmentProfile profile,
      BigDecimal fraction, BigDecimal contribution, BigDecimal spending, BigDecimal funding,
      BigDecimal passive, BigDecimal pension, BigDecimal contractualIncome, BigDecimal redemption) {
    return new CurrentYearBridgeResult(profile, context.asOfYear(), context.firstProjectedYear(),
        context.asOfAge() >= context.originalAssumptions().retirementAge()
            ? SimulationLifecyclePhase.RETIRED : SimulationLifecyclePhase.WORKING,
        fraction, contribution, spending, funding, passive, pension, contractualIncome, redemption,
        context.currentYearEvents());
  }
}
