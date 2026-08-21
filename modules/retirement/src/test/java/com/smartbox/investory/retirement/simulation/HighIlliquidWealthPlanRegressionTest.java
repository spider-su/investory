package com.smartbox.investory.retirement.simulation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.smartbox.investory.longterm.infrastructure.InterestTreatment;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType;
import com.smartbox.investory.longterm.infrastructure.rental.CashFlowType;
import com.smartbox.investory.retirement.infrastructure.simulation.SimulationPlanEntity;
import com.smartbox.investory.retirement.infrastructure.simulation.SimulationPlanEventRepository;
import com.smartbox.investory.retirement.infrastructure.simulation.SimulationPlanRepository;
import com.smartbox.investory.retirement.infrastructure.simulation.SimulationPlanRevisionEntity;
import com.smartbox.investory.retirement.infrastructure.simulation.SimulationPlanRevisionEventRepository;
import com.smartbox.investory.retirement.infrastructure.simulation.SimulationPlanRevisionRepository;
import com.smartbox.investory.retirement.planning.CurrentYearProjectionBridge;
import com.smartbox.investory.retirement.planning.ForwardSimulationInput;
import com.smartbox.investory.retirement.planning.ForwardSimulationInputService;
import com.smartbox.investory.retirement.profile.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** End-to-end economic sanity check for the saved-plan forward path. */
class HighIlliquidWealthPlanRegressionTest {
  private static final BigDecimal ZERO = BigDecimal.ZERO;
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);

  @Test
  void highIlliquidWealthPlanDoesNotExplodeAfterLiquidityFailure() {
    RetirementSimulationService simulator = new RetirementSimulationService();
    ForwardSimulationContextFactory contexts = new ForwardSimulationContextFactory(CLOCK);
    CurrentYearProjectionBridge bridge =
        new CurrentYearProjectionBridge(CLOCK, simulator, contexts);
    ForwardSimulationInput input =
        new ForwardSimulationInputService(contexts, bridge)
            .prepare(profile(), savedRevisionAssumptions());

    SimulationAssumptions forward = input.forwardAssumptions().orElseThrow();
    assertEquals(2027, forward.startYear());
    assertEquals(41, forward.currentAge());
    assertEquals(new BigDecimal("0.02"), forward.cashReturnRate());
    assertEquals(new BigDecimal("0.05"), forward.fixedIncomeReturnRate());
    assertEquals(new BigDecimal("0.05"), forward.realEstateReturnRate());
    assertEquals(new BigDecimal("0.08"), forward.equityReturnRate());
    assertEquals(new BigDecimal("0.02"), forward.otherReturnRate());
    assertEquals(new BigDecimal("0.025"), forward.inflationRate());
    assertEquals(new BigDecimal("0.025"), forward.spendingGrowthRate());
    assertEquals(new BigDecimal("0.19"), forward.capitalGainTaxRate());
    assertEquals(new BigDecimal("0.95"), forward.equityGainHarvestRate());
    assertEquals(
        new BigDecimal("0.05"),
        input.bridgedProfile().longTermAssets().getFirst().periods().getFirst().annualReturnRate());
    assertTrue(input.context().historicalEventCountExcluded() >= 0);

    SimulationResult base =
        simulator.simulate(input.bridgedProfile(), forward, SimulationScenario.BASE);
    SimulationYear[] checkpoints = {
      year(base, 2044),
      year(base, 2045),
      year(base, 2046),
      year(base, 2047),
      year(base, 2050),
      base.finalYear()
    };
    for (SimulationYear year : checkpoints) {
      System.out.printf(
          "trace year=%d age=%d cash=%s fixed=%s equity=%s other=%s contractual=%s spendable=%s realEstate=%s financial=%s unfunded=%s netWorth=%s%n",
          year.year(),
          year.age(),
          compact(year.cashEnd()),
          compact(year.fixedIncomeEnd()),
          compact(year.equityEnd()),
          compact(year.otherEnd()),
          compact(year.contractualAssetsEnd()),
          compact(year.spendableAssetsEnd()),
          compact(year.realEstateEnd()),
          compact(year.financialAssetsEnd()),
          compact(year.unfundedAmount()),
          compact(year.endNetWorth()));
    }
    assertTrue(base.simulationFailed(), "high spending should eventually exhaust spendable assets");
    assertNotNull(base.failureAge());
    System.out.printf(
        "failure age=%d calendarYear=%d%n", base.failureAge(), base.finalYear().year());
    BigDecimal finalRealEstate = base.finalYear().realEstateEnd();
    assertEquals(ZERO, finalRealEstate, "property value is not part of simulated wealth");
    assertEquals(
        0,
        base.finalYear()
            .endNetWorth()
            .compareTo(
                base.finalYear().financialAssetsEnd().add(base.finalYear().totalIlliquidAssets())));
    assertTrue(base.finalYear().totalIlliquidAssets().compareTo(ZERO) > 0);
    assertEquals(
        0,
        base.finalYear()
            .totalIlliquidAssets()
            .compareTo(
                base.finalYear().realEstateEnd().add(base.finalYear().contractualAssetsEnd())));

    SimulationEvaluationService evaluations = new SimulationEvaluationService(simulator);
    SimulationEvaluation baseEvaluation =
        evaluations.evaluate(input.bridgedProfile(), forward, SimulationScenario.BASE);
    assertEquals(base.finalYear().endNetWorth(), baseEvaluation.decisionSummary().finalNetWorth());

    var scenarios = simulator.compareScenarios(input.bridgedProfile(), forward);
    assertEquals(
        base.finalYear().endNetWorth(),
        scenarios.get(SimulationScenario.BASE).finalYear().endNetWorth());
    assertEquals(
        base.finalYear().financialAssetsEnd().add(base.finalYear().totalIlliquidAssets()),
        base.finalYear().endNetWorth());
    var ordered =
        List.of(
            SimulationScenario.BASE,
            SimulationScenario.CONSERVATIVE,
            SimulationScenario.OPTIMISTIC);
    for (SimulationScenario scenario : ordered) {
      assertEquals(
          scenarios.get(scenario).finalYear().endNetWorth(),
          simulator.simulate(input.bridgedProfile(), forward, scenario).finalYear().endNetWorth());
    }
  }

  private static SimulationYear year(SimulationResult result, int calendarYear) {
    return result.years().stream()
        .filter(year -> year.year() == calendarYear)
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing calendar year " + calendarYear));
  }

  private static SimulationAssumptions assumptions() {
    return new SimulationAssumptions(
        40,
        80,
        bd("198918.77"),
        bd("0.025"),
        bd("0.02"),
        bd("0.05"),
        bd("0.08"),
        bd("0.05"),
        bd("0.02"),
        67,
        bd("69621.16"),
        bd("0.19"),
        2026,
        bd("59675.63"),
        List.of(),
        bd("0.02"),
        bd("0.025"),
        SimulationFundingStrategy.RESERVE_AND_HARVEST,
        bd("10"),
        bd("0.08"),
        bd("0.95"),
        false,
        42,
        bd("238702.52"),
        bd("119351.26"));
  }

  private static SimulationAssumptions savedRevisionAssumptions() {
    SimulationPlanRepository plans = mock(SimulationPlanRepository.class);
    SimulationPlanEventRepository legacyEvents = mock(SimulationPlanEventRepository.class);
    SimulationPlanRevisionRepository revisions = mock(SimulationPlanRevisionRepository.class);
    SimulationPlanRevisionEventRepository revisionEvents =
        mock(SimulationPlanRevisionEventRepository.class);
    SimulationPlanService planService =
        new SimulationPlanService(plans, legacyEvents, revisions, revisionEvents);
    when(plans.findAllByPortfolioIdOrderByName(7L)).thenReturn(List.of());
    when(plans.save(any()))
        .thenAnswer(
            invocation -> {
              SimulationPlanEntity plan = invocation.getArgument(0);
              if (plan.getId() == null) plan.setId(70L);
              return plan;
            });
    java.util.concurrent.atomic.AtomicReference<SimulationPlanRevisionEntity> storedRevision =
        new java.util.concurrent.atomic.AtomicReference<>();
    when(revisions.save(any()))
        .thenAnswer(
            invocation -> {
              SimulationPlanRevisionEntity revision = invocation.getArgument(0);
              revision.setId(700L);
              storedRevision.set(revision);
              return revision;
            });
    SimulationPlanEntity saved = planService.create(7L, "Observed plan", assumptions());
    when(plans.findByIdAndPortfolioId(70L, 7L)).thenReturn(java.util.Optional.of(saved));
    when(revisions.findByIdAndSimulationPlanId(700L, 70L))
        .thenReturn(java.util.Optional.of(storedRevision.get()));
    when(revisionEvents.findAllByRevisionIdOrderByYearAscIdAsc(700L)).thenReturn(List.of());
    return planService.assumptions(planService.get(7L, 70L));
  }

  private static InvestmentProfile profile() {
    BigDecimal realEstate = bd("4550000");
    BigDecimal deposit = bd("100000");
    BigDecimal market = bd("614365");
    return new InvestmentProfile(
        7L,
        CurrencyType.PLN,
        market,
        realEstate.add(deposit),
        market.add(realEstate).add(deposit),
        ZERO,
        bd("171415"),
        bd("171415"),
        market,
        realEstate.add(deposit),
        List.of(
            allocation(EconomicBucket.LIQUID_CASH, bd("100000")),
            allocation(EconomicBucket.FIXED_INCOME, bd("100000").add(deposit)),
            allocation(EconomicBucket.EQUITY, bd("414365")),
            allocation(EconomicBucket.REAL_ESTATE, realEstate)),
        List.of(realEstateAsset(realEstate), depositAsset(deposit)));
  }

  private static ProfileAllocation allocation(EconomicBucket bucket, BigDecimal value) {
    return new ProfileAllocation(
        bucket,
        value,
        ZERO,
        bucket == EconomicBucket.REAL_ESTATE ? Liquidity.ILLIQUID : Liquidity.LIQUID);
  }

  private static ProjectedLongTermAsset realEstateAsset(BigDecimal value) {
    return new ProjectedLongTermAsset(
        1L,
        "Property",
        LongTermAssetType.REAL_ESTATE,
        EconomicBucket.REAL_ESTATE,
        CurrencyType.PLN,
        value,
        Liquidity.ILLIQUID,
        List.of(
            new ProjectedLongTermAsset.Period(
                LocalDate.of(2026, 1, 1), null, bd("171415"), ZERO, bd("0.05"), CashFlowType.RENT)),
        null,
        null,
        null,
        bd("0.085"),
        ZERO);
  }

  private static ProjectedLongTermAsset depositAsset(BigDecimal value) {
    return new ProjectedLongTermAsset(
        2L,
        "Deposit",
        LongTermAssetType.DEPOSIT,
        EconomicBucket.FIXED_INCOME,
        CurrencyType.PLN,
        value,
        Liquidity.LIQUID,
        List.of(
            new ProjectedLongTermAsset.Period(
                LocalDate.of(2026, 1, 1), null, ZERO, ZERO, bd("0.05"))),
        LocalDate.of(2090, 12, 31),
        null,
        InterestTreatment.PAY_OUT,
        bd("0.19"),
        null);
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }

  private static BigDecimal compact(BigDecimal value) {
    return value.setScale(2, java.math.RoundingMode.HALF_UP);
  }
}
