package com.smartbox.investory.ui.retirement.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.profile.api.model.ProfileIncomeSummary;
import com.smartbox.investory.retirement.api.RetirementAnnualCostsApi;
import com.smartbox.investory.retirement.api.contract.RetirementTimelineContracts;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FinancialLandscapePageAssemblerTest {
  private final RetirementPlanClient plans = mock(RetirementPlanClient.class);
  private final RetirementProjectionClient projections = mock(RetirementProjectionClient.class);
  private final RetirementTimelineClient timeline = mock(RetirementTimelineClient.class);
  private final AnnualCostsClient annualCosts = mock(AnnualCostsClient.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-10-13T10:00:00Z"), ZoneOffset.UTC);
  private final FinancialLandscapePageAssembler assembler =
      new FinancialLandscapePageAssembler(plans, projections, timeline, annualCosts, clock);

  @BeforeEach
  void setup() {
    InvestmentProfile profile = mock(InvestmentProfile.class);
    when(profile.currency()).thenReturn(CurrencyType.PLN);
    ProfileIncomeSummary summary = mock(ProfileIncomeSummary.class);
    when(summary.combinedNetAnnualIncome()).thenReturn(new BigDecimal("253100"));
    when(summary.marketNetAnnualIncome()).thenReturn(new BigDecimal("63100"));
    when(summary.longTermAnnualIncome()).thenReturn(new BigDecimal("190000"));
    when(profile.incomeSummary()).thenReturn(summary);
    when(profile.liquidAssets()).thenReturn(new BigDecimal("120000"));
    when(profile.illiquidAssets()).thenReturn(new BigDecimal("850000"));
    when(profile.totalNetWorth()).thenReturn(new BigDecimal("970000"));
    when(profile.retirementReserve()).thenReturn(new BigDecimal("90000"));
    when(plans.resolvePlanId(7L, null)).thenReturn(Optional.of(11L));
    RetirementProjection projection = mock(RetirementProjection.class);
    when(projection.profile()).thenReturn(profile);
    when(projections.load(7L, null)).thenReturn(projection);
    when(projections.load(7L, 11L)).thenReturn(projection);
  }

  @Test
  void assemblesLiveYearAndPortfolioFacts() {
    var live = new PlanningTimelineYear(2026, 40, PlanningTimelineState.LIVE, null, null, null);
    var projected =
        new PlanningTimelineYear(2027, 41, PlanningTimelineState.PROJECTED, null, null, null);
    PlanningTimelineMoney money = mock(PlanningTimelineMoney.class);
    when(money.totalIncome()).thenReturn(new BigDecimal("240000"));
    when(money.annualCosts()).thenReturn(new BigDecimal("180000"));
    when(money.fundingSurplus()).thenReturn(new BigDecimal("60000"));
    when(money.fundingGap()).thenReturn(BigDecimal.ZERO);
    when(timeline.loadForwardTimeline(
            eq(7L), any(), eq(SimulationScenario.BASE), eq(CurrencyType.PLN)))
        .thenReturn(
            new RetirementTimelineContracts.ForwardTimelineResponse(
                new PlanningTimeline(CurrencyType.PLN, List.of(live, projected)),
                Map.of(2026, money)));
    when(annualCosts.load(7L, 11L, 2026))
        .thenReturn(
            new RetirementAnnualCostsApi.AnnualCosts(
                11L,
                2026,
                "Plan",
                List.of(new RetirementAnnualCostsApi.Group(1L, "Housing", new BigDecimal("6000"), 0)),
                new BigDecimal("6000"),
                new BigDecimal("72000"),
                BigDecimal.ZERO,
                null,
                null,
                BigDecimal.ZERO));

    var page = assembler.assemble(7L);

    assertThat(page.portfolioId()).isEqualTo(7L);
    assertThat(page.currency()).isEqualTo("PLN");
    assertThat(page.year()).isEqualTo(2026);
    assertThat(page.annualIncome()).isEqualByComparingTo("253100");
    assertThat(page.annualCashIncome()).isEqualByComparingTo("240000");
    assertThat(page.annualMarketResult()).isEqualByComparingTo("63100");
    assertThat(page.annualLongTermIncome()).isEqualByComparingTo("190000");
    assertThat(page.annualExpenses()).isEqualByComparingTo("180000");
    assertThat(page.annualSurplus()).isEqualByComparingTo("60000");
    assertThat(page.annualGap()).isEqualByComparingTo("0");
    assertThat(page.liquidAssets()).isEqualByComparingTo("120000");
    assertThat(page.reserve()).isEqualByComparingTo("90000");
    assertThat(page.illiquidAssets()).isEqualByComparingTo("850000");
    assertThat(page.netWorth()).isEqualByComparingTo("970000");
    assertThat(page.expenseGroups()).hasSize(1);
    verify(timeline, never()).ensurePlanningTimeline(anyLong());
    verify(projections).load(7L, 11L);
    verify(annualCosts).load(7L, 11L, 2026);
  }

  @Test
  void fallsBackToCurrentYearWhenLiveYearMissing() {
    when(plans.resolvePlanId(7L, null)).thenReturn(Optional.empty());
    when(timeline.loadForwardTimeline(
            eq(7L), any(), eq(SimulationScenario.BASE), eq(CurrencyType.PLN)))
        .thenReturn(
            new RetirementTimelineContracts.ForwardTimelineResponse(
                new PlanningTimeline(CurrencyType.PLN, List.of()), Map.of()));

    var page = assembler.assemble(7L);

    assertThat(page.year()).isEqualTo(2026);
    assertThat(page.annualCashIncome()).isNull();
    assertThat(page.annualExpenses()).isNull();
    assertThat(page.expenseGroups()).isEmpty();
    verify(projections).load(7L, null);
    verifyNoInteractions(annualCosts);
  }
}
