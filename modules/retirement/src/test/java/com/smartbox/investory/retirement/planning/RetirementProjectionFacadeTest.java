package com.smartbox.investory.retirement.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.smartbox.investory.profile.api.ProfileReader;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.RetirementPlanApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.ForwardSimulationContext;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.retirement.simulation.ForwardSimulationContextFactory;
import com.smartbox.investory.retirement.simulation.RetirementSimulation;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Retirement Projection Facade")
class RetirementProjectionFacadeTest {
  @DisplayName("future Projection Uses Frozen Baseline Instead Of Live Balances")
  @Test
  void futureProjectionUsesFrozenBaselineInsteadOfLiveBalances() {
    InvestmentProfile live =
        new InvestmentProfile(
            1L,
            CurrencyType.PLN,
            new BigDecimal("675000"),
            new BigDecimal("300000"),
            new BigDecimal("975000"),
            new BigDecimal("100000"),
            BigDecimal.ZERO,
            List.of(),
            new BigDecimal("10"),
            BigDecimal.ZERO,
            new com.smartbox.investory.profile.api.model.ProfileAssetProjection(
                List.of(),
                java.math.BigDecimal.ZERO,
                0,
                com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
            (new BigDecimal("100000") == null
                ? java.math.BigDecimal.ZERO
                : new BigDecimal("100000")),
            new BigDecimal("675000")
                .subtract(
                    (new BigDecimal("100000") == null
                        ? java.math.BigDecimal.ZERO
                        : new BigDecimal("100000")))
                .max(java.math.BigDecimal.ZERO),
            com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
                BigDecimal.ZERO,
                new BigDecimal("675000"),
                BigDecimal.ZERO,
                new BigDecimal("300000"),
                BigDecimal.ZERO,
                new BigDecimal("975000")),
            com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
    SimulationAssumptions assumptions = SimulationAssumptions.defaults(live, 40, 45, 2025);
    ForwardSimulationContext context =
        new ForwardSimulationContextFactory(
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC))
            .create(live, assumptions);
    ForwardSimulationInputService forwardInputs = mock(ForwardSimulationInputService.class);
    when(forwardInputs.prepare(any(), eq(assumptions)))
        .thenReturn(new ForwardSimulationInput(context, live, Optional.empty()));
    RetirementProjectionFacade facade =
        new RetirementProjectionFacade(
            mock(ProfileReader.class),
            mock(RetirementPlanApi.class),
            forwardInputs,
            mock(RetirementSimulation.class),
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    facade.project(
        live,
        assumptions,
        new PlanningBaseline(
            2026,
            new BigDecimal("90000"),
            new BigDecimal("500000"),
            new BigDecimal("280000"),
            new BigDecimal("12"),
            BigDecimal.ZERO));

    var captor = org.mockito.ArgumentCaptor.forClass(InvestmentProfile.class);
    verify(forwardInputs).prepare(captor.capture(), eq(assumptions));
    assertEquals(new BigDecimal("590000"), captor.getValue().marketPortfolioValue());
    assertEquals(new BigDecimal("90000"), captor.getValue().liquidAssets());

    InvestmentProfile changedLive =
        new InvestmentProfile(
            1L,
            CurrencyType.PLN,
            new BigDecimal("1200000"),
            new BigDecimal("990000"),
            new BigDecimal("2190000"),
            new BigDecimal("250000"),
            BigDecimal.ZERO,
            List.of(),
            new BigDecimal("999"),
            BigDecimal.ZERO,
            new com.smartbox.investory.profile.api.model.ProfileAssetProjection(
                List.of(),
                java.math.BigDecimal.ZERO,
                0,
                com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
            (new BigDecimal("250000") == null
                ? java.math.BigDecimal.ZERO
                : new BigDecimal("250000")),
            new BigDecimal("1200000")
                .subtract(
                    (new BigDecimal("250000") == null
                        ? java.math.BigDecimal.ZERO
                        : new BigDecimal("250000")))
                .max(java.math.BigDecimal.ZERO),
            com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
                BigDecimal.ZERO,
                new BigDecimal("1200000"),
                BigDecimal.ZERO,
                new BigDecimal("990000"),
                BigDecimal.ZERO,
                new BigDecimal("2190000")),
            com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
    facade.project(
        changedLive,
        assumptions,
        new PlanningBaseline(
            2026,
            new BigDecimal("90000"),
            new BigDecimal("500000"),
            new BigDecimal("280000"),
            new BigDecimal("12"),
            BigDecimal.ZERO));
    verify(forwardInputs, times(2)).prepare(captor.capture(), eq(assumptions));
    assertEquals(new BigDecimal("590000"), captor.getAllValues().get(1).marketPortfolioValue());
    assertEquals(new BigDecimal("90000"), captor.getAllValues().get(1).liquidAssets());
  }

  @DisplayName("preserves No Forward Projection State Without Calling Simulation Engine")
  @Test
  void preservesNoForwardProjectionStateWithoutCallingSimulationEngine() {
    InvestmentProfile profile =
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
                .subtract((BigDecimal.ZERO == null ? java.math.BigDecimal.ZERO : BigDecimal.ZERO))
                .max(java.math.BigDecimal.ZERO),
            com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO),
            com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
    SimulationAssumptions assumptions = SimulationAssumptions.defaults(profile, 40, 95, 2026);
    ForwardSimulationContext context =
        new ForwardSimulationContextFactory(
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC))
            .create(profile, assumptions);
    ForwardSimulationInputService forwardInputs = mock(ForwardSimulationInputService.class);
    when(forwardInputs.prepare(profile, assumptions))
        .thenReturn(new ForwardSimulationInput(context, profile, Optional.empty()));
    RetirementSimulation simulations = mock(RetirementSimulation.class);
    RetirementProjectionFacade facade =
        new RetirementProjectionFacade(
            mock(ProfileReader.class),
            mock(RetirementPlanApi.class),
            forwardInputs,
            simulations,
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    RetirementProjectionContext result = facade.project(profile, assumptions);

    assertEquals(profile, result.profile());
    assertEquals(assumptions, result.projectedAssumptions());
    assertTrue(result.scenarioResults().isEmpty());
    assertTrue(result.summaries().isEmpty());
    verifyNoInteractions(simulations);
  }

  @DisplayName("saved Plan Keeps Future Frozen While Current Profile Follows Live Reader")
  @Test
  void savedPlanKeepsFutureFrozenWhileCurrentProfileFollowsLiveReader() {
    var liveA = profile(new BigDecimal("700000"), new BigDecimal("575000"));
    var liveB = profile(new BigDecimal("900000"), new BigDecimal("900000"));
    var assumptions = SimulationAssumptions.defaults(liveA, 40, 45, 2025);
    var baseline = PlanningBaseline.fromProfile(liveA, 2026);
    var profiles = mock(ProfileReader.class);
    var plans = mock(RetirementPlanApi.class);
    var forwardInputs = mock(ForwardSimulationInputService.class);
    var clock = Clock.fixed(Instant.parse("2026-08-22T00:00:00Z"), ZoneOffset.UTC);
    when(plans.details(1L, 7L))
        .thenReturn(
            new com.smartbox.investory.retirement.api.model.PlanDetails(
                7L, "Saved", assumptions, 1L, null, baseline));
    when(profiles.loadProfile(1L)).thenReturn(liveA, liveB);
    when(forwardInputs.prepare(any(), eq(assumptions)))
        .thenAnswer(
            invocation -> {
              InvestmentProfile prepared = invocation.getArgument(0);
              var context =
                  new ForwardSimulationContextFactory(clock).create(prepared, assumptions);
              return new ForwardSimulationInput(context, prepared, Optional.empty());
            });
    var facade =
        new RetirementProjectionFacade(
            profiles, plans, forwardInputs, mock(RetirementSimulation.class), clock);

    RetirementProjectionContext first = facade.load(1L, 7L, 40, 45);
    RetirementProjectionContext second = facade.load(1L, 7L, 40, 45);

    assertEquals(liveA, first.profile());
    assertEquals(liveB, second.profile());
    assertEquals(first.projectedProfile(), second.projectedProfile());
    verify(forwardInputs, times(2)).prepare(any(), eq(assumptions));
  }

  private static InvestmentProfile profile(BigDecimal reserve, BigDecimal investment) {
    return new InvestmentProfile(
        1L,
        CurrencyType.PLN,
        reserve.add(investment),
        BigDecimal.ZERO,
        reserve.add(investment),
        reserve,
        BigDecimal.ZERO,
        List.of(),
        new BigDecimal("100"),
        BigDecimal.ZERO,
        new com.smartbox.investory.profile.api.model.ProfileAssetProjection(
            List.of(),
            java.math.BigDecimal.ZERO,
            0,
            com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
        (reserve == null ? java.math.BigDecimal.ZERO : reserve),
        reserve
            .add(investment)
            .subtract((reserve == null ? java.math.BigDecimal.ZERO : reserve))
            .max(java.math.BigDecimal.ZERO),
        com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
            BigDecimal.ZERO,
            reserve.add(investment),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            reserve.add(investment)),
        com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
  }
}
