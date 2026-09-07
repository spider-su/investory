package com.smartbox.investory.retirement.planning;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.smartbox.investory.longterm.api.LongTermAssetProfileReader;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.infrastructure.planning.RetirementPlanningYearEntity;
import com.smartbox.investory.retirement.infrastructure.planning.RetirementPlanningYearRepository;
import com.smartbox.investory.retirement.infrastructure.planning.RetirementPlanningYearStateCodec;
import com.smartbox.investory.retirement.simulation.RetirementSimulation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Planning Timeline Facade")
class PlanningTimelineFacadeTest {
  private final Clock clock = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);
  private final RetirementPlanningYearRepository years =
      mock(RetirementPlanningYearRepository.class);
  private final RetirementPlanningYearStateCodec state =
      mock(RetirementPlanningYearStateCodec.class);
  private final RetirementSimulation simulations = mock(RetirementSimulation.class);
  private final PlanningMetricDerivationService metrics =
      mock(PlanningMetricDerivationService.class);
  private PlanningTimelineFacade facade;
  private RetirementPlanningYearEntity current;

  @BeforeEach
  void setUp() {
    current = planningYear(7L, 1L, 2026, PlanningYearStatus.DRAFT);
    when(years.findByPortfolioIdAndYear(1L, 2026)).thenReturn(Optional.of(current));
    when(years.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    facade =
        new PlanningTimelineFacade(
            years,
            state,
            metrics,
            simulations,
            clock,
            mock(LongTermAssetProfileReader.class),
            new PlanningProgressService(),
            new PlanningYearReviewService(new PlanningProgressService()),
            mock(PlanningMoneyConversionService.class));
  }

  @Test
  void currentManualSpendingIsStoredInsideThePlanningYearAggregate() {
    facade.saveCurrentManualValue(
        1L, 2026, PlanningMetric.CORE_SPENDING, new BigDecimal("120000"), "reviewed");

    PlanningMetricValue stored =
        current.getValues().get(PlanningValueKind.ACTUAL).get(PlanningMetric.CORE_SPENDING);
    assertEquals(new BigDecimal("120000"), stored.approvedValue());
    assertEquals(PlanningValueSource.USER_OVERRIDE, stored.source());
    verify(state).writeFrom(current);
    verify(years).save(current);
  }

  @Test
  void authoritativeCurrentFactsCannotBeOverridden() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            facade.saveCurrentManualValue(
                1L, 2026, PlanningMetric.NET_WORTH, BigDecimal.TEN, "no"));
  }

  @Test
  void historicalCloseRequiresCompleteReviewedFacts() {
    RetirementPlanningYearEntity past = planningYear(8L, 1L, 2025, PlanningYearStatus.DRAFT);
    when(years.findByPortfolioIdAndYear(1L, 2025)).thenReturn(Optional.of(past));

    PlanningYearCloseStatus status = facade.historicalCloseStatus(1L, 2025);

    assertFalse(status.canClose());
    assertTrue(status.missingMetrics().contains("Net worth or market assets"));
  }

  @Test
  void closedHistoricalYearCanBeExplicitlyReopened() {
    RetirementPlanningYearEntity past = planningYear(8L, 1L, 2025, PlanningYearStatus.CLOSED);
    when(years.findByPortfolioIdAndYear(1L, 2025)).thenReturn(Optional.of(past));

    facade.reopenHistoricalYear(1L, 2025);

    assertEquals(PlanningYearStatus.DRAFT, past.getStatus());
    assertEquals(Instant.now(clock), past.getReopenedAt());
    verify(state).writeFrom(past);
  }

  @Test
  void baselineUsesPlanIdentityWithoutPersistingRevisionIdentity() {
    SimulationYear projected = mock(SimulationYear.class);
    when(simulations.simulate(any(), any(), any(), anyInt()))
        .thenReturn(
            new SimulationResult(
                SimulationScenario.BASE,
                false,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(projected)));

    facade.setCurrentBaseline(1L, 2026, 4L, mock(InvestmentProfile.class), assumptions());

    assertEquals(4L, current.getBaselinePlanId());
    assertEquals(Instant.now(clock), current.getBaselineCreatedAt());
  }

  @Test
  void reviewModeUsesPersistedHistoricalAggregateAndClockForLiveYear() {
    RetirementPlanningYearEntity past = planningYear(8L, 1L, 2025, PlanningYearStatus.CLOSED);
    when(years.findByPortfolioIdAndYear(1L, 2025)).thenReturn(Optional.of(past));

    assertEquals(YearReviewMode.CLOSED, facade.reviewMode(1L, 2025));
    assertEquals(YearReviewMode.LIVE, facade.reviewMode(1L, 2026));
    assertEquals(YearReviewMode.NONE, facade.reviewMode(1L, 2027));
  }

  private static RetirementPlanningYearEntity planningYear(
      Long id, Long portfolioId, int year, PlanningYearStatus status) {
    RetirementPlanningYearEntity result = new RetirementPlanningYearEntity();
    result.setId(id);
    result.setPortfolioId(portfolioId);
    result.setYear(year);
    result.setStatus(status);
    return result;
  }

  private static SimulationAssumptions assumptions() {
    return new SimulationAssumptions(
        40,
        80,
        new BigDecimal("120000"),
        new BigDecimal("0.02"),
        new BigDecimal("0.05"),
        new BigDecimal("0.03"),
        67,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        2026,
        BigDecimal.ZERO,
        List.of(),
        new BigDecimal("0.02"),
        new BigDecimal("0.02"),
        SimulationFundingStrategy.RESERVE_AND_HARVEST,
        new BigDecimal("5"),
        new BigDecimal("0.07"),
        new BigDecimal("0.75"),
        true,
        40,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        SimulationAssumptions.DEFAULT_FUNDING_ORDER,
        ExpenseProfile.EMPTY);
  }
}
