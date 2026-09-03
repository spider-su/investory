package com.smartbox.investory.retirement.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.infrastructure.simulation.PersistedSimulationAssumptions;
import com.smartbox.investory.retirement.infrastructure.simulation.SimulationAssumptionsPersistenceMapper;
import com.smartbox.investory.retirement.infrastructure.simulation.SimulationPlanRevisionEntity;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Simulation Assumptions Persistence Mapper")
class SimulationAssumptionsPersistenceMapperTest {

  @DisplayName("plan And Revision Share The Same Round Trip Boundary")
  @Test
  void planAndRevisionShareTheSameRoundTripBoundary() {
    SimulationAssumptions source = assumptions();
    PersistedSimulationAssumptions persistedRow = new SimulationPlanRevisionEntity();

    SimulationAssumptionsPersistenceMapper.write(persistedRow, source);

    SimulationAssumptions restored =
        SimulationAssumptionsPersistenceMapper.read(persistedRow, source.futureEvents());
    assertEquals(source.currentAge(), restored.currentAge());
    assertEquals(source.fixedIncomeReturnRate(), restored.fixedIncomeReturnRate());
    assertEquals(source.equityReturnRate(), restored.equityReturnRate());
    assertEquals(source.capitalGainTaxRate(), restored.capitalGainTaxRate());

    assertInstanceOf(PersistedSimulationAssumptions.class, new SimulationPlanRevisionEntity());
  }

  @DisplayName("legacy Nulls Are Normalized Only At The Persistence Boundary")
  @Test
  void legacyNullsAreNormalizedOnlyAtThePersistenceBoundary() {
    SimulationPlanRevisionEntity persisted = new SimulationPlanRevisionEntity();
    SimulationAssumptionsPersistenceMapper.write(persisted, assumptions());
    persisted.setRentalIncomeGrowthSpread(null);
    persisted.setSpendingGrowthSpread(null);
    persisted.setFundingStrategy(null);
    persisted.setSafeReserveYears(null);
    persisted.setEquityHarvestMinimumReturnRate(null);
    persisted.setEquityGainHarvestRate(null);
    persisted.setAllowEmergencyEquityWithdrawal(null);
    persisted.setRetirementAge(null);
    persisted.setAnnualEmploymentIncome(null);
    persisted.setAnnualPreRetirementContribution(null);
    persisted.setFundingOrder(null);
    persisted.setExpenseProfile(null);

    SimulationAssumptions restored =
        SimulationAssumptionsPersistenceMapper.read(persisted, List.of());

    assertEquals(
        SimulationAssumptions.DEFAULT_RENTAL_INCOME_GROWTH_SPREAD,
        restored.rentalIncomeGrowthSpread());
    assertEquals(
        SimulationAssumptions.DEFAULT_SPENDING_GROWTH_SPREAD, restored.spendingGrowthSpread());
    assertEquals(SimulationFundingStrategy.SIMPLE_WATERFALL, restored.fundingStrategy());
    assertEquals(SimulationAssumptions.DEFAULT_SAFE_RESERVE_YEARS, restored.safeReserveYears());
    assertEquals(BigDecimal.ZERO, restored.equityHarvestMinimumReturnRate());
    assertEquals(BigDecimal.ZERO, restored.equityGainHarvestRate());
    assertEquals(true, restored.allowEmergencyEquityWithdrawal());
    assertEquals(restored.currentAge(), restored.retirementAge());
    assertEquals(BigDecimal.ZERO, restored.annualEmploymentIncome());
    assertEquals(BigDecimal.ZERO, restored.annualPreRetirementContribution());
    assertEquals(SimulationAssumptions.DEFAULT_FUNDING_ORDER, restored.fundingOrder());
    assertEquals(ExpenseProfile.EMPTY, restored.expenseProfile());
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
