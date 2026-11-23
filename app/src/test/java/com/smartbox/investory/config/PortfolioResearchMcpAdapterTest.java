package com.smartbox.investory.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import com.smartbox.investory.investment.api.reporting.model.OverviewView;
import com.smartbox.investory.profile.api.ProfileSnapshotReader;
import com.smartbox.investory.profile.api.contract.ProfileResponse;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.profile.web.ProfileResponseMapper;
import com.smartbox.investory.rag.InvestoryRagClient;
import com.smartbox.investory.retirement.api.RetirementPlanApi;
import com.smartbox.investory.retirement.api.RetirementProjectionApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class PortfolioResearchMcpAdapterTest {
  private final InvestmentDashboardApi investment = mock(InvestmentDashboardApi.class);
  private final ProfileSnapshotReader profiles = mock(ProfileSnapshotReader.class);
  private final ProfileResponseMapper mapper = mock(ProfileResponseMapper.class);
  private final RetirementPlanApi plans = mock(RetirementPlanApi.class);
  private final RetirementProjectionApi projections = mock(RetirementProjectionApi.class);
  private final ObjectProvider<InvestoryRagClient> provider = mock(ObjectProvider.class);
  private final PortfolioResearchMcpAdapter adapter =
      new PortfolioResearchMcpAdapter(
          investment,
          mock(com.smartbox.investory.investment.api.asset.InvestmentAssetApi.class),
          profiles,
          mapper,
          plans,
          mock(com.smartbox.investory.longterm.api.LongTermAssetsApi.class),
          projections,
          provider,
          mock(ObjectProvider.class));

  @Test
  void periodAndPortfolioArePreservedWithoutModelArithmetic() {
    var page = mock(InvestmentDashboardApi.DashboardPageView.class);
    var overview = mock(OverviewView.class);
    when(investment.loadDashboard(any())).thenReturn(page);
    when(page.overview()).thenReturn(overview);
    when(overview.baseCurrency()).thenReturn(CurrencyType.PLN);
    when(overview.totalProfit()).thenReturn(new BigDecimal("12.3456789"));
    var result = adapter.performance(21, DashboardPeriod.MAX);
    assertEquals("12.3456789", result.profit());
    assertEquals("MAX", result.period());
    assertEquals("PLN", result.currency());
    var query = ArgumentCaptor.forClass(InvestmentDashboardApi.DashboardQuery.class);
    verify(investment).loadDashboard(query.capture());
    assertEquals(21L, query.getValue().portfolioId());
    assertEquals(DashboardPeriod.MAX, query.getValue().period());
    verifyNoInteractions(profiles, mapper, plans, provider, mock(ObjectProvider.class));
  }

  @Test
  void searchOnlyForwardsPublicQuestionAndLimit() {
    var client = mock(InvestoryRagClient.class);
    when(provider.getIfAvailable()).thenReturn(client);
    when(client.searchDocumentation("transfers", 2))
        .thenReturn(
            new InvestoryRagClient.Documentation(
                List.of(
                    new InvestoryRagClient.Snippet(
                        "accounting.md", "Internal transfers conserve capital.", 0.9))));
    var result = adapter.search(21, "transfers", 2);
    assertEquals("accounting.md", result.matches().getFirst().source());
    verify(client).searchDocumentation("transfers", 2);
    verifyNoInteractions(
        investment,
        mock(com.smartbox.investory.investment.api.asset.InvestmentAssetApi.class),
        profiles,
        mapper,
        plans);
  }

  @Test
  void whatIfUsesCopiedAssumptionsAndNeverMutatesSavedPlan() {
    var baseline = SimulationAssumptions.defaults(41, 90, 2026);
    var profile = mock(InvestmentProfile.class);
    var response = mock(ProfileResponse.class);
    var projected = mock(RetirementProjection.class);
    when(plans.resolvePlanId(21L, null)).thenReturn(Optional.of(7L));
    when(plans.details(21L, 7L)).thenReturn(new PlanDetails(7L, "Base", baseline, null));
    when(profiles.loadProfile(21L)).thenReturn(profile);
    when(mapper.map(profile)).thenReturn(response);
    when(response.currency()).thenReturn(CurrencyType.PLN);
    var summary = mock(SimulationDecisionSummary.class);
    when(summary.finalNetWorth()).thenReturn(new BigDecimal("600000"));
    when(projected.summaries()).thenReturn(Map.of(SimulationScenario.BASE, summary));
    when(projections.project(eq(profile), any(SimulationAssumptions.class), isNull()))
        .thenReturn(projected);

    var whatIf =
        adapter.retirementWhatIf(21L, "Base", new BigDecimal("216000"), new BigDecimal("12000"));

    assertTrue(whatIf.available());
    assertEquals("216000", whatIf.annualLivingExpenses());
    assertEquals("12000", whatIf.annualDiscretionaryExpenses());
    assertEquals("600000", whatIf.scenarios().getFirst().finalNetWorth());
    assertEquals(new BigDecimal("180000"), baseline.annualLivingExpenses());
    var assumptions = ArgumentCaptor.forClass(SimulationAssumptions.class);
    verify(projections).project(eq(profile), assumptions.capture(), isNull());
    assertEquals(new BigDecimal("216000"), assumptions.getValue().annualLivingExpenses());
    assertNotSame(baseline, assumptions.getValue());
    verify(plans, never()).createPlan(any());
    verify(plans, never()).updatePlan(any());
    verify(plans, never()).rebaselinePlan(any(), any(), any());
  }

  @Test
  void whatIfRejectsUnexpectedSelectedPlanBeforeProjection() {
    when(plans.resolvePlanId(21L, null)).thenReturn(Optional.of(7L));
    when(plans.details(21L, 7L))
        .thenReturn(
            new PlanDetails(7L, "Actual", SimulationAssumptions.defaults(41, 90, 2026), null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            adapter.retirementWhatIf(21L, "Different", new BigDecimal("216000"), BigDecimal.ZERO));
    verifyNoInteractions(projections);
  }
}
