package com.smartbox.investory.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import com.smartbox.investory.investment.api.reporting.model.OverviewView;
import com.smartbox.investory.profile.api.ProfileSnapshotReader;
import com.smartbox.investory.profile.web.ProfileResponseMapper;
import com.smartbox.investory.rag.InvestoryRagClient;
import com.smartbox.investory.retirement.api.RetirementPlanApi;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class PortfolioResearchMcpAdapterTest {
  private final InvestmentDashboardApi investment = mock(InvestmentDashboardApi.class);
  private final ProfileSnapshotReader profiles = mock(ProfileSnapshotReader.class);
  private final ProfileResponseMapper mapper = mock(ProfileResponseMapper.class);
  private final RetirementPlanApi plans = mock(RetirementPlanApi.class);
  private final ObjectProvider<InvestoryRagClient> provider = mock(ObjectProvider.class);
  private final PortfolioResearchMcpAdapter adapter =
      new PortfolioResearchMcpAdapter(
          investment,
          mock(com.smartbox.investory.investment.api.asset.InvestmentAssetApi.class),
          profiles,
          mapper,
          plans,
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
}
