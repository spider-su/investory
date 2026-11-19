package com.smartbox.investory.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.smartbox.investory.investment.api.asset.InvestmentAssetApi;
import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import com.smartbox.investory.longterm.api.LongTermAssetsApi;
import com.smartbox.investory.longterm.api.model.AssetGroupView;
import com.smartbox.investory.longterm.api.model.AssetSummaryView;
import com.smartbox.investory.longterm.api.model.LongTermOverviewView;
import com.smartbox.investory.profile.api.ProfileSnapshotReader;
import com.smartbox.investory.profile.api.contract.ProfileResponse;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.profile.web.ProfileResponseMapper;
import com.smartbox.investory.retirement.api.RetirementPlanApi;
import com.smartbox.investory.retirement.api.RetirementProjectionApi;
import com.smartbox.investory.retirement.api.model.PlanDetails;
import com.smartbox.investory.retirement.api.model.RetirementProjection;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.retirement.api.model.SimulationDecisionSummary;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class PortfolioResearchMcpExpandedTest {
  private final LongTermAssetsApi longTerm = mock(LongTermAssetsApi.class);
  private final RetirementPlanApi plans = mock(RetirementPlanApi.class);
  private final RetirementProjectionApi projections = mock(RetirementProjectionApi.class);
  private final ProfileSnapshotReader profiles = mock(ProfileSnapshotReader.class);
  private final ProfileResponseMapper mapper = mock(ProfileResponseMapper.class);
  private final PortfolioResearchMcpAdapter adapter =
      new PortfolioResearchMcpAdapter(
          mock(InvestmentDashboardApi.class),
          mock(InvestmentAssetApi.class),
          profiles,
          mapper,
          plans,
          longTerm,
          projections,
          mock(ObjectProvider.class),
          mock(ObjectProvider.class));

  @Test
  void fullProfilePreservesExactFinancialValuesWithoutModelArithmetic() {
    var profile = mock(InvestmentProfile.class);
    var response = mock(ProfileResponse.class);
    when(profiles.loadProfile(21L)).thenReturn(profile);
    when(mapper.map(profile)).thenReturn(response);
    when(response.currency()).thenReturn(CurrencyType.PLN);
    when(response.totalNetWorth()).thenReturn(new BigDecimal("3456789.123456"));
    when(response.longTermAssetValue()).thenReturn(new BigDecimal("3000000.00"));
    when(response.liquidAssets()).thenReturn(new BigDecimal("123456.78"));
    var result = adapter.generalProfile(21L);
    assertEquals("PLN", result.currency());
    assertEquals("3456789.123456", result.netWorth());
    assertEquals("3000000.00", result.longTermValue());
    assertEquals("123456.78", result.liquidAssets());
    assertEquals("live:profile", result.source());
    verify(profiles).loadProfile(21L);
    verifyNoInteractions(longTerm, plans, projections);
  }

  @Test
  void longTermListingUsesSelectedPortfolioAndRespectsLimit() {
    var overview = mock(LongTermOverviewView.class);
    var group = mock(AssetGroupView.class);
    var asset = mock(AssetSummaryView.class);
    when(longTerm.overview(eq(21L), any())).thenReturn(overview);
    when(overview.currency()).thenReturn(CurrencyType.PLN);
    when(overview.totalValue()).thenReturn(new BigDecimal("3000000.4321"));
    when(overview.groups()).thenReturn(List.of(group));
    when(group.assets()).thenReturn(List.of(asset, asset, asset));
    when(asset.name()).thenReturn("Apartment");
    when(asset.currentValue()).thenReturn(new BigDecimal("1000000.144"));
    when(asset.currency()).thenReturn(CurrencyType.PLN);

    var result = adapter.longTermAssets(21L, 2);
    assertEquals("3000000.4321", result.totalValue());
    assertEquals(2, result.assets().size());
    assertEquals("1000000.144", result.assets().getFirst().value());
    assertEquals("live:longterm", result.source());
    assertThrows(IllegalArgumentException.class, () -> adapter.longTermAssets(21L, 21));
    verify(longTerm).overview(eq(21L), any());
    verifyNoInteractions(projections);
  }

  @Test
  void retirementSimulationReturnsOnlyCanonicalSavedPlanScenarios() {
    var details = mock(PlanDetails.class);
    var projection = mock(RetirementProjection.class);
    var assumptions = mock(SimulationAssumptions.class);
    var summary = mock(SimulationDecisionSummary.class);
    var profile = mock(InvestmentProfile.class);
    var response = mock(ProfileResponse.class);
    when(plans.resolvePlanId(21L, null)).thenReturn(Optional.of(9L));
    when(plans.details(21L, 9L)).thenReturn(details);
    when(details.name()).thenReturn("Family plan");
    when(projections.load(21L, 9L)).thenReturn(projection);
    when(projection.assumptions()).thenReturn(assumptions);
    when(assumptions.currentAge()).thenReturn(41);
    when(assumptions.retirementAge()).thenReturn(45);
    when(assumptions.endAge()).thenReturn(85);
    when(assumptions.annualLivingExpenses()).thenReturn(new BigDecimal("180000.12"));
    when(projection.summaries()).thenReturn(Map.of(SimulationScenario.BASE, summary));
    when(summary.finalNetWorth()).thenReturn(new BigDecimal("987654.321"));
    when(profiles.loadProfile(21L)).thenReturn(profile);
    when(mapper.map(profile)).thenReturn(response);
    when(response.currency()).thenReturn(CurrencyType.PLN);

    var result = adapter.retirementSimulation(21L);
    assertTrue(result.available());
    assertEquals("Family plan", result.plan());
    assertEquals(45, result.retirementAge());
    assertEquals("180000.12", result.annualLivingExpenses());
    assertEquals(1, result.scenarios().size());
    assertEquals("BASE", result.scenarios().getFirst().scenario());
    assertEquals("987654.321", result.scenarios().getFirst().finalNetWorth());
    assertEquals("live:retirement-simulation", result.source());
    verify(projections).load(21L, 9L);
  }

  @Test
  void missingSavedPlanDoesNotRunSimulationOrReadOtherPortfolio() {
    when(plans.resolvePlanId(21L, null)).thenReturn(Optional.empty());
    var result = adapter.retirementSimulation(21L);
    assertFalse(result.available());
    assertTrue(result.scenarios().isEmpty());
    verifyNoInteractions(projections, profiles);
  }
}
