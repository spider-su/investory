package com.smartbox.investory.retirement.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.smartbox.investory.investment.application.InvestmentAnnualProjectionService;
import com.smartbox.investory.investment.api.InvestmentAnnualProjectionApi;
import com.smartbox.investory.longterm.application.service.LongTermAnnualProjectionService;
import com.smartbox.investory.retirement.api.InvestmentProfileFacade;
import com.smartbox.investory.retirement.infrastructure.simulation.*;
import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.simulation.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.longterm.api.LongTermAnnualProjectionApi;
import com.smartbox.investory.longterm.api.model.InterestTreatmentModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProjectionModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetTypeModel;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RetirementFrozenPlanLifecycleIntegrationTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-08-22T00:00:00Z"), ZoneOffset.UTC);

  @Test
  void realProjectionRemainsFrozenUntilExplicitRebaseline() {
    var liveA = profile("700000", "575000");
    var liveB = profile("900000", "900000");
    var assumptions = SimulationAssumptions.defaults(liveA, 40, 45, 2025);
    var baselineA = PlanningBaseline.fromProfile(liveA, 2026);
    var store = newPlanStore();
    var planService = store.service();
    var plan = planService.create(1L, "Frozen", assumptions, baselineA);

    var profiles = mock(InvestmentProfileFacade.class);
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

    var revisionB = planService.rebaseline(1L, plan.getId(), PlanningBaseline.fromProfile(liveB, 2026));
    var rebased = facade.load(1L, plan.getId(), 40, 45);
    assertThat(revisionB.getId()).isNotEqualTo(firstRevisionId);
    assertThat(rebased.scenarioResults().get(SimulationScenario.BASE)).isNotEqualTo(firstFuture);
    assertThat(planService.baseline(1L, plan.getId()).investmentCapital())
        .isEqualByComparingTo("900000");

    var assumptionsB = planService.assumptions(1L, plan.getId()).withRetirementAge(43);
    planService.update(1L, plan.getId(), "Frozen", assumptionsB);
    assertThat(planService.baseline(1L, plan.getId()).investmentCapital())
        .isEqualByComparingTo("900000");
  }

  @Test
  void newlySavedBaselineRetainsLongTermBondProjectionInputs() {
    var bonds = java.util.stream.IntStream.range(0, 6)
        .mapToObj(i -> new LongTermAssetProjectionModel(
            (long) i, "Bond " + i, LongTermAssetTypeModel.BOND, CurrencyType.PLN,
            new BigDecimal("150000"),
            List.of(new LongTermAssetProjectionModel.Period(
                java.time.LocalDate.of(2025, 1, 1), java.time.LocalDate.of(2028, 12, 31),
                new BigDecimal("8000"), BigDecimal.ZERO, BigDecimal.ZERO)),
            java.time.LocalDate.of(2028, 12, 31), new BigDecimal("150000"),
            InterestTreatmentModel.PAY_OUT, new BigDecimal("0.19")))
        .toList();
    var source = profile("700000", "575000");
    source = new InvestmentProfile(source.portfolioId(), source.currency(), source.marketPortfolioValue(),
        new BigDecimal("900000"), new BigDecimal("3175000"), source.historicalMarketInvestmentIncome(),
        new BigDecimal("38880"), source.totalInvestmentIncome(), source.liquidAssets(), source.illiquidAssets(),
        source.allocations(), source.longTermAssets(), BigDecimal.ZERO, new BigDecimal("38880"),
        new LongTermAnnualProjectionApi.PlanningState(bonds, BigDecimal.ZERO, 2026, LongTermAnnualProjectionApi.Source.PROJECTED),
        source.retirementReserve(), source.investmentCapital());
    var store = newPlanStore();
    var plan = store.service().create(1L, "Bonds", SimulationAssumptions.defaults(source, 40, 45, 2025),
        PlanningBaseline.fromProfile(source, 2026));

    var restored = store.service().baseline(1L, plan.getId());
    assertThat(restored.longTermPlanningState().assets()).hasSize(6);
    var quote = new LongTermAnnualProjectionService().quote(new LongTermAnnualProjectionApi.PlanningRequest(
        2027, BigDecimal.ZERO, restored.longTermPlanningState()));
    assertThat(quote.plannedCashFlows().stream()
        .filter(f -> f.kind() == LongTermAnnualProjectionApi.CashFlowKind.FIXED_INCOME)
        .map(LongTermAnnualProjectionApi.PlannedCashFlow::annualAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("48000");
  }

  private static InMemoryPlanStore newPlanStore() {
    return new InMemoryPlanStore();
  }

  private static InvestmentProfile profile(String reserve, String investment) {
    var r = new BigDecimal(reserve);
    var i = new BigDecimal(investment);
    return new InvestmentProfile(1L, CurrencyType.PLN, r.add(i), BigDecimal.ZERO,
        r.add(i), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, r, BigDecimal.ZERO,
        List.of(), List.of(), BigDecimal.ZERO, BigDecimal.ZERO);
  }

  private static final class InMemoryPlanStore {
    private final SimulationPlanRepository plans = mock(SimulationPlanRepository.class);
    private final SimulationPlanEventRepository legacyEvents = mock(SimulationPlanEventRepository.class);
    private final SimulationPlanRevisionRepository revisions = mock(SimulationPlanRevisionRepository.class);
    private final SimulationPlanRevisionEventRepository revisionEvents = mock(SimulationPlanRevisionEventRepository.class);
    private final List<SimulationPlanRevisionEntity> revisionRows = new ArrayList<>();
    private SimulationPlanEntity plan;

    private InMemoryPlanStore() {
      when(plans.existsByPortfolioIdAndName(any(), any())).thenReturn(false);
      when(plans.save(any())).thenAnswer(invocation -> {
        var value = invocation.getArgument(0, SimulationPlanEntity.class);
        if (value.getId() == null) value.setId(1L);
        plan = value;
        return value;
      });
      when(plans.findByIdAndPortfolioId(any(), any())).thenAnswer(invocation -> Optional.ofNullable(plan));
      when(revisions.save(any())).thenAnswer(invocation -> {
        var value = invocation.getArgument(0, SimulationPlanRevisionEntity.class);
        if (value.getId() == null) value.setId((long) revisionRows.size() + 1);
        revisionRows.add(value);
        return value;
      });
      when(revisions.findAllBySimulationPlanIdOrderByRevisionNumberDesc(any()))
          .thenAnswer(invocation -> revisionRows.reversed());
      when(revisions.findByIdAndSimulationPlanId(any(), any())).thenAnswer(invocation ->
          revisionRows.stream().filter(r -> r.getId().equals(invocation.getArgument(0))).findFirst());
      when(revisionEvents.findAllByRevisionIdOrderByYearAscIdAsc(any())).thenReturn(List.of());
    }

    private SimulationPlanService service() {
      return new SimulationPlanService(plans, legacyEvents, revisions, revisionEvents);
    }
  }
}
