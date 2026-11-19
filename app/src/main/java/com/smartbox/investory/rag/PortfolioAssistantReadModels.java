package com.smartbox.investory.rag;

import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import com.smartbox.investory.investment.api.reporting.model.PortfolioStructureView;
import com.smartbox.investory.profile.api.contract.ProfileResponse;
import com.smartbox.investory.retirement.api.RetirementPlanApi;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Read-only, deterministic assistant intents backed by existing Investory domain APIs.
 *
 * <p>No model arithmetic, new persistence, or cross-portfolio global dashboard queries. The caller
 * checks scoped authorization before invoking any method here.
 */
@Component
@ConditionalOnProperty(prefix = "investory.rag", name = "enabled", havingValue = "true")
public class PortfolioAssistantReadModels {
  private static final String UNAVAILABLE =
      "The selected portfolio does not have enough verified data for this question.";
  private final InvestmentDashboardApi dashboard;
  private final RetirementPlanApi retirement;
  private final AssistantRoutingPatterns patterns;

  public PortfolioAssistantReadModels(
      InvestmentDashboardApi dashboard,
      RetirementPlanApi retirement,
      AssistantRoutingPatterns patterns) {
    this.dashboard = dashboard;
    this.retirement = retirement;
    this.patterns = patterns;
  }

  public enum Intent {
    NONE,
    UNSUPPORTED,
    NET_WORTH,
    MAIN_ASSETS,
    SPY_COMPARISON,
    RETIREMENT_PLAN
  }

  private boolean matches(String key, String question) {
    return patterns.pattern(key).matcher(question).find();
  }

  public Intent classify(String question) {
    if (question == null) return Intent.NONE;
    // All matching phrases are configured in application.yml. No embedded regex fallback.
    String q = question.strip();
    if (matches("classify-financial-subject", q)
        && (matches("classify-unsupported-forecast", q)
            || matches("classify-unsupported-relative", q)
            || matches("classify-unsupported-in-days", q)
            || matches("classify-unsupported-current-window", q)
            || matches("classify-unsupported-trade", q))) {
      return Intent.UNSUPPORTED;
    }
    if (matches("classify-benchmark-subject", q) && matches("classify-benchmark-comparison", q)) {
      return Intent.SPY_COMPARISON;
    }
    if (matches("classify-retirement-subject", q) && matches("classify-retirement-context", q)) {
      return Intent.RETIREMENT_PLAN;
    }
    if (matches("classify-largest-qualifier", q) && matches("classify-investment-subject", q)) {
      return Intent.MAIN_ASSETS;
    }
    if ((matches("classify-net-worth-direct", q)
            || matches("classify-net-worth-personal", q)
            || matches("classify-net-worth-question", q))
        && !matches("classify-definition-request", q)) {
      return Intent.NET_WORTH;
    }
    return Intent.NONE;
  }

  public InvestoryRagClient.RagAnswer netWorth(ProfileResponse snapshot) {
    if (snapshot == null || snapshot.totalNetWorth() == null || snapshot.currency() == null) {
      return unavailable();
    }
    return new InvestoryRagClient.RagAnswer(
        "Your total net worth (Profile, including long-term assets) is "
            + snapshot.totalNetWorth().toPlainString()
            + " "
            + snapshot.currency().name()
            + ". This is not the Investment dashboard balance. [live:profile]",
        List.of("live:profile"));
  }

  /** Returns only a public symbol from scoped holdings, never values or account data. */
  public String largestHoldingSymbol(long portfolioId) {
    var view =
        dashboard.loadDashboard(
            new InvestmentDashboardApi.DashboardQuery(
                List.of(), false, DashboardPeriod.MAX, portfolioId));
    var overview = view == null ? null : view.overview();
    var structure = overview == null ? null : overview.portfolioStructure();
    if (structure == null || structure.topHoldings().isEmpty()) return null;
    return structure.topHoldings().get(0).symbol();
  }

  public InvestoryRagClient.RagAnswer mainAssets(long portfolioId) {
    var view =
        dashboard.loadDashboard(
            new InvestmentDashboardApi.DashboardQuery(
                List.of(), false, DashboardPeriod.MAX, portfolioId));
    var overview = view == null ? null : view.overview();
    PortfolioStructureView structure = overview == null ? null : overview.portfolioStructure();
    var currency = overview == null ? null : overview.baseCurrency();
    if (structure == null || currency == null || structure.topHoldings().isEmpty()) {
      return unavailable();
    }
    String holdings =
        structure.topHoldings().stream()
            .limit(5)
            .map(
                h ->
                    h.symbol()
                        + ": "
                        + BigDecimal.valueOf(h.value()).toPlainString()
                        + " "
                        + currency.name()
                        + " ("
                        + BigDecimal.valueOf(h.weightPct()).toPlainString()
                        + "%)")
            .collect(Collectors.joining("; "));
    return new InvestoryRagClient.RagAnswer(
        "Largest Investment holdings (MAX dashboard scope): "
            + holdings
            + ". These exclude separate long-term assets tracked in Profile. "
            + "[live:investment-structure]",
        List.of("live:investment-structure"));
  }

