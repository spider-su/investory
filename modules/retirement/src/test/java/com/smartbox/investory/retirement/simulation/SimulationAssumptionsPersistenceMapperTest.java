package com.smartbox.investory.retirement.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.smartbox.investory.retirement.infrastructure.simulation.PersistedSimulationAssumptions;
import com.smartbox.investory.retirement.infrastructure.simulation.SimulationAssumptionsPersistenceMapper;
import com.smartbox.investory.retirement.infrastructure.simulation.SimulationPlanEntity;
import com.smartbox.investory.retirement.infrastructure.simulation.SimulationPlanRevisionEntity;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulationAssumptionsPersistenceMapperTest {

  @Test
  void planAndRevisionShareTheSameRoundTripBoundary() {
    SimulationAssumptions source = assumptions();
    List<PersistedSimulationAssumptions> persistedRows =
        List.of(new SimulationPlanEntity(), new SimulationPlanRevisionEntity());

    for (PersistedSimulationAssumptions persistedRow : persistedRows) {
      SimulationAssumptionsPersistenceMapper.write(persistedRow, source);

      assertEquals(
          source, SimulationAssumptionsPersistenceMapper.read(persistedRow, source.futureEvents()));
    }

    assertInstanceOf(PersistedSimulationAssumptions.class, new SimulationPlanEntity());
    assertInstanceOf(PersistedSimulationAssumptions.class, new SimulationPlanRevisionEntity());
  }

  @Test
  @SuppressWarnings("deprecation")
  void legacyNullsAreNormalizedOnlyAtThePersistenceBoundary() {
    SimulationPlanEntity persisted = new SimulationPlanEntity();
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
    persisted.setRentalIncomeMode(null);
    persisted.setManualRentalIncome(null);
    persisted.setBondCashIncomeMode(null);
    persisted.setManualBondCashIncome(null);

    SimulationAssumptions restored =
        SimulationAssumptionsPersistenceMapper.read(persisted, List.of());

    assertEquals(
        SimulationAssumptions.DEFAULT_RENTAL_INCOME_GROWTH_SPREAD,
        restored.rentalIncomeGrowthSpread());
    assertEquals(
        SimulationAssumptions.DEFAULT_SPENDING_GROWTH_SPREAD, restored.spendingGrowthSpread());
    assertEquals(SimulationFundingStrategy.SIMPLE_WATERFALL, restored.fundingStrategy());
    assertEquals(BigDecimal.ZERO, restored.safeReserveYears());
    assertEquals(BigDecimal.ZERO, restored.equityHarvestMinimumReturnRate());
    assertEquals(BigDecimal.ZERO, restored.equityGainHarvestRate());
    assertEquals(true, restored.allowEmergencyEquityWithdrawal());
    assertEquals(restored.currentAge(), restored.retirementAge());
    assertEquals(BigDecimal.ZERO, restored.annualEmploymentIncome());
    assertEquals(BigDecimal.ZERO, restored.annualPreRetirementContribution());
    assertEquals(SimulationAssumptions.DEFAULT_FUNDING_ORDER, restored.fundingOrder());
    assertEquals(ExpenseProfile.EMPTY, restored.expenseProfile());
    assertEquals(ProjectedIncomePolicy.SOURCE, restored.projectedIncomePolicy());
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
