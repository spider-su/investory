package com.smartbox.investory.rag;

import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic read-only answers for the selected portfolio's lifetime result.
 *
 * Do not infer profitability from market value, net worth or model-generated facts.
 * MAX is the Investment dashboard's uncapped (all-history) reporting period.
 */
@Component
@ConditionalOnProperty(prefix = "investory.rag", name = "enabled", havingValue = "true")
public final class PortfolioProfitabilityAnswer {
  private static final Pattern INTENT =
      Pattern.compile(
          "(?:\\bprofitab(?:le|ility)\\b|\\bprofitable\\b|\\b(?:profit|loss|gain|p\\s*/\\s*l)\\b|"
              + "\\b(?:up|down) overall\\b|\\bmade money\\b|\\bearned money\\b)",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern HISTORY =
      Pattern.compile(
          "\\b(?:all[ -]?time|whole (?:time|history)|ever|overall|since (?:inception|start|i (?:started|have|had))|"
              + "entire (?:time|period)|from (?:the )?beginning|lifetime|historically)\\b",
          Pattern.CASE_INSENSITIVE);

  private final InvestmentDashboardApi investment;

  public PortfolioProfitabilityAnswer(InvestmentDashboardApi investment) {
    this.investment = investment;
  }

  /**
   * A yes/no profitability question is implicitly about the currently authorized
   * portfolio. More general profit questions must explicitly specify all time.
   */
  public boolean supports(String question) {
    if (question == null || !INTENT.matcher(question).find()) return false;
    String normalized = question.toLowerCase(Locale.ROOT);
    return HISTORY.matcher(question).find()
        || normalized.matches(
            "(?s).*\\b(?:is|was|am|are)\\s+(?:it|my portfolio|the portfolio)\\s+profitable\\b.*")
        || normalized.matches("(?s).*\\b(?:have i|did i)\\s+made?\\s+money\\b.*");
  }

  public InvestoryRagClient.RagAnswer answer(long portfolioId) {
    var dashboard =
        investment.loadDashboard(
            new InvestmentDashboardApi.DashboardQuery(
                List.of(), false, DashboardPeriod.MAX, portfolioId));
    if (dashboard == null
        || dashboard.overview() == null
        || dashboard.overview().totalProfit() == null
        || dashboard.overview().baseCurrency() == null) {
      return new InvestoryRagClient.RagAnswer(
          "The all-time portfolio profit or loss is unavailable from current reporting data.",
          List.of());
    }
    BigDecimal profit = dashboard.overview().totalProfit();
    String state =
        profit.signum() > 0 ? "profitable" : profit.signum() < 0 ? "at a loss" : "break-even";
    String sign = profit.signum() > 0 ? "+" : "";
    String answer =
        "For the selected portfolio, the all-time investment result (MAX reporting period) is "
            + sign
            + profit.toPlainString()
            + " "
            + dashboard.overview().baseCurrency().name()
            + ". The portfolio is "
            + state
            + " over that period. This is investment profit/loss, not the current portfolio value. "
            + "[live:investment-performance]";
    return new InvestoryRagClient.RagAnswer(answer, List.of("live:investment-performance"));
  }
}
