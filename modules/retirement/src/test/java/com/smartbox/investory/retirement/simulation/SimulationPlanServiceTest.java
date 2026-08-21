package com.smartbox.investory.retirement.simulation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.smartbox.investory.retirement.infrastructure.simulation.*;
import java.math.BigDecimal;
import java.util.List;
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
    assertEquals(new BigDecimal("0.020"), saved.getRentalIncomeGrowthRate());
    assertEquals(new BigDecimal("0.015"), saved.getSpendingGrowthRate());
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
  void duplicateNamesAreRejectedWithinPortfolio() {
    SimulationPlanEntity existing = new SimulationPlanEntity();
    existing.setId(1L);
    existing.setName("Current plan");
    when(repository.findAllByPortfolioIdOrderByName(1L)).thenReturn(List.of(existing));
    assertThrows(
        IllegalArgumentException.class, () -> service.create(1L, " current plan ", assumptions));
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
        SimulationAssumptions.DEFAULT_RENTAL_INCOME_GROWTH_RATE,
        service.assumptions(plan).rentalIncomeGrowthRate());
    assertEquals(plan.getInflationRate(), service.assumptions(plan).spendingGrowthRate());
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
