package com.smartbox.investory.retirement.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.smartbox.investory.longterm.api.model.InterestTreatment;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.profile.api.ProfileReader;
import com.smartbox.investory.profile.api.model.EconomicBucket;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.profile.api.model.Liquidity;
import com.smartbox.investory.profile.api.model.ProfileAssetProjection;
import com.smartbox.investory.profile.api.model.ProjectedLongTermAsset;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.infrastructure.simulation.*;
import com.smartbox.investory.retirement.infrastructure.simulation.SimulationPlanService;
import com.smartbox.investory.retirement.simulation.ForwardSimulationContextFactory;
import com.smartbox.investory.retirement.simulation.RetirementSimulationService;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Retirement Frozen Plan Lifecycle Integration")
class RetirementFrozenPlanLifecycleIntegrationTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-08-22T00:00:00Z"), ZoneOffset.UTC);

  @DisplayName("real Projection Remains Frozen Until Explicit Rebaseline")
  @Test
  void realProjectionRemainsFrozenUntilExplicitRebaseline() {
    var liveA = profile("700000", "575000");
    var liveB = profile("900000", "900000");
    var assumptions = SimulationAssumptions.defaults(liveA, 40, 45, 2025);
    var baselineA = PlanningBaseline.fromProfile(liveA, 2026);
    var store = newPlanStore();
    var planService = store.service();
    var plan = planService.create(1L, "Frozen", assumptions, baselineA);

    var profiles = mock(ProfileReader.class);
    when(profiles.loadProfile(1L)).thenReturn(liveA, liveB, liveB);
    var simulations = new RetirementSimulationService();
    var contexts = new ForwardSimulationContextFactory(CLOCK);
    var bridge = new CurrentYearProjectionBridge(CLOCK, simulations, contexts);
    var inputs = new ForwardSimulationInputService(contexts, bridge);
    var facade = new RetirementProjectionFacade(profiles, planService, inputs, simulations, CLOCK);

    var first = facade.load(1L, plan.getId(), 40, 45);
    var firstFuture = first.scenarioResults().get(SimulationScenario.BASE);
    var firstRevisionId = plan.getCurrentRevisionId();
    var second = facade.load(1L, plan.getId(), 40, 45);
    var secondFuture = second.scenarioResults().get(SimulationScenario.BASE);

    assertThat(first.profile()).isEqualTo(liveA);
    assertThat(second.profile()).isEqualTo(liveB);
    assertThat(secondFuture).isEqualTo(firstFuture);

    var revisionB =
        planService.rebaseline(1L, plan.getId(), PlanningBaseline.fromProfile(liveB, 2026));
    var rebased = facade.load(1L, plan.getId(), 40, 45);
    assertThat(revisionB.getId()).isNotEqualTo(firstRevisionId);
    assertThat(rebased.scenarioResults().get(SimulationScenario.BASE)).isNotEqualTo(firstFuture);
    assertThat(planService.details(1L, plan.getId()).baseline().investmentCapital())
        .isEqualByComparingTo("900000");

    var assumptionsB = planService.details(1L, plan.getId()).assumptions().withRetirementAge(43);
    planService.update(1L, plan.getId(), "Frozen", assumptionsB);
    assertThat(planService.details(1L, plan.getId()).baseline().investmentCapital())
        .isEqualByComparingTo("900000");
  }

  @DisplayName("newly Saved Baseline Retains Long Term Bond Projection Inputs")
  @Test
  void newlySavedBaselineRetainsLongTermBondProjectionInputs() {
    var bonds =
        java.util.stream.IntStream.range(0, 6)
            .mapToObj(
                i ->
                    new ProjectedLongTermAsset(
                        (long) i,
                        "Bond " + i,
                        LongTermAssetType.BOND,
                        EconomicBucket.FIXED_INCOME,
                        CurrencyType.PLN,
                        new BigDecimal("150000"),
                        Liquidity.LIQUID,
                        List.of(
                            new ProjectedLongTermAsset.Period(
                                java.time.LocalDate.of(2025, 1, 1),
                                java.time.LocalDate.of(2028, 12, 31),
                                new BigDecimal("8000"),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                null,
                                false)),
                        java.util.List.of(),
                        java.time.LocalDate.of(2028, 12, 31),
                        new BigDecimal("150000"),
                        InterestTreatment.PAY_OUT,
                        new BigDecimal("0.19"),
                        null,
                        false))
            .toList();
    var source = profile("700000", "575000");
    source =
        new InvestmentProfile(
            source.portfolioId(),
            source.currency(),
            source.marketPortfolioValue(),
            new BigDecimal("900000"),
            new BigDecimal("3175000"),
            source.liquidAssets(),
            source.illiquidAssets(),
            source.allocations(),
            BigDecimal.ZERO,
            new BigDecimal("38880"),
            new ProfileAssetProjection(
                bonds,
                BigDecimal.ZERO,
                2026,
                com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
            source.retirementReserve(),
            source.investmentCapital(),
            com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
                source.incomeSummary().marketIncomeYtd(),
                source.marketPortfolioValue(),
                new BigDecimal("38880"),
                new BigDecimal("900000"),
                source.incomeSummary().combinedAnnualIncome(),
                new BigDecimal("3175000")),
            com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
    var store = newPlanStore();
    var plan =
        store
            .service()
            .create(
                1L,
                "Bonds",
                SimulationAssumptions.defaults(source, 40, 45, 2025),
                PlanningBaseline.fromProfile(source, 2026));

    var restored = store.service().details(1L, plan.getId()).baseline();
    assertThat(restored.longTermPlanningState().assets()).hasSize(6);
    assertThat(
            restored.longTermPlanningState().assets().stream()
                .flatMap(asset -> asset.periods().stream())
                .map(ProjectedLongTermAsset.Period::annualIncome)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo("48000");
  }

  private static InMemoryPlanStore newPlanStore() {
    return new InMemoryPlanStore();
  }

  private static InvestmentProfile profile(String reserve, String investment) {
    var r = new BigDecimal(reserve);
    var i = new BigDecimal(investment);
    return new InvestmentProfile(
        1L,
        CurrencyType.PLN,
        r.add(i),
        BigDecimal.ZERO,
        r.add(i),
        r,
        BigDecimal.ZERO,
        List.of(),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new com.smartbox.investory.profile.api.model.ProfileAssetProjection(
            List.of(),
            java.math.BigDecimal.ZERO,
            0,
            com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
        (r == null ? java.math.BigDecimal.ZERO : r),
        r.add(i)
            .subtract((r == null ? java.math.BigDecimal.ZERO : r))
            .max(java.math.BigDecimal.ZERO),
        com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
            BigDecimal.ZERO, r.add(i), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, r.add(i)),
        com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
  }

  private static final class InMemoryPlanStore {
    private final SimulationPlanRepository plans = mock(SimulationPlanRepository.class);
    private final SimulationPlanRevisionRepository revisions =
        mock(SimulationPlanRevisionRepository.class);
    private final SimulationPlanRevisionEventRepository revisionEvents =
        mock(SimulationPlanRevisionEventRepository.class);
    private final List<SimulationPlanRevisionEntity> revisionRows = new ArrayList<>();
    private SimulationPlanEntity plan;

    private InMemoryPlanStore() {
      when(plans.existsByPortfolioIdAndName(any(), any())).thenReturn(false);
      when(plans.save(any()))
          .thenAnswer(
              invocation -> {
                var value = invocation.getArgument(0, SimulationPlanEntity.class);
                if (value.getId() == null) value.setId(1L);
                plan = value;
                return value;
              });
      when(plans.findByIdAndPortfolioId(any(), any()))
          .thenAnswer(invocation -> Optional.ofNullable(plan));
      when(plans.findByIdAndPortfolioIdForUpdate(any(), any()))
          .thenAnswer(invocation -> Optional.ofNullable(plan));
      when(revisions.save(any()))
          .thenAnswer(
              invocation -> {
                var value = invocation.getArgument(0, SimulationPlanRevisionEntity.class);
                if (value.getId() == null) value.setId((long) revisionRows.size() + 1);
                revisionRows.add(value);
                return value;
              });
      when(revisions.findAllBySimulationPlanIdOrderByRevisionNumberDesc(any()))
          .thenAnswer(invocation -> revisionRows.reversed());
      when(revisions.findByIdAndSimulationPlanId(any(), any()))
          .thenAnswer(
              invocation ->
                  revisionRows.stream()
                      .filter(r -> r.getId().equals(invocation.getArgument(0)))
                      .findFirst());
      when(revisionEvents.findAllByRevisionIdOrderByYearAscIdAsc(any())).thenReturn(List.of());
    }

    private SimulationPlanService service() {
      return new SimulationPlanService(
          plans, revisions, revisionEvents, new tools.jackson.databind.ObjectMapper());
    }
  }
}
