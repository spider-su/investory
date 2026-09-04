package com.smartbox.investory.retirement.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.smartbox.investory.retirement.api.model.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Simulation Assumptions Builder")
class SimulationAssumptionsBuilderTest {

  @DisplayName("named Copy Changes One Field And Preserves Every Other Record Component")
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

  @DisplayName("named Copy Still Uses Canonical Validation")
  @Test
  void namedCopyStillUsesCanonicalValidation() {
    SimulationAssumptions source = assumptions();

    assertThrows(
        IllegalArgumentException.class,
        () ->
            source.toBuilder()
                .fundingOrder(
                    List.of(RetirementFundingSource.RESERVE, RetirementFundingSource.RESERVE))
                .build());
  }

  @Test
  void rejectsCapitalGainTaxRateOutsideTaxRateBounds() {
    SimulationAssumptions source = assumptions();

    assertThrows(
        IllegalArgumentException.class,
        () -> source.toBuilder().capitalGainTaxRate(new BigDecimal("-0.01")).build());
    assertThrows(
        IllegalArgumentException.class,
        () -> source.toBuilder().capitalGainTaxRate(new BigDecimal("1.01")).build());
  }

  @DisplayName("named Copy Adjusts Recurring Spending Without Changing Its Composition")
  @Test
  void namedCopyAdjustsRecurringSpendingWithoutChangingItsComposition() {
    SimulationAssumptions source = assumptions();

    SimulationAssumptions changed =
        source.toBuilder().recurringSpending(new BigDecimal("72000")).build();

    assertEquals(new BigDecimal("60000.000000000000"), changed.annualLivingExpenses());
    assertEquals(new BigDecimal("12000.000000000000"), changed.annualDiscretionaryExpenses());
  }

  private static SimulationAssumptions assumptions() {
    return new SimulationAssumptions(
        41,
        96,
        new BigDecimal("120000"),
        new BigDecimal("0.025"),
        new BigDecimal("0.04"),
        new BigDecimal("0.07"),
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
        List.of(
            RetirementFundingSource.LONG_TERM,
            RetirementFundingSource.RESERVE,
            RetirementFundingSource.INVESTMENT),
        new ExpenseProfile(List.of(new ExpenseProfileStep(5, new BigDecimal("0.8")))));
  }
}
