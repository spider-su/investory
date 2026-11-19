package com.smartbox.investory.rag;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import com.smartbox.investory.investment.api.reporting.model.BenchmarkView;
import com.smartbox.investory.investment.api.reporting.model.OverviewView;
import com.smartbox.investory.investment.api.reporting.model.PerformanceView;
import com.smartbox.investory.investment.api.reporting.model.PortfolioStructureView;
import com.smartbox.investory.profile.api.contract.ProfileResponse;
import com.smartbox.investory.retirement.api.RetirementPlanApi;
import com.smartbox.investory.retirement.api.model.PlanDetails;
import com.smartbox.investory.retirement.api.model.PlanSummary;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PortfolioAssistantReadModelsTest {
  private final InvestmentDashboardApi investment = mock(InvestmentDashboardApi.class);
  private final RetirementPlanApi retirement = mock(RetirementPlanApi.class);
  private final PortfolioAssistantReadModels assistant =
      new PortfolioAssistantReadModels(investment, retirement, RoutingPatternsTestSupport.load());

  @Test
  void recognizesRealUserPhrasesWithoutConfusingDefinitionsOrCurrentValue() {
    assertEquals(
        PortfolioAssistantReadModels.Intent.NET_WORTH, assistant.classify("what is my worth"));
    assertEquals(
        PortfolioAssistantReadModels.Intent.MAIN_ASSETS,
        assistant.classify("what are main assets?"));
    assertEquals(
        PortfolioAssistantReadModels.Intent.SPY_COMPARISON,
        assistant.classify("what is the difference if invest in SPY from the beginning"));
    assertEquals(
        PortfolioAssistantReadModels.Intent.RETIREMENT_PLAN,
        assistant.classify("what is my retirement plan"));
    assertEquals(
        PortfolioAssistantReadModels.Intent.NONE,
        assistant.classify("what is my portfolio value?"));
    assertEquals(
        PortfolioAssistantReadModels.Intent.NONE,
        assistant.classify("how does Investory calculate net worth?"));
  }

  @Test
  void doesNotConfuseInvestmentWorthWithWholeWealth() {
    assertEquals(
        PortfolioAssistantReadModels.Intent.NONE,
        assistant.classify("What is my portfolio worth?"));
    assertEquals(
        PortfolioAssistantReadModels.Intent.NONE, assistant.classify("What are my shares worth?"));
    assertEquals(
        PortfolioAssistantReadModels.Intent.NONE, assistant.classify("How does net worth work?"));
    assertEquals(
        PortfolioAssistantReadModels.Intent.NET_WORTH, assistant.classify("How much am I worth?"));
    verifyNoInteractions(investment, retirement);
  }

  @Test
  void doesNotSubstituteCurrentOrMaxDataForOtherScopes() {
    for (String question :
        List.of(
            "What will my net worth be next Friday?",
            "What was my net worth last year?",
            "What is my worth in 10 years?",
            "What is the difference if I invest in S&P 500 this year?",
            "Compare my investments versus SPY this year",
            "Compare my holdings versus SPY in 2025",
            "What were my largest assets last month?",
            "Should I buy SPY instead of my investments?")) {
      assertEquals(
          PortfolioAssistantReadModels.Intent.UNSUPPORTED, assistant.classify(question), question);
    }
    verifyNoInteractions(investment, retirement);
  }

  @Test
  void worthComesFromProfileAndSeparatesDashboardValue() {
    var profile = mock(ProfileResponse.class);
    when(profile.totalNetWorth()).thenReturn(new BigDecimal("12345.6789"));
    when(profile.currency()).thenReturn(CurrencyType.PLN);
    var answer = assistant.netWorth(profile);
    assertTrue(answer.answer().contains("12345.6789 PLN"));
    assertTrue(answer.answer().contains("not the Investment dashboard balance"));
    assertEquals(List.of("live:profile"), answer.sources());
    verifyNoInteractions(investment, retirement);
  }

  @Test
  void holdingsAreSelectedFromMaxPortfolioStructure() {
    var page = mock(InvestmentDashboardApi.DashboardPageView.class);
    var overview = mock(OverviewView.class);
    var structure = mock(PortfolioStructureView.class);
    when(investment.loadDashboard(any())).thenReturn(page);
    when(page.overview()).thenReturn(overview);
    when(overview.baseCurrency()).thenReturn(CurrencyType.USD);
    when(overview.portfolioStructure()).thenReturn(structure);
    when(structure.topHoldings())
        .thenReturn(List.of(new PortfolioStructureView.Holding("SPY", 1000, 30, -3)));
    var answer = assistant.mainAssets(5);
    assertTrue(answer.answer().contains("SPY: 1000.0 USD (30.0%)"));
    assertEquals(List.of("live:investment-structure"), answer.sources());
    var query = ArgumentCaptor.forClass(InvestmentDashboardApi.DashboardQuery.class);
    verify(investment).loadDashboard(query.capture());
    assertEquals(5L, query.getValue().portfolioId());
    assertEquals(DashboardPeriod.MAX, query.getValue().period());
  }

  @Test
  void spyComparisonUsesExistingBenchmarkAndDoesNotInventAnInvestmentScenario() {
    var page = mock(InvestmentDashboardApi.DashboardPageView.class);
    var performance = mock(PerformanceView.class);
    var benchmark = mock(BenchmarkView.class);
    var overview = mock(OverviewView.class);
    when(investment.loadDashboard(any())).thenReturn(page);
    when(page.performance()).thenReturn(performance);
    when(performance.benchmark()).thenReturn(benchmark);
    when(page.overview()).thenReturn(overview);
    when(overview.baseCurrency()).thenReturn(CurrencyType.USD);
    when(benchmark.portfolioPerformanceAvailable()).thenReturn(true);
    when(benchmark.benchmarkAvailable()).thenReturn(true);
    when(benchmark.benchmarkPl()).thenReturn(2500.0);
    when(benchmark.symbol()).thenReturn("SPY");
    when(benchmark.portfolioPl()).thenReturn(1400.0);
    when(benchmark.portfolioReturnPct()).thenReturn(11.1);
    when(benchmark.benchmarkReturnPct()).thenReturn(10.6);
    var answer = assistant.spyComparison(5);
    assertTrue(answer.answer().contains("1400.0 USD"));
    assertTrue(answer.answer().contains("2500.0 USD"));
    assertTrue(answer.answer().contains("not a hypothetical lump-sum purchase"));
    assertEquals(List.of("live:investment-benchmark"), answer.sources());
  }

  @Test
  void spyComparisonAbstainsWithoutAvailableBenchmark() {
    var page = mock(InvestmentDashboardApi.DashboardPageView.class);
    var performance = mock(PerformanceView.class);
    var benchmark = mock(BenchmarkView.class);
    when(investment.loadDashboard(any())).thenReturn(page);
    when(page.performance()).thenReturn(performance);
    when(performance.benchmark()).thenReturn(benchmark);
    var answer = assistant.spyComparison(5);
    assertTrue(answer.answer().contains("not have enough verified data"));
    assertTrue(answer.sources().isEmpty());
  }

  @Test
  void retirementPlanUsesSelectedPlanInSamePortfolio() {
    var plan = mock(PlanSummary.class);
    var details = mock(PlanDetails.class);
    var assumptions = mock(SimulationAssumptions.class);
    when(retirement.resolvePlanId(5L, null)).thenReturn(Optional.of(17L));
    when(retirement.listPlans(5L)).thenReturn(List.of(plan));
    when(plan.name()).thenReturn("Base plan");
    when(retirement.details(5L, 17L)).thenReturn(details);
    when(details.name()).thenReturn("Base plan");
    when(details.assumptions()).thenReturn(assumptions);
    when(assumptions.currentAge()).thenReturn(41);
    when(assumptions.retirementAge()).thenReturn(43);
    when(assumptions.endAge()).thenReturn(85);
    var answer = assistant.retirementPlan(5L);
    assertTrue(answer.answer().contains("Base plan"));
    assertTrue(answer.answer().contains("planned retirement age: 43"));
    assertEquals(List.of("live:retirement-plan"), answer.sources());
    verifyNoInteractions(investment);
  }
}
