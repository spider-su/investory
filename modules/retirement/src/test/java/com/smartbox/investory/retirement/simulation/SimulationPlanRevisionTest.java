package com.smartbox.investory.retirement.simulation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.smartbox.investory.retirement.infrastructure.simulation.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SimulationPlanRevisionTest {
  private final SimulationPlanRepository plans = mock(SimulationPlanRepository.class);
  private final SimulationPlanEventRepository legacyEvents =
      mock(SimulationPlanEventRepository.class);
  private final SimulationPlanRevisionRepository revisions =
      mock(SimulationPlanRevisionRepository.class);
  private final SimulationPlanRevisionEventRepository revisionEvents =
      mock(SimulationPlanRevisionEventRepository.class);
  private final SimulationPlanService service =
      new SimulationPlanService(plans, legacyEvents, revisions, revisionEvents);

  @Test
  void editingCreatesNewRevisionAndLeavesOldSnapshotUntouched() {
    SimulationPlanEntity plan = logicalPlan(7L);
    plan.setCurrentRevisionId(11L);
    SimulationPlanRevisionEntity old = revision(11L, 1);
    when(plans.findByIdAndPortfolioId(7L, 1L)).thenReturn(Optional.of(plan));
    when(plans.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of(plan));
    when(revisions.findByIdAndSimulationPlanId(11L, 7L)).thenReturn(Optional.of(old));
    when(revisions.findAllBySimulationPlanIdOrderByRevisionNumberDesc(7L)).thenReturn(List.of(old));
    when(revisionEvents.findAllByRevisionIdOrderByYearAscIdAsc(11L)).thenReturn(List.of());
    when(revisions.save(any()))
        .thenAnswer(
            invocation -> {
              SimulationPlanRevisionEntity value = invocation.getArgument(0);
              value.setId(12L);
              return value;
            });
    when(plans.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    SimulationAssumptions changed = assumptions(new BigDecimal("210000"));
    service.update(1L, 7L, "Retirement 55", changed);

    assertEquals(new BigDecimal("180000"), old.getAnnualLivingExpenses());
    assertEquals(12L, plan.getCurrentRevisionId());
    verify(revisions).save(argThat(value -> value.getRevisionNumber() == 2));
  }

  @Test
  void currentRevisionEventsAreCopiedIntoNewRevision() {
    SimulationPlanEntity plan = logicalPlan(7L);
    plan.setCurrentRevisionId(11L);
    SimulationPlanRevisionEntity old = revision(11L, 1);
    SimulationPlanRevisionEventEntity event = new SimulationPlanRevisionEventEntity();
    event.setId(21L);
    event.setRevisionId(11L);
    event.setYear(2030);
    event.setName("Car");
    event.setAmount(new BigDecimal("1000"));
    event.setType(SimulationEventType.ONE_OFF_EXPENSE);
    when(plans.findByIdAndPortfolioId(7L, 1L)).thenReturn(Optional.of(plan));
    when(plans.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of(plan));
    when(revisions.findByIdAndSimulationPlanId(11L, 7L)).thenReturn(Optional.of(old));
    when(revisions.findAllBySimulationPlanIdOrderByRevisionNumberDesc(7L)).thenReturn(List.of(old));
    when(revisionEvents.findAllByRevisionIdOrderByYearAscIdAsc(11L)).thenReturn(List.of(event));
    when(revisions.save(any()))
        .thenAnswer(
            invocation -> {
              SimulationPlanRevisionEntity value = invocation.getArgument(0);
              value.setId(12L);
              return value;
            });
    when(plans.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    service.saveEvent(
        1L,
        7L,
        null,
        2031,
        "New",
        new BigDecimal("2000"),
        SimulationEventType.ONE_OFF_INCOME,
        null);

    verify(revisionEvents, times(2)).save(any(SimulationPlanRevisionEventEntity.class));
    assertEquals(11L, event.getRevisionId());
    assertEquals(12L, plan.getCurrentRevisionId());
  }

  @Test
  void archivedPlanCanStillResolveHistoricalRevision() {
    SimulationPlanEntity plan = logicalPlan(7L);
    plan.setArchived(true);
    plan.setCurrentRevisionId(11L);
    SimulationPlanRevisionEntity old = revision(11L, 1);
    when(plans.findByIdAndPortfolioId(7L, 1L)).thenReturn(Optional.of(plan));
    when(revisions.findByIdAndSimulationPlanId(11L, 7L)).thenReturn(Optional.of(old));

    assertSame(old, service.revision(1L, 7L, 11L));
  }

  private static SimulationPlanEntity logicalPlan(Long id) {
    SimulationPlanEntity plan = new SimulationPlanEntity();
    plan.setId(id);
    plan.setPortfolioId(1L);
    plan.setName("Retirement 55");
    return plan;
  }

  private static SimulationPlanRevisionEntity revision(Long id, int number) {
    SimulationPlanRevisionEntity revision = new SimulationPlanRevisionEntity();
    revision.setId(id);
    revision.setSimulationPlanId(7L);
    revision.setRevisionNumber(number);
    SimulationAssumptions a = assumptions(new BigDecimal("180000"));
    revision.setCurrentAge(a.currentAge());
    revision.setStartYear(a.startYear());
    revision.setEndAge(a.endAge());
    revision.setRetirementAge(a.retirementAge());
    revision.setAnnualEmploymentIncome(a.annualEmploymentIncome());
    revision.setAnnualPreRetirementContribution(a.annualPreRetirementContribution());
    revision.setAnnualLivingExpenses(a.annualLivingExpenses());
    revision.setAnnualDiscretionaryExpenses(a.annualDiscretionaryExpenses());
    revision.setInflationRate(a.inflationRate());
    revision.setRentalIncomeGrowthSpread(a.rentalIncomeGrowthSpread());
    revision.setSpendingGrowthSpread(a.spendingGrowthSpread());
    revision.setFundingStrategy(a.fundingStrategy());
    revision.setSafeReserveYears(a.safeReserveYears());
    revision.setEquityHarvestMinimumReturnRate(a.equityHarvestMinimumReturnRate());
    revision.setEquityGainHarvestRate(a.equityGainHarvestRate());
    revision.setAllowEmergencyEquityWithdrawal(a.allowEmergencyEquityWithdrawal());
    revision.setCashReturnRate(a.cashReturnRate());
    revision.setFixedIncomeReturnRate(a.fixedIncomeReturnRate());
    revision.setEquityReturnRate(a.equityReturnRate());
    revision.setRealEstateReturnRate(a.realEstateReturnRate());
    revision.setOtherReturnRate(a.otherReturnRate());
    revision.setPensionStartAge(a.pensionStartAge());
    revision.setAnnualPension(a.annualPension());
    revision.setCapitalGainTaxRate(a.capitalGainTaxRate());
    return revision;
  }

  private static SimulationAssumptions assumptions(BigDecimal spending) {
    return new SimulationAssumptions(
        40,
        80,
        spending,
        new BigDecimal(".025"),
        new BigDecimal(".02"),
        new BigDecimal(".04"),
        new BigDecimal(".06"),
        new BigDecimal(".025"),
        new BigDecimal(".03"),
        67,
        new BigDecimal("30000"),
        new BigDecimal(".19"),
        2026,
        BigDecimal.ZERO,
        List.of(),
        new BigDecimal(".02"),
        new BigDecimal(".015"),
        SimulationFundingStrategy.RESERVE_AND_HARVEST,
        new BigDecimal("5"),
        new BigDecimal(".07"),
        new BigDecimal(".75"),
        true);
  }
}
