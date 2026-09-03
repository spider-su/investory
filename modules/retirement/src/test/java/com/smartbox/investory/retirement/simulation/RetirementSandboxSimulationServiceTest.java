package com.smartbox.investory.retirement.simulation;

import static org.junit.jupiter.api.Assertions.*;

import com.smartbox.investory.retirement.api.model.SandboxSimulationInput;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RetirementSandboxSimulationServiceTest {
  private final RetirementSandboxSimulationService service =
      new RetirementSandboxSimulationService();

  @Test
  void reportsOkWhenBucketsFundTheWholeHorizon() {
    var result = service.simulate(input("100000000", "0", "0", "0", "0"));

    assertFalse(result.simulationFailed());
    assertNull(result.failureAge());
    assertEquals(56, result.years().size());
  }

  @Test
  void reportsNokAtTheFirstUnfundedYear() {
    var result = service.simulate(input("0", "0", "100000", "0", "0"));

    assertTrue(result.simulationFailed());
    assertEquals(41, result.failureAge());
    assertTrue(result.firstFailureShortfall().compareTo(BigDecimal.ZERO) > 0);
  }

  @Test
  void growsBucketsBeforeWithdrawalsStart() {
    var result = service.simulate(input("0", "100000", "0", "0.10", "0"));

    assertEquals(0, result.years().getFirst().fixedIncomeEnd().compareTo(new BigDecimal("10000")));
  }

  @Test
  void returnsTheRetirementHorizonWithSpendingFromTheFirstVisibleYear() {
    var input =
        new SandboxSimulationInput(
            40,
            42,
            45,
            new BigDecimal("180000"),
            new BigDecimal("0.025"),
            new BigDecimal("1000000"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            2026);

    var result = service.simulate(input);

    assertEquals(4, result.years().size());
    assertEquals(42, result.years().getFirst().age());
    assertEquals(0, result.years().getFirst().totalExpenses().compareTo(new BigDecimal("180000")));
  }

  @Test
  void rentalIncomeFundsRetirementSpending() {
    var input =
        new SandboxSimulationInput(
            40,
            40,
            40,
            new BigDecimal("180000"),
            new BigDecimal("0.025"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("15000"),
            2026);

    var result = service.simulate(input);

    assertFalse(result.simulationFailed());
  }

  @Test
  void pensionIncomeStartsAtConfiguredAge() {
    var input =
        new SandboxSimulationInput(
            40,
            40,
            41,
            new BigDecimal("180000"),
            new BigDecimal("0.025"),
            new BigDecimal("189000"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("15000"),
            41,
            2026);

    var result = service.simulate(input);

    assertEquals(0, result.years().getFirst().pensionIncome().signum());
    assertEquals(0, result.years().get(1).pensionIncome().compareTo(new BigDecimal("180000")));
    assertFalse(result.simulationFailed());
  }

  private static SandboxSimulationInput input(
      String cash, String bonds, String equities, String bondReturn, String equityReturn) {
    return new SandboxSimulationInput(
        40,
        40,
        95,
        new BigDecimal("100000"),
        new BigDecimal("0.025"),
        new BigDecimal(cash),
        new BigDecimal(bonds),
        new BigDecimal(bondReturn),
        new BigDecimal(equities),
        new BigDecimal(equityReturn),
        2026);
  }
}
