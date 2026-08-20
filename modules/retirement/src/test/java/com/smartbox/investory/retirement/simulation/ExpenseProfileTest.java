package com.smartbox.investory.retirement.simulation;

import static org.junit.jupiter.api.Assertions.*;

import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExpenseProfileTest {
  @Test
  void missingScheduleKeepsFactorAtOne() {
    assertBd("1.00", ExpenseProfile.EMPTY.factorForYear(20));
  }

  @Test
  void stepsApplyFromTheirYearUntilTheNextStep() {
    ExpenseProfile profile =
        new ExpenseProfile(
            List.of(
                new ExpenseProfileStep(8, new BigDecimal("0.90")),
                new ExpenseProfileStep(15, new BigDecimal("0.80")),
                new ExpenseProfileStep(22, new BigDecimal("0.75"))));

    assertBd("1.00", profile.factorForYear(7));
    assertBd("0.90", profile.factorForYear(8));
    assertBd("0.90", profile.factorForYear(14));
    assertBd("0.80", profile.factorForYear(15));
    assertBd("0.75", profile.factorForYear(99));
  }

  @Test
  void simulationAppliesInflationGrowthAndProfileIndependently() {
    SimulationAssumptions assumptions =
        SimulationAssumptions.defaults(emptyProfile(), 40, 42, 2025)
            .withRecurringSpending(new BigDecimal("100"))
            .withSpendingGrowthRate(new BigDecimal("0.10"))
            .withExpenseProfile(
                new ExpenseProfile(List.of(new ExpenseProfileStep(1, new BigDecimal("0.50")))));

    var years =
        new RetirementSimulationService()
            .simulate(emptyProfile(), assumptions, SimulationScenario.BASE)
            .years();

    assertBd("100", years.get(0).coreExpenses());
    assertBd("55.0", years.get(1).coreExpenses());
    assertBd("60.50", years.get(2).coreExpenses());
  }

  @Test
  void invalidSchedulesAreRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ExpenseProfile(List.of(new ExpenseProfileStep(-1, BigDecimal.ONE))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ExpenseProfile(
                List.of(
                    new ExpenseProfileStep(2, BigDecimal.ONE),
                    new ExpenseProfileStep(1, BigDecimal.ONE))));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ExpenseProfile(List.of(new ExpenseProfileStep(0, BigDecimal.ZERO))));
  }

  private static InvestmentProfile emptyProfile() {
    return new InvestmentProfile(
        null,
        CurrencyType.USD,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        List.of(),
        List.of());
  }

  private static void assertBd(String expected, BigDecimal actual) {
    assertEquals(0, new BigDecimal(expected).compareTo(actual));
  }
}
