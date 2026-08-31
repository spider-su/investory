package com.smartbox.investory.retirement.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Expense Profile")
class ExpenseProfileTest {
  @DisplayName("missing Schedule Keeps Factor At One")
  @Test
  void missingScheduleKeepsFactorAtOne() {
    assertBd("1.00", ExpenseProfile.EMPTY.factorForYear(20));
  }

  @DisplayName("steps Apply From Their Year Until The Next Step")
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

  @DisplayName("invalid Schedules Are Rejected")
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
        () -> new ExpenseProfile(List.of(new ExpenseProfileStep(0, BigDecimal.ONE.negate()))));
  }

  @DisplayName("resolves Stages From Plan Start Anchor")
  @Test
  void resolvesStagesFromPlanStartAnchor() {
    var assumptions =
        SimulationAssumptions.defaults(
                new InvestmentProfile(
                    1L,
                    CurrencyType.PLN,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    List.of(),
                    null,
                    null,
                    new com.smartbox.investory.profile.api.model.ProfileAssetProjection(
                        List.of(),
                        java.math.BigDecimal.ZERO,
                        0,
                        com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
                    (BigDecimal.ZERO == null ? java.math.BigDecimal.ZERO : BigDecimal.ZERO),
                    BigDecimal.ZERO
                        .subtract(
                            (BigDecimal.ZERO == null ? java.math.BigDecimal.ZERO : BigDecimal.ZERO))
                        .max(java.math.BigDecimal.ZERO),
                    com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures
                        .annualIncome(
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO),
                    com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY),
                40,
                80,
                2025)
            .withExpenseProfile(
                new ExpenseProfile(
                    List.of(
                        new ExpenseProfileStep(0, BigDecimal.ONE),
                        new ExpenseProfileStep(10, BigDecimal.ONE),
                        new ExpenseProfileStep(20, new BigDecimal("0.85")),
                        new ExpenseProfileStep(30, new BigDecimal("0.75")))));
    var stages = assumptions.expenseProfile().steps();
    assertEquals(
        List.of(2025, 2035, 2045, 2055),
        stages.stream().map(assumptions::expenseProfileStageYear).toList());
    assertEquals(
        List.of(40, 50, 60, 70), stages.stream().map(assumptions::expenseProfileStageAge).toList());
    assertBd("1.00", assumptions.expenseProfileFactorForCalendarYear(2044));
    assertBd("0.85", assumptions.expenseProfileFactorForCalendarYear(2045));
    assertBd("0.85", assumptions.expenseProfileFactorForCalendarYear(2054));
    assertBd("0.75", assumptions.expenseProfileFactorForCalendarYear(2055));
  }

  private static void assertBd(String expected, BigDecimal actual) {
    assertEquals(0, new BigDecimal(expected).compareTo(actual));
  }
}
