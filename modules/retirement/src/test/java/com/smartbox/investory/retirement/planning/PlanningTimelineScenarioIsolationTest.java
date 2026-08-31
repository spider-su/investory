package com.smartbox.investory.retirement.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.api.reporting.HistoricalPortfolioActualsReader;
import com.smartbox.investory.profile.api.model.EconomicBucket;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.retirement.api.model.SimulationCustomDeltas;
import com.smartbox.investory.retirement.api.model.SimulationResult;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import com.smartbox.investory.retirement.api.model.SimulationYear;
import com.smartbox.investory.retirement.infrastructure.planning.PlanningYearRepository;
import com.smartbox.investory.retirement.infrastructure.planning.PlanningYearValueRepository;
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

/** Scenario overlays are future-only; historical and LIVE rows are scenario invariant. */
@DisplayName("Planning Timeline Scenario Isolation")
class PlanningTimelineScenarioIsolationTest {

  @DisplayName("changing Scenario Changes Projected Rows Only")
  @Test
  void changingScenarioChangesProjectedRowsOnly() {
    Clock clock = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);
    PlanningYearRepository years = mock(PlanningYearRepository.class);
    PlanningYearValueRepository values = mock(PlanningYearValueRepository.class);
    HistoricalPortfolioActualsReader historical = mock(HistoricalPortfolioActualsReader.class);
    HistoricalLongTermAssetYearSource historicalLongTerm =
        mock(HistoricalLongTermAssetYearSource.class);
    RetirementSimulation simulations = mock(RetirementSimulation.class);
    CurrentYearProjectionBridge bridge = mock(CurrentYearProjectionBridge.class);
    ForwardSimulationContextFactory contexts = new ForwardSimulationContextFactory(clock);
    PlanningTimelineFacade facade =
        new PlanningTimelineFacade(
            years, values, historical, historicalLongTerm, simulations, bridge, clock, contexts);

    InvestmentProfile profile = profile();
    SimulationAssumptions assumptions =
        SimulationAssumptions.defaults(profile, 40, 42, 2025)
            .withRetirementAge(41)
            .withRecurringSpending(new BigDecimal("100"));
    var context = contexts.create(profile, assumptions);
    var forward = new ForwardSimulationInput(context, profile, context.forwardAssumptions(), null);

    when(years.findByPortfolioIdAndYear(1L, 2025)).thenReturn(Optional.empty());
    when(years.findByPortfolioIdAndYear(1L, 2026)).thenReturn(Optional.empty());
    when(simulations.simulate(any(), any(), eq(SimulationScenario.BASE), eq(2026)))
        .thenReturn(result(SimulationScenario.BASE, "90"));
    when(simulations.simulate(any(), any(), eq(SimulationScenario.CONSERVATIVE), eq(2026)))
        .thenReturn(result(SimulationScenario.CONSERVATIVE, "70"));
    when(simulations.simulate(any(), any(), eq(SimulationScenario.OPTIMISTIC), eq(2026)))
        .thenReturn(result(SimulationScenario.OPTIMISTIC, "110"));
    var custom =
        new SimulationCustomDeltas(
            new BigDecimal("0.04"),
            new BigDecimal("0.02"),
            new BigDecimal("0.03"),
            new BigDecimal("0.05"),
            new BigDecimal("0.02"));
    when(simulations.simulate(any(), any(), eq(SimulationScenario.CUSTOM), eq(2026), eq(custom)))
        .thenReturn(result(SimulationScenario.CUSTOM, "140"));

    PlanningTimeline base =
        facade.loadForwardTimeline(1L, profile, forward, SimulationScenario.BASE);
    PlanningTimeline conservative =
        facade.loadForwardTimeline(1L, profile, forward, SimulationScenario.CONSERVATIVE);
    PlanningTimeline optimistic =
        facade.loadForwardTimeline(1L, profile, forward, SimulationScenario.OPTIMISTIC);

    assertSamePastAndLiveRows(base, conservative);
    assertSamePastAndLiveRows(base, optimistic);