  public InvestoryRagClient.RagAnswer spyComparison(long portfolioId) {
    return spyComparison(portfolioId, DashboardPeriod.MAX);
  }

  public InvestoryRagClient.RagAnswer spyComparison(long portfolioId, DashboardPeriod period) {
    return spyComparison(portfolioId, period, false);
  }

  public InvestoryRagClient.RagAnswer spyComparison(
      long portfolioId, DashboardPeriod period, boolean freshFacts) {
    var view =
        dashboard.loadDashboard(
            new InvestmentDashboardApi.DashboardQuery(
                List.of(), false, period, portfolioId, freshFacts));
    var benchmark =
        view == null || view.performance() == null ? null : view.performance().benchmark();
    var overview = view == null ? null : view.overview();
    if (benchmark == null
        || !benchmark.portfolioPerformanceAvailable()
        || !benchmark.benchmarkAvailable()
        || !Double.isFinite(benchmark.portfolioPl())
        || !Double.isFinite(benchmark.portfolioReturnPct())
        || benchmark.benchmarkPl() == null
        || !Double.isFinite(benchmark.benchmarkPl())
        || benchmark.benchmarkReturnPct() == null
        || !Double.isFinite(benchmark.benchmarkReturnPct())
        || overview == null
        || overview.baseCurrency() == null
        || !"SPY".equalsIgnoreCase(benchmark.symbol())) {
      return unavailable();
    }
    String currency = overview.baseCurrency().name();
    String result =
        "For the Investment dashboard "
            + period.wireValue()
            + " history window, actual portfolio P/L was "
            + BigDecimal.valueOf(benchmark.portfolioPl()).toPlainString()
            + " "
            + currency
            + "; the existing SPY benchmark scenario P/L was "
            + BigDecimal.valueOf(benchmark.benchmarkPl()).toPlainString()
            + " "
            + currency
            + ". Portfolio flow-adjusted return: "
            + BigDecimal.valueOf(benchmark.portfolioReturnPct()).toPlainString()
            + "%; SPY benchmark return: "
            + BigDecimal.valueOf(benchmark.benchmarkReturnPct()).toPlainString()
            + "%. This uses Investory's existing benchmark methodology, not a hypothetical"
            + " lump-sum purchase on your first investment date. [live:investment-benchmark]";
    return new InvestoryRagClient.RagAnswer(result, List.of("live:investment-benchmark"));
  }

  public InvestoryRagClient.RagAnswer retirementPlan(long portfolioId) {
    var selected = retirement.resolvePlanId(portfolioId, null);
    var plans = retirement.listPlans(portfolioId);
    if (plans == null || plans.isEmpty()) {
      return new InvestoryRagClient.RagAnswer(
          "No saved retirement plan is available for this portfolio.", List.of());
    }
    var names = plans.stream().limit(5).map(plan -> plan.name()).collect(Collectors.joining(", "));
    if (selected.isEmpty()) {
      return new InvestoryRagClient.RagAnswer(
          "Saved retirement plans: "
              + names
              + ". No active plan was resolved. Open Simulation to inspect assumptions. "
              + "[live:retirement-plan]",
          List.of("live:retirement-plan"));
    }
    var details = retirement.details(portfolioId, selected.get());
    if (details == null || details.assumptions() == null) {
      return new InvestoryRagClient.RagAnswer(
          "Saved retirement plans: "
              + names
              + ". Plan details are unavailable. [live:retirement-plan]",
          List.of("live:retirement-plan"));
    }
    var assumptions = details.assumptions();
    return new InvestoryRagClient.RagAnswer(
        "Selected retirement plan: "
            + details.name()
            + ". Current age: "
            + assumptions.currentAge()
            + "; planned retirement age: "
            + assumptions.retirementAge()
            + "; end age: "
            + assumptions.endAge()
            + ". Other saved plans: "
            + names
            + ". For annual expenses and sustainability calculations, open Simulation. "
            + "[live:retirement-plan]",
        List.of("live:retirement-plan"));
  }

  private InvestoryRagClient.RagAnswer unavailable() {
    return new InvestoryRagClient.RagAnswer(UNAVAILABLE, List.of());
  }
}
