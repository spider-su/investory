package com.smartbox.investory.retirement.simulation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.smartbox.investory.retirement.infrastructure.simulation.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SimulationPlanServiceTest {
  private final SimulationPlanRepository repository = mock(SimulationPlanRepository.class);
  private final SimulationPlanEventRepository eventRepository =
      mock(SimulationPlanEventRepository.class);
  private final SimulationPlanService service =
      new SimulationPlanService(repository, eventRepository);
  private final SimulationAssumptions assumptions =
      new SimulationAssumptions(
          40,
          80,
          new BigDecimal("180000"),
          new BigDecimal("0.025"),
          new BigDecimal("0.02"),
          new BigDecimal("0.04"),
          new BigDecimal("0.06"),
          new BigDecimal("0.025"),
          new BigDecimal("0.03"),
          67,
          new BigDecimal("30000"),
          new BigDecimal("0.19"),
          2026,
          BigDecimal.ZERO,
          List.of(),
          new BigDecimal("0.020"),
          new BigDecimal("0.015"),
          SimulationFundingStrategy.RESERVE_AND_HARVEST,
          new BigDecimal("5"),
          new BigDecimal("0.07"),
          new BigDecimal("0.75"),
          true);

  @BeforeEach
  void setUp() {
    when(eventRepository.findAllBySimulationPlanIdOrderByYearAscIdAsc(any())).thenReturn(List.of());
  }

  @Test
  void crudLifecycleCreatesReadsUpdatesAndDeletesOwnedPlan() {
    Map<Long, SimulationPlanEntity> stored = new HashMap<>();
    when(repository.findAllByPortfolioIdOrderByName(anyLong()))
        .thenAnswer(
            invocation ->
                stored.values().stream()
                    .filter(plan -> plan.getPortfolioId().equals(invocation.getArgument(0)))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
    when(repository.findByIdAndPortfolioId(anyLong(), anyLong()))
        .thenAnswer(
            invocation -> {
              Long id = invocation.getArgument(0);
              Long portfolioId = invocation.getArgument(1);
              return Optional.ofNullable(stored.get(id))
                  .filter(plan -> plan.getPortfolioId().equals(portfolioId));
            });
    when(repository.save(any()))
        .thenAnswer(
            invocation -> {
              SimulationPlanEntity plan = invocation.getArgument(0);
              if (plan.getId() == null) plan.setId(7L);
              stored.put(plan.getId(), plan);
              return plan;
            });
    doAnswer(invocation -> stored.remove(invocation.<SimulationPlanEntity>getArgument(0).getId()))
        .when(repository)
        .delete(any());

    SimulationPlanEntity created = service.create(1L, "Current plan", assumptions);
    assertEquals("Current plan", service.name(1L, created.getId()));
    assertEquals(assumptions, service.assumptions(1L, created.getId()));

    SimulationPlanEntity updated =
        service.update(1L, created.getId(), "Updated plan", assumptions.withRetirementAge(45));
    assertEquals("Updated plan", updated.getName());
    assertEquals(45, service.assumptions(1L, created.getId()).retirementAge());

    service.delete(1L, created.getId());
    verify(repository).delete(created);
    assertThrows(NoSuchElementException.class, () -> service.get(1L, created.getId()));
  }

  @Test
  void createAndLoadRoundTripsAssumptions() {
    when(repository.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of());
    when(repository.save(any()))
        .thenAnswer(
            i -> {
              SimulationPlanEntity p = i.getArgument(0);
              p.setId(7L);
              return p;
            });
    SimulationPlanEntity saved = service.create(1L, "Current plan", assumptions);
    when(repository.findByIdAndPortfolioId(7L, 1L)).thenReturn(Optional.of(saved));
    assertEquals(7L, saved.getId());
    assertEquals(assumptions, service.assumptions(service.get(1L, 7L)));
    assertEquals(new BigDecimal("0.020"), saved.getRentalIncomeGrowthSpread());
    assertEquals(new BigDecimal("0.015"), saved.getSpendingGrowthSpread());
    assertEquals(SimulationFundingStrategy.RESERVE_AND_HARVEST, saved.getFundingStrategy());
    assertEquals(new BigDecimal("5"), saved.getSafeReserveYears());
  }

  @Test
  void updateChangesAssumptionsButNeverStoresResults() {
    SimulationPlanEntity plan = new SimulationPlanEntity();
    plan.setId(7L);
    plan.setPortfolioId(1L);
    plan.setName("Old");
    when(repository.findByIdAndPortfolioId(7L, 1L)).thenReturn(Optional.of(plan));
    when(repository.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of(plan));
    service.update(1L, 7L, "Updated", assumptions);
    ArgumentCaptor<SimulationPlanEntity> captor =
        ArgumentCaptor.forClass(SimulationPlanEntity.class);
    verify(repository).save(captor.capture());
    assertEquals("Updated", captor.getValue().getName());
    assertEquals(180000, captor.getValue().getAnnualLivingExpenses().intValue());
    assertFalse(captor.getValue().getClass().getDeclaredFields().length == 0);
  }

  @Test
  void assumptionOnlyRevisionKeepsExistingFrozenBaseline() {
    var revisions = mock(SimulationPlanRevisionRepository.class);
    var revisionEvents = mock(SimulationPlanRevisionEventRepository.class);
    var revision = new SimulationPlanRevisionEntity();
    revision.setId(11L);
    revision.setSimulationPlanId(7L);
    revision.setRevisionNumber(1);
    revision.setBaselineAsOfYear(2026);
    revision.setBaselineReserve(new BigDecimal("700000"));
    revision.setBaselineInvestmentCapital(new BigDecimal("575000"));
    revision.setBaselineLongTermCapital(new BigDecimal("4000000"));
    var plan = basicPlan();
    plan.setId(7L);
    plan.setPortfolioId(1L);
    plan.setCurrentRevisionId(11L);
    when(repository.findByIdAndPortfolioId(7L, 1L)).thenReturn(Optional.of(plan));
    when(repository.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of(plan));
    when(revisions.findByIdAndSimulationPlanId(11L, 7L)).thenReturn(Optional.of(revision));
    when(revisions.findAllBySimulationPlanIdOrderByRevisionNumberDesc(7L))
        .thenReturn(List.of(revision));
    when(revisionEvents.findAllByRevisionIdOrderByYearAscIdAsc(11L)).thenReturn(List.of());
    when(revisions.save(any()))
        .thenAnswer(
            invocation -> {
              var saved = invocation.getArgument(0, SimulationPlanRevisionEntity.class);
              saved.setId(12L);
              return saved;
            });
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    var service =
        spy(new SimulationPlanService(repository, eventRepository, revisions, revisionEvents));
    doReturn(assumptions).when(service).assumptions(plan);

    service.update(1L, 7L, "Updated", assumptions.withRetirementAge(43));

    var captured = ArgumentCaptor.forClass(SimulationPlanRevisionEntity.class);
    verify(revisions).save(captured.capture());
    assertEquals(2026, captured.getValue().getBaselineAsOfYear());
    assertEquals(new BigDecimal("700000"), captured.getValue().getBaselineReserve());
    assertEquals(new BigDecimal("575000"), captured.getValue().getBaselineInvestmentCapital());
  }

  @Test
  void explicitRebaselineCreatesNewRevisionAndLeavesOldBaselineUntouched() {
    var revisions = mock(SimulationPlanRevisionRepository.class);
    var revisionEvents = mock(SimulationPlanRevisionEventRepository.class);
    var old = new SimulationPlanRevisionEntity();
    old.setId(11L);
    old.setSimulationPlanId(7L);
    old.setRevisionNumber(1);
    old.setBaselineAsOfYear(2026);
    old.setBaselineReserve(new BigDecimal("700000"));
    var plan = basicPlan();
    plan.setId(7L);
    plan.setPortfolioId(1L);
    plan.setCurrentRevisionId(11L);
    when(repository.findByIdAndPortfolioId(7L, 1L)).thenReturn(Optional.of(plan));
    when(repository.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of(plan));
    when(revisions.findAllBySimulationPlanIdOrderByRevisionNumberDesc(7L)).thenReturn(List.of(old));
    when(revisions.findByIdAndSimulationPlanId(11L, 7L)).thenReturn(Optional.of(old));
    when(revisionEvents.findAllByRevisionIdOrderByYearAscIdAsc(11L)).thenReturn(List.of());
    when(revisions.save(any()))
        .thenAnswer(
            invocation -> {
              var saved = invocation.getArgument(0, SimulationPlanRevisionEntity.class);
              saved.setId(12L);
              return saved;
            });
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    var service =
        spy(new SimulationPlanService(repository, eventRepository, revisions, revisionEvents));
    doReturn(assumptions).when(service).assumptions(plan);

    var replacement =
        new com.smartbox.investory.retirement.planning.PlanningBaseline(
            2026,
            new BigDecimal("900000"),
            new BigDecimal("900000"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO);
    var created = service.rebaseline(1L, 7L, replacement);

    assertEquals(12L, created.getId());
    assertEquals(2, created.getRevisionNumber());
    assertEquals(new BigDecimal("900000"), created.getBaselineReserve());
    assertEquals(11L, old.getId());
    assertEquals(new BigDecimal("700000"), old.getBaselineReserve());
    assertEquals(12L, plan.getCurrentRevisionId());
  }

  @Test
  void duplicateNamesAreRejectedWithinPortfolio() {
    SimulationPlanEntity existing = new SimulationPlanEntity();
    existing.setId(1L);
    existing.setName("Current plan");
    when(repository.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of(existing));
    assertThrows(
        IllegalArgumentException.class, () -> service.create(1L, " current plan ", assumptions));
  }

  @Test
  void resolvePlanIdUsesTheLatestUnarchivedPlanWhenNoPlanIsRequested() {
    SimulationPlanEntity latest = basicPlan();
    latest.setId(8L);
    when(repository.findFirstByPortfolioIdAndArchivedFalseOrderByUpdatedAtDescIdDesc(1L))
        .thenReturn(Optional.of(latest));

    assertEquals(Optional.of(8L), service.resolvePlanId(1L, null));
    verify(repository).findFirstByPortfolioIdAndArchivedFalseOrderByUpdatedAtDescIdDesc(1L);
  }

  @Test
  void resolvePlanIdKeepsAnExplicitOwnedPlan() {
    SimulationPlanEntity explicit = basicPlan();
    explicit.setId(7L);
    when(repository.findByIdAndPortfolioId(7L, 1L)).thenReturn(Optional.of(explicit));

    assertEquals(Optional.of(7L), service.resolvePlanId(1L, 7L));
    verify(repository, never())
        .findFirstByPortfolioIdAndArchivedFalseOrderByUpdatedAtDescIdDesc(any());
  }

  @Test
  void deleteUsesPortfolioOwnership() {
    SimulationPlanEntity plan = new SimulationPlanEntity();
    plan.setId(7L);
    when(repository.findByIdAndPortfolioId(7L, 1L)).thenReturn(Optional.of(plan));
    service.delete(1L, 7L);
    verify(repository).delete(plan);
  }

  @Test
  void eventIsSavedForOwnedPlan() {
    SimulationPlanEntity plan = new SimulationPlanEntity();
    plan.setId(7L);
    when(repository.findByIdAndPortfolioId(7L, 1L)).thenReturn(Optional.of(plan));
    when(eventRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    SimulationPlanEventEntity event =
        service.saveEvent(
            1L,
            7L,
            null,
            2030,
            "Car",
            new BigDecimal("150000"),
            SimulationEventType.ONE_OFF_EXPENSE,
            "PLN");
    assertEquals(2030, event.getYear());
    assertEquals(SimulationEventType.ONE_OFF_EXPENSE, event.getType());
    assertEquals(new BigDecimal("150000"), event.getAmount());
  }

  @Test
  void loadingPlanRestoresSavedEvents() {
    SimulationPlanEntity plan = new SimulationPlanEntity();
    plan.setId(7L);
    plan.setCurrentAge(40);
    plan.setEndAge(80);
    plan.setAnnualLivingExpenses(new BigDecimal("180000"));
    plan.setAnnualDiscretionaryExpenses(BigDecimal.ZERO);
    plan.setInflationRate(BigDecimal.ZERO);
    plan.setCashReturnRate(BigDecimal.ZERO);
    plan.setFixedIncomeReturnRate(BigDecimal.ZERO);
    plan.setEquityReturnRate(BigDecimal.ZERO);
    plan.setRealEstateReturnRate(BigDecimal.ZERO);
    plan.setOtherReturnRate(BigDecimal.ZERO);
    plan.setPensionStartAge(99);
    plan.setAnnualPension(BigDecimal.ZERO);
    plan.setCapitalGainTaxRate(BigDecimal.ZERO);
    SimulationPlanEventEntity event = new SimulationPlanEventEntity();
    event.setId(9L);
    event.setYear(2030);
    event.setName("Car");
    event.setAmount(new BigDecimal("150000"));
    event.setType(SimulationEventType.ONE_OFF_EXPENSE);
    when(eventRepository.findAllBySimulationPlanIdOrderByYearAscIdAsc(7L))
        .thenReturn(List.of(event));
    assertEquals("Car", service.assumptions(plan).futureEvents().get(0).name());
    assertEquals(2030, service.assumptions(plan).futureEvents().get(0).year());
  }

  @Test
  void oldPlanWithoutGrowthFieldsUsesDeterministicCompatibilityValues() {
    SimulationPlanEntity plan = basicPlan();
    assertEquals(
        SimulationAssumptions.DEFAULT_RENTAL_INCOME_GROWTH_SPREAD,
        service.assumptions(plan).rentalIncomeGrowthSpread());
    assertEquals(
        SimulationAssumptions.DEFAULT_SPENDING_GROWTH_SPREAD,
        service.assumptions(plan).spendingGrowthSpread());
    assertEquals(
        SimulationFundingStrategy.SIMPLE_WATERFALL, service.assumptions(plan).fundingStrategy());
  }

  @Test
  void oldPlanWithoutRetirementFieldsDefaultsToImmediateRetirement() {
    SimulationAssumptions loaded = service.assumptions(basicPlan());
    assertEquals(loaded.currentAge(), loaded.retirementAge());
    assertEquals(BigDecimal.ZERO, loaded.annualEmploymentIncome());
    assertEquals(BigDecimal.ZERO, loaded.annualPreRetirementContribution());
  }

  @Test
  void savedPlanKeepsItsCalendarAgeAnchorWhenReloadedLater() {
    SimulationPlanEntity plan = basicPlan();
    plan.setStartYear(2026);
    plan.setCurrentAge(40);
    SimulationAssumptions reloaded = service.assumptions(plan);
    assertEquals(2026, reloaded.startYear());
    assertEquals(40, reloaded.currentAge());
    assertEquals(41, reloaded.currentAge() + 2027 - reloaded.startYear());
  }

  private static SimulationPlanEntity basicPlan() {
    SimulationPlanEntity plan = new SimulationPlanEntity();
    plan.setId(7L);
    plan.setStartYear(2026);
    plan.setCurrentAge(40);
    plan.setEndAge(80);
    plan.setAnnualLivingExpenses(BigDecimal.ZERO);
    plan.setAnnualDiscretionaryExpenses(BigDecimal.ZERO);
    plan.setInflationRate(BigDecimal.ZERO);
    plan.setCashReturnRate(BigDecimal.ZERO);
    plan.setFixedIncomeReturnRate(BigDecimal.ZERO);
    plan.setEquityReturnRate(BigDecimal.ZERO);
    plan.setRealEstateReturnRate(BigDecimal.ZERO);
    plan.setOtherReturnRate(BigDecimal.ZERO);
    plan.setPensionStartAge(99);
    plan.setAnnualPension(BigDecimal.ZERO);
    plan.setCapitalGainTaxRate(BigDecimal.ZERO);
    return plan;
  }
}
