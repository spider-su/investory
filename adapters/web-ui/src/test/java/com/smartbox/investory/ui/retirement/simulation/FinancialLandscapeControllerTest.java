package com.smartbox.investory.ui.retirement.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.contract.RetirementProjectionContracts;
import com.smartbox.investory.retirement.api.contract.RetirementTimelineContracts;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.ui.profile.ProfileClient;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

class FinancialLandscapeControllerTest {
  private final ProfileClient profiles = mock(ProfileClient.class);
  private final RetirementPlanClient plans = mock(RetirementPlanClient.class);
  private final RetirementProjectionClient projections = mock(RetirementProjectionClient.class);
  private final RetirementTimelineClient timeline = mock(RetirementTimelineClient.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-10-13T10:00:00Z"), ZoneOffset.UTC);
  private final FinancialLandscapeController controller =
      new FinancialLandscapeController(profiles, plans, projections, timeline, clock);

  @BeforeEach
  void setup() {
    InvestmentProfile profile = mock(InvestmentProfile.class);
    when(profile.currency()).thenReturn(CurrencyType.PLN);
    when(profile.liquidAssets()).thenReturn(new BigDecimal("120000"));
    when(profile.illiquidAssets()).thenReturn(new BigDecimal("850000"));
    when(profile.totalNetWorth()).thenReturn(new BigDecimal("970000"));
    when(profile.retirementReserve()).thenReturn(new BigDecimal("90000"));
    when(profiles.loadProfile(7L)).thenReturn(profile);
    when(plans.resolvePlanId(7L, null)).thenReturn(Optional.of(11L));
    RetirementProjection projection = mock(RetirementProjection.class);
    when(projections.loadDisplay(eq(7L), eq(11L), isNull(), isNull(), eq(CurrencyType.PLN)))
        .thenReturn(new RetirementProjectionContracts.DisplayProjectionResponse(projection, Map.of()));
  }

  @Test
  void usesOnlyLivePlanningCashFlowAndProfileAssetFacts() {
    var live = new PlanningTimelineYear(2026, 40, PlanningTimelineState.LIVE, null, null, null);
    var projected = new PlanningTimelineYear(2027, 41, PlanningTimelineState.PROJECTED, null, null, null);
    PlanningTimelineMoney money = mock(PlanningTimelineMoney.class);
    when(money.totalIncome()).thenReturn(new BigDecimal("240000"));
    when(money.annualCosts()).thenReturn(new BigDecimal("180000"));
    when(money.fundingSurplus()).thenReturn(new BigDecimal("60000"));
    when(money.fundingGap()).thenReturn(BigDecimal.ZERO);
    when(money.rentalIncome()).thenReturn(new BigDecimal("120000"));
    when(money.bondIncome()).thenReturn(new BigDecimal("10000"));
    when(money.employmentIncome()).thenReturn(new BigDecimal("110000"));
    when(money.bondReturn()).thenReturn(new BigDecimal("999999"));
    when(money.equityReturn()).thenReturn(new BigDecimal("999999"));
    when(money.investmentWithdrawal()).thenReturn(new BigDecimal("999999"));
    when(timeline.loadForwardTimeline(eq(7L), any(), eq(SimulationScenario.BASE), eq(CurrencyType.PLN)))
        .thenReturn(new RetirementTimelineContracts.ForwardTimelineResponse(
            new PlanningTimeline(CurrencyType.PLN, List.of(live, projected)), Map.of(2026, money)));

    var model = new ConcurrentModel();
    assertThat(controller.landscape(7L, "full", model)).isEqualTo("financial-landscape");
    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) model.getAttribute("landscape");
    assertThat(result).containsEntry("annualIncome", new BigDecimal("240000"))
        .containsEntry("annualExpenses", new BigDecimal("180000"))
        .containsEntry("annualSurplus", new BigDecimal("60000"))
        .containsEntry("liquidAssets", new BigDecimal("120000"))
        .containsEntry("illiquidAssets", new BigDecimal("850000"))
        .containsEntry("currency", "PLN").containsEntry("year", 2026);
    verify(timeline).ensurePlanningTimeline(7L);
    verify(money, never()).bondReturn();
    verify(money, never()).equityReturn();
    verify(money, never()).investmentWithdrawal();
  }

  @Test
  void compactUsesSameDataAdapter() {
    when(timeline.loadForwardTimeline(eq(7L), any(), eq(SimulationScenario.BASE), eq(CurrencyType.PLN)))
        .thenReturn(new RetirementTimelineContracts.ForwardTimelineResponse(
            new PlanningTimeline(CurrencyType.PLN, List.of()), Map.of()));
    var model = new ConcurrentModel();
    assertThat(controller.landscape(7L, "compact", model)).isEqualTo("financial-landscape-compact");
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) model.getAttribute("landscape");
    assertThat(data).containsEntry("liquidAssets", new BigDecimal("120000"));
  }

  @Test
  void gracefullyHandlesMissingLiveYearAndPlan() {
    when(plans.resolvePlanId(7L, null)).thenReturn(Optional.empty());
    RetirementProjection projection = mock(RetirementProjection.class);
    when(projections.loadDisplay(eq(7L), isNull(), isNull(), isNull(), eq(CurrencyType.PLN)))
        .thenReturn(new RetirementProjectionContracts.DisplayProjectionResponse(projection, Map.of()));
    when(timeline.loadForwardTimeline(eq(7L), any(), eq(SimulationScenario.BASE), eq(CurrencyType.PLN)))
        .thenReturn(new RetirementTimelineContracts.ForwardTimelineResponse(
            new PlanningTimeline(CurrencyType.PLN, List.of()), Map.of()));

    var model = new ConcurrentModel();
    assertThat(controller.landscape(7L, "full", model)).isEqualTo("financial-landscape");
    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) model.getAttribute("landscape");
    assertThat(result).containsEntry("year", 2026)
        .containsEntry("annualIncome", null).containsEntry("annualExpenses", null);
    verify(projections).loadDisplay(7L, null, null, null, CurrencyType.PLN);
  }
}
