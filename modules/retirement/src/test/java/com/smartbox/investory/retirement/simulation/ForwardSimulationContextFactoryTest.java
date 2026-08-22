package com.smartbox.investory.retirement.simulation;

import static org.junit.jupiter.api.Assertions.*;

import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ForwardSimulationContextFactoryTest {
  private static final InvestmentProfile PROFILE =
      new InvestmentProfile(
          1L,
          CurrencyType.PLN,
          BigDecimal.TEN,
          BigDecimal.ZERO,
          BigDecimal.TEN,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          List.of(),
          List.of());

  @Test
  void sharedTemporalHelpersUseTheSavedPlanAnchor() {
    SimulationAssumptions assumptions = assumptions(2025, 40, 90).withRetirementAge(45);

    assertEquals(41, ForwardSimulationContextFactory.currentPlanningAge(assumptions, 2026));
    assertEquals(2030, ForwardSimulationContextFactory.retirementYear(assumptions));

    SimulationAssumptions pastRetirement = assumptions(2020, 60, 90).withRetirementAge(65);
    assertEquals(66, ForwardSimulationContextFactory.currentPlanningAge(pastRetirement, 2026));
    assertEquals(2025, ForwardSimulationContextFactory.retirementYear(pastRetirement));
  }

  @Test
  void historicalPlanAnchorDerivesCurrentAgeAndRetirementYear() {
    SimulationAssumptions assumptions = assumptions(2023, 37, 80).withRetirementAge(42);

    assertEquals(40, ForwardSimulationContextFactory.currentPlanningAge(assumptions, 2026));
    assertEquals(2028, ForwardSimulationContextFactory.retirementYear(assumptions));
    assertEquals(37, assumptions.ageAtPlanStart());
    assertEquals(2023, assumptions.planStartYear());
  }

  @Test
  void sameYearKeepsSavedAgeAndStartsWithTheNextFullYear() {
    SimulationAssumptions assumptions = assumptions(2026, 40, 80);

    ForwardSimulationContext context = factory("2026-06-01T00:00:00Z").create(PROFILE, assumptions);

    assertEquals(40, context.asOfAge());
    assertEquals(2026, context.asOfYear());
    assertEquals(41, context.firstProjectedAge());
    assertEquals(2027, context.firstProjectedYear());
    assertTrue(context.requiresCurrentYearBridge());
    assertEquals(41, context.forwardAssumptions().orElseThrow().currentAge());
  }

  @Test
  void elapsedYearsAdvanceEffectiveAgeAndFilterCompletedEvents() {
    SimulationAssumptions assumptions =
        assumptions(2026, 40, 80)
            .rebasedTo(
                40,
                2026,
                List.of(
                    new SimulationEvent(
                        1L,
                        2026,
                        "Current",
                        BigDecimal.ONE,
                        SimulationEventType.ONE_OFF_EXPENSE,
                        null),
                    new SimulationEvent(
                        2L,
                        2028,
                        "Completed",
                        BigDecimal.ONE,
                        SimulationEventType.ONE_OFF_EXPENSE,
                        null),
                    new SimulationEvent(
                        3L,
                        2030,
                        "Future",
                        BigDecimal.ONE,
                        SimulationEventType.ONE_OFF_INCOME,
                        null)));

    ForwardSimulationContext context = factory("2028-06-01T00:00:00Z").create(PROFILE, assumptions);

    assertEquals(42, context.asOfAge());
    assertEquals(2029, context.firstProjectedYear());
    assertEquals(43, context.firstProjectedAge());
    assertEquals(1, context.historicalEventCountExcluded());
    assertEquals(
        List.of(2028), context.currentYearEvents().stream().map(SimulationEvent::year).toList());
    assertEquals(
        List.of(2030),
        context.remainingFutureEvents().stream().map(SimulationEvent::year).toList());
    assertEquals(
        List.of(2030),
        context.forwardAssumptions().orElseThrow().futureEvents().stream()
            .map(SimulationEvent::year)
            .toList());
  }

  @Test
  void rebasingPreservesLifecycleAndEconomicFieldsWithoutMutatingOriginal() {
    SimulationAssumptions original =
        assumptions(2026, 40, 80)
            .withRetirementAge(60)
            .withAnnualEmploymentIncome(new BigDecimal("240000"))
            .withAnnualPreRetirementContribution(new BigDecimal("50000"))
            .withAnnualPension(new BigDecimal("60000"));

    ForwardSimulationContext context = factory("2028-06-01T00:00:00Z").create(PROFILE, original);
    SimulationAssumptions rebased = context.forwardAssumptions().orElseThrow();

    assertEquals(60, rebased.retirementAge());
    assertEquals(new BigDecimal("240000"), rebased.annualEmploymentIncome());
    assertEquals(new BigDecimal("50000"), rebased.annualPreRetirementContribution());
    assertEquals(original.pensionStartAge(), rebased.pensionStartAge());
    assertEquals(new BigDecimal("60000"), rebased.annualPension());
    assertEquals(40, original.currentAge());
    assertEquals(2026, original.startYear());
    assertSame(PROFILE, context.currentProfile());
  }

  @Test
  void retirementAndPensionRemainAbsoluteWhenAlreadyActive() {
    SimulationAssumptions original = assumptions(2020, 40, 65).withRetirementAge(50);
    original =
        new SimulationAssumptions(
            original.currentAge(),
            original.endAge(),
            original.annualLivingExpenses(),
            original.inflationRate(),
            original.cashReturnRate(),
            original.fixedIncomeReturnRate(),
            original.equityReturnRate(),
            original.realEstateReturnRate(),
            original.otherReturnRate(),
            65,
            new BigDecimal("30000"),
            original.capitalGainTaxRate(),
            original.startYear(),
            original.annualDiscretionaryExpenses(),
            original.futureEvents(),
            original.rentalIncomeGrowthRate(),
            original.spendingGrowthRate(),
            original.fundingStrategy(),
            original.safeReserveYears(),
            original.equityHarvestMinimumReturnRate(),
            original.equityGainHarvestRate(),
            original.allowEmergencyEquityWithdrawal(),
            original.retirementAge(),
            original.annualEmploymentIncome(),
            original.annualPreRetirementContribution());

    SimulationAssumptions rebased =
        factory("2030-06-01T00:00:00Z")
            .create(PROFILE, original)
            .forwardAssumptions()
            .orElseThrow();

    assertEquals(50, rebased.retirementAge());
    assertEquals(65, rebased.pensionStartAge());
    assertTrue(rebased.currentAge() >= rebased.retirementAge());
  }

  @Test
  void horizonBoundaryHasNoFullProjectedYear() {
    SimulationAssumptions original = assumptions(2026, 40, 42);

    ForwardSimulationContext context = factory("2028-06-01T00:00:00Z").create(PROFILE, original);

    assertEquals(42, context.asOfAge());
    assertEquals(43, context.firstProjectedAge());
    assertTrue(context.forwardAssumptions().isEmpty());
    assertFalse(context.futureProjectionAvailable());
    assertFalse(context.requiresCurrentYearBridge());
  }

  @Test
  void beyondHorizonIsRejected() {
    SimulationAssumptions original = assumptions(2026, 40, 42);

    assertThrows(
        IllegalArgumentException.class,
        () -> factory("2029-06-01T00:00:00Z").create(PROFILE, original));
  }

  private static ForwardSimulationContextFactory factory(String instant) {
    return new ForwardSimulationContextFactory(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
  }

  private static SimulationAssumptions assumptions(int startYear, int currentAge, int endAge) {
    return SimulationAssumptions.defaults(PROFILE, currentAge, endAge, startYear);
  }
}