    assertThat(row(base, 2027).projection().cashEnd()).isEqualByComparingTo("90");
    assertThat(row(conservative, 2027).projection().cashEnd()).isEqualByComparingTo("70");
    assertThat(row(optimistic, 2027).projection().cashEnd()).isEqualByComparingTo("110");

    PlanningTimeline customTimeline =
        facade.loadForwardTimeline(1L, profile, forward, SimulationScenario.CUSTOM, custom);
    assertThat(row(customTimeline, 2027).projection().cashEnd()).isEqualByComparingTo("140");
  }

  private static void assertSamePastAndLiveRows(
      PlanningTimeline expected, PlanningTimeline actual) {
    PlanningTimelineYear expectedHistorical = row(expected, 2025);
    PlanningTimelineYear actualHistorical = row(actual, 2025);
    assertThat(actualHistorical.state()).isEqualTo(expectedHistorical.state());
    assertThat(actualHistorical.age()).isEqualTo(expectedHistorical.age());
    assertThat(actualHistorical.past()).isEqualTo(expectedHistorical.past());
    assertThat(actualHistorical.projection()).isNull();

    PlanningTimelineYear expectedLive = row(expected, 2026);
    PlanningTimelineYear actualLive = row(actual, 2026);
    assertThat(actualLive.state()).isEqualTo(PlanningTimelineState.LIVE);
    assertThat(actualLive.age()).isEqualTo(expectedLive.age());
    assertThat(actualLive.current().actualValues())
        .isEqualTo(expectedLive.current().actualValues());
    assertThat(actualLive.current().expectedValues())
        .isEqualTo(expectedLive.current().expectedValues());
    assertThat(actualLive.projection()).isNull();
  }

  private static PlanningTimelineYear row(PlanningTimeline timeline, int year) {
    return timeline.years().stream()
        .filter(value -> value.year() == year)
        .findFirst()
        .orElseThrow();
  }

  private static SimulationResult result(SimulationScenario scenario, String cashEnd) {
    var cash = bucket(EconomicBucket.LIQUID_CASH, "100", cashEnd);
    var zeroBonds = bucket(EconomicBucket.FIXED_INCOME, "0", "0");
    var zeroEquity = bucket(EconomicBucket.EQUITY, "0", "0");
    var zeroRealEstate = bucket(EconomicBucket.REAL_ESTATE, "0", "0");
    SimulationYear year =
        SimulationYear.bucket(
            42,
            2027,
            true,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            cash,
            zeroBonds,
            zeroEquity,
            zeroRealEstate,
            BigDecimal.ZERO,
            BigDecimal.ZERO);
    return new SimulationResult(
        scenario, false, null, BigDecimal.ZERO, BigDecimal.ZERO, List.of(year));
  }

  private static BucketResult bucket(EconomicBucket type, String start, String end) {
    BigDecimal startValue = new BigDecimal(start);
    BigDecimal endValue = new BigDecimal(end);
    return new BucketResult(
        type,
        startValue,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        startValue.subtract(endValue).max(BigDecimal.ZERO),
        endValue);
  }

  private static InvestmentProfile profile() {
    return new InvestmentProfile(
        1L,
        CurrencyType.PLN,
        new BigDecimal("100"),
        BigDecimal.ZERO,
        new BigDecimal("100"),
        new BigDecimal("100"),
        BigDecimal.ZERO,
        List.of(),
        null,
        null,
        new com.smartbox.investory.profile.api.model.ProfileAssetProjection(
            List.of(),
            java.math.BigDecimal.ZERO,
            0,
            com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
        (new BigDecimal("100") == null ? java.math.BigDecimal.ZERO : new BigDecimal("100")),
        new BigDecimal("100")
            .subtract(
                (new BigDecimal("100") == null ? java.math.BigDecimal.ZERO : new BigDecimal("100")))
            .max(java.math.BigDecimal.ZERO),
        com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
            BigDecimal.ZERO,
            new BigDecimal("100"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("100")),
        com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
  }
}
