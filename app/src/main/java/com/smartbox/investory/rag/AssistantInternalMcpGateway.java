package com.smartbox.investory.rag;

import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.mcp.PortfolioResearchClient;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Local execution of Investory's published read-only MCP query contract.
 *
 * <p>Web, Telegram and LLM-classified requests use the same PortfolioResearchClient methods
 * exposed as MCP tools. In-process calls avoid HTTP loopback and never delegate to a model.
 * Scoped authorization is performed by FinancialAiOrchestratorController before calling here.
 */
@Component
@ConditionalOnProperty(prefix = "investory.rag", name = "enabled", havingValue = "true")
public class AssistantInternalMcpGateway {
  private final PortfolioResearchClient research;

  public AssistantInternalMcpGateway(PortfolioResearchClient research) {
    this.research = research;
  }

  /** MCP: get_profile_summary */
  public InvestoryRagClient.RagAnswer netWorth(long portfolioId) {
    var result = research.profile(portfolioId);
    if (result.netWorth() == null || result.currency() == null) return unavailable();
    return new InvestoryRagClient.RagAnswer(
        "Your total net worth (Profile, including long-term assets) is "
            + result.netWorth() + " " + result.currency()
            + ". This is not the Investment dashboard balance. [live:profile]",
        List.of("live:profile"));
  }

  /** MCP: get_portfolio_holdings */
  public InvestoryRagClient.RagAnswer holdings(long portfolioId) {
    var result = research.holdings(portfolioId, 5);
    if (result.currency() == null || result.holdings() == null || result.holdings().isEmpty()) {
      return unavailable();
    }
    String entries = result.holdings().stream()
        .map(h -> h.symbol() + ": " + h.value() + " " + result.currency()
            + " (" + h.weightPercent() + "%)")
        .collect(Collectors.joining("; "));
    return new InvestoryRagClient.RagAnswer(
        "Largest Investment holdings (MAX dashboard scope): " + entries
            + ". These exclude separate long-term assets tracked in Profile. "
            + "[live:investment-structure]",
        List.of("live:investment-structure"));
  }

  /** MCP: get_investment_period_result */
  public InvestoryRagClient.RagAnswer profitability(long portfolioId, DashboardPeriod period) {
    var result = research.performance(portfolioId, period);
    if (!result.available() || result.profit() == null || result.currency() == null) {
      return new InvestoryRagClient.RagAnswer(
          "Portfolio profit or loss for " + period.wireValue()
              + " is unavailable from current reporting data.",
          List.of());
    }
    var profit = new java.math.BigDecimal(result.profit());
    String state = profit.signum() > 0
        ? "profitable" : profit.signum() < 0 ? "at a loss" : "break-even";
    String introductory = period == DashboardPeriod.MAX
        ? "For the selected portfolio, the all-time investment result (MAX reporting period) is "
        : "For the selected portfolio, the investment result ("
            + period.wireValue() + " reporting period) is ";
    return new InvestoryRagClient.RagAnswer(
        introductory + (profit.signum() > 0 ? "+" : "") + result.profit() + " "
            + result.currency() + ". The portfolio is " + state
            + " over that period. This is investment profit/loss, not the current portfolio value. "
            + "[live:investment-performance]",
        List.of("live:investment-performance"));
  }

  /** MCP: get_benchmark_comparison */
  public InvestoryRagClient.RagAnswer benchmark(long portfolioId, DashboardPeriod period) {
    var result = research.benchmark(portfolioId, period);
    if (!result.available()
        || !"SPY".equalsIgnoreCase(result.symbol())
        || result.currency() == null
        || result.portfolioProfit() == null
        || result.benchmarkProfit() == null
        || result.portfolioReturnPercent() == null
        || result.benchmarkReturnPercent() == null) {
      return unavailable();
    }
    return new InvestoryRagClient.RagAnswer(
        "For the Investment dashboard " + period.wireValue()
            + " history window, actual portfolio P/L was "
            + result.portfolioProfit() + " " + result.currency()
            + "; the existing SPY benchmark scenario P/L was "
            + result.benchmarkProfit() + " " + result.currency()
            + ". Portfolio flow-adjusted return: " + result.portfolioReturnPercent()
            + "%; SPY benchmark return: " + result.benchmarkReturnPercent()
            + "%. This uses Investory's existing benchmark methodology, not a hypothetical"
            + " lump-sum purchase on your first investment date. [live:investment-benchmark]",
        List.of("live:investment-benchmark"));
  }

  /** MCP: get_retirement_plan */
  public InvestoryRagClient.RagAnswer retirement(long portfolioId) {
    var result = research.retirement(portfolioId);
    if (result.savedPlans() == null || result.savedPlans().isEmpty()) {
      return new InvestoryRagClient.RagAnswer(
          "No saved retirement plan is available for this portfolio.", List.of());
    }
    String names = result.savedPlans().stream().limit(5).collect(Collectors.joining(", "));
    if (result.selectedPlan() == null) {
      return new InvestoryRagClient.RagAnswer(
          "Saved retirement plans: " + names
              + ". No active plan was resolved. Open Simulation to inspect assumptions. "
              + "[live:retirement-plan]",
          List.of("live:retirement-plan"));
    }
    if (!result.available()) {
      return new InvestoryRagClient.RagAnswer(
          "Saved retirement plans: " + names
              + ". Plan details are unavailable. [live:retirement-plan]",
          List.of("live:retirement-plan"));
    }
    return new InvestoryRagClient.RagAnswer(
        "Selected retirement plan: " + result.selectedPlan()
            + ". Current age: " + result.currentAge()
            + "; planned retirement age: " + result.retirementAge()
            + "; end age: " + result.endAge()
            + ". Other saved plans: " + names
            + ". For annual expenses and sustainability calculations, open Simulation. "
            + "[live:retirement-plan]",
        List.of("live:retirement-plan"));
  }

  private static InvestoryRagClient.RagAnswer unavailable() {
    return new InvestoryRagClient.RagAnswer(
        "The selected portfolio does not have enough verified data for this question.",
        List.of());
  }
}
