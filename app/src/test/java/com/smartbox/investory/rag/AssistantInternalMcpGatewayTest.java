package com.smartbox.investory.rag;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.mcp.PortfolioResearchClient;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssistantInternalMcpGatewayTest {
  private final PortfolioResearchClient client = mock(PortfolioResearchClient.class);
  private final AssistantInternalMcpGateway gateway = new AssistantInternalMcpGateway(client);

  @Test
  void netWorthReadsExactMcpProfileWithoutConflatingInvestmentValue() {
    when(client.profile(21L))
        .thenReturn(
            new PortfolioResearchClient.ProfileSummary(
                "PLN", "1234.567", "55", "890", "34", "1200", Instant.EPOCH, "live:profile"));
    var answer = gateway.netWorth(21L);
    assertTrue(answer.answer().contains("1234.567 PLN"));
    assertFalse(answer.answer().contains("55 PLN"));
    assertEquals(List.of("live:profile"), answer.sources());
    verify(client).profile(21L);
    verifyNoMoreInteractions(client);
  }

  @Test
  void holdingsAreCappedAtFiveThroughSharedMcpPort() {
    when(client.holdings(21L, 5))
        .thenReturn(
            new PortfolioResearchClient.HoldingsSummary(
                "USD",
                List.of(new PortfolioResearchClient.Holding("SPY", "12.34", "48.5")),
                Instant.EPOCH,
                "live:investment-structure",
                "current positions"));
    var answer = gateway.holdings(21L);
    assertTrue(answer.answer().contains("SPY: 12.34 USD (48.5%)"));
    assertEquals(List.of("live:investment-structure"), answer.sources());
    verify(client).holdings(21L, 5);
  }

  @Test
  void profitUsesExactPeriodAndAbstainsWhenUnavailable() {
    when(client.performance(21L, DashboardPeriod.MAX))
        .thenReturn(
            new PortfolioResearchClient.PeriodResult(
                "MAX", "PLN", "-10.125", true, Instant.EPOCH, "live:investment-performance"));
    var answer = gateway.profitability(21L, DashboardPeriod.MAX);
    assertTrue(answer.answer().contains("-10.125 PLN"));
    assertTrue(answer.answer().contains("at a loss"));
    assertEquals(List.of("live:investment-performance"), answer.sources());
    when(client.performance(21L, DashboardPeriod.YEAR_TO_DATE))
        .thenReturn(
            new PortfolioResearchClient.PeriodResult(
                "YTD", "PLN", null, false, Instant.EPOCH, "live:investment-performance"));
    assertTrue(gateway.profitability(21L, DashboardPeriod.YEAR_TO_DATE).sources().isEmpty());
  }

  @Test
  void benchmarkIsOnlyTrustedWhenSameMcpContractReportsSupportedSpy() {
    when(client.benchmark(21L, DashboardPeriod.MAX))
        .thenReturn(
            new PortfolioResearchClient.BenchmarkResult(
                "MAX",
                "USD",
                "SPY",
                true,
                "12.34",
                "15.67",
                "4.5",
                "6.7",
                Instant.EPOCH,
                "live:investment-benchmark",
                "flow-adjusted"));
    var answer = gateway.benchmark(21L, DashboardPeriod.MAX);
    assertTrue(answer.answer().contains("12.34 USD"));
    assertTrue(answer.answer().contains("SPY benchmark"));
    assertEquals(List.of("live:investment-benchmark"), answer.sources());

    when(client.benchmark(21L, DashboardPeriod.YEAR_TO_DATE))
        .thenReturn(
            new PortfolioResearchClient.BenchmarkResult(
                "YTD",
                "USD",
                "QQQ",
                true,
                "12",
                "15",
                "4",
                "6",
                Instant.EPOCH,
                "live:investment-benchmark",
                "flow-adjusted"));
    assertTrue(gateway.benchmark(21L, DashboardPeriod.YEAR_TO_DATE).sources().isEmpty());
  }

  @Test
  void retirementUsesSavedMcpAssumptionsAndNoFabricatedForecast() {
    when(client.retirement(21L))
        .thenReturn(
            new PortfolioResearchClient.RetirementSummary(
                true,
                "Base",
                41,
                43,
                85,
                List.of("Base", "Alternative"),
                Instant.EPOCH,
                "live:retirement-plan"));
    var answer = gateway.retirement(21L);
    assertTrue(answer.answer().contains("planned retirement age: 43"));
    assertFalse(answer.answer().contains("sustainable"));
    assertEquals(List.of("live:retirement-plan"), answer.sources());
  }
}
