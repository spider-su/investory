package com.smartbox.investory.retirement.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulationAssumptionsBuilderTest {

  @Test
  void namedCopyChangesOneFieldAndPreservesEveryOtherRecordComponent()
      throws ReflectiveOperationException {
    SimulationAssumptions source = assumptions();

    SimulationAssumptions changed =
        source.toBuilder().annualPension(new BigDecimal("99000")).build();

    for (var component : SimulationAssumptions.class.getRecordComponents()) {
      Object actual = component.getAccessor().invoke(changed);
      if (component.getName().equals("annualPension")) {
        assertEquals(new BigDecimal("99000"), actual);
      } else {
        assertEquals(
            component.getAccessor().invoke(source),
            actual,
            () -> component.getName() + " was not preserved");
      }
    }
  }

  @Test
  void namedCopyStillUsesCanonicalValidation() {
    SimulationAssumptions source = assumptions();

    assertThrows(
        IllegalArgumentException.class,
        () ->
            source.toBuilder()
                .fundingOrder(List.of(FundingSource.CASH, FundingSource.CASH))
                .build());
  }

  private static SimulationAssumptions assumptions() {
    return new SimulationAssumptions(
        41,
        96,
        new BigDecimal("120000"),
        new BigDecimal("0.025"),
        new BigDecimal("0.01"),
        new BigDecimal("0.04"),
        new BigDecimal("0.07"),
        new BigDecimal("0.03"),
        new BigDecimal("0.02"),
        68,
        new BigDecimal("50000"),
        new BigDecimal("0.19"),
        2027,
        new BigDecimal("24000"),
        List.of(
            new SimulationEvent(
                7L,
                2035,
                "Roof replacement",
                new BigDecimal("10000"),
                SimulationEventType.ONE_OFF_EXPENSE,
                "Planned")),
        new BigDecimal("0.015"),
        new BigDecimal("0.02"),
        SimulationFundingStrategy.RESERVE_AND_HARVEST,
        new BigDecimal("4"),
        new BigDecimal("0.06"),
        new BigDecimal("0.5"),
        false,
        65,
        new BigDecimal("180000"),
        new BigDecimal("36000"),
        List.of(FundingSource.BONDS, FundingSource.CASH, FundingSource.STOCKS),
        new ExpenseProfile(List.of(new ExpenseProfileStep(5, new BigDecimal("0.8")))),
        new ProjectedIncomePolicy(
            ProjectedIncomePolicy.IncomeMode.MANUAL,
            new BigDecimal("25000"),
            ProjectedIncomePolicy.IncomeMode.MANUAL,
            new BigDecimal("7000")));
  }
}
