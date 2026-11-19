package com.smartbox.investory.rag;

import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic read-only answers for the selected portfolio's lifetime result.
 *
 * <p>Do not infer profitability from market value, net worth or model-generated facts. MAX is the
 * Investment dashboard's uncapped (all-history) reporting period.
 */
@Component
@ConditionalOnProperty(prefix = "investory.rag", name = "enabled", havingValue = "true")
public final class PortfolioProfitabilityAnswer {
  private Pattern INTENT =
      Pattern.compile(
          "(?:\\bprofitab(?:le|ility)\\b|\\bprofitable\\b|\\b(?:profit|loss|gain|p\\s*/\\s*l)\\b|"
              + "\\b(?:up|down) overall\\b|\\bmade money\\b|\\bearned money\\b)",
          Pattern.CASE_INSENSITIVE);
  private Pattern HISTORY =
      Pattern.compile(
          "\\b(?:all[ -]?time|whole (?:time|history)|ever|overall|since (?:inception|start|i (?:started|have|had))|"
              + "entire (?:time|period)|from (?:the )?beginning|lifetime|historically)\\b",
          Pattern.CASE_INSENSITIVE);

  // A narrower requested period must never silently become an all-history result.
  private Pattern OTHER_PERIOD =
      Pattern.compile(
          "\\b(?:ytd|mtd|qtd|today|yesterday|(?:this|last|previous|current|past)\\s+(?:\\d+\\s+)?(?:days?|weeks?|months?|quarters?|years?)|(?:19|20)\\d{2})\\b",
          Pattern.CASE_INSENSITIVE);

  private final InvestmentDashboardApi investment;

  public PortfolioProfitabilityAnswer(InvestmentDashboardApi investment) {
    this.investment = investment;
  }

  @Autowired
  void configureRoutingPatterns(AssistantRoutingPatterns patterns) {
    INTENT = patterns.pattern("profit-intent");
    HISTORY = patterns.pattern("profit-history");
    OTHER_PERIOD = patterns.pattern("profit-other-period");
  }

  /**
   * A yes/no profitability question is implicitly about the currently authorized portfolio. More
   * general profit questions must explicitly specify all time.
   */
  public boolean supports(String question) {
    if (question == null
        || !INTENT.matcher(question).find()
        || OTHER_PERIOD.matcher(question).find()) return false;
    String normalized = question.toLowerCase(Locale.ROOT);
    return HISTORY.matcher(question).find()
        || normalized.matches(
            "(?s).*\\b(?:is|was|am|are)\\s+(?:it|my portfolio|the portfolio)\\s+profitable\\b.*")
        || normalized.matches("(?s).*\\b(?:have i|did i)\\s+made?\\s+money\\b.*");
  }

  public boolean requestsResult(String question) {
    return INTENT.matcher(question).find();
  }

  public InvestoryRagClient.RagAnswer answer(long portfolioId) {
    return answer(portfolioId, DashboardPeriod.MAX);
  }

  public InvestoryRagClient.RagAnswer answer(long portfolioId, DashboardPeriod period) {
    return answer(portfolioId, period, false);
  }

  public InvestoryRagClient.RagAnswer answer(
      long portfolioId, DashboardPeriod period, boolean freshFacts) {
    var dashboard =
        investment.loadDashboard(
            new InvestmentDashboardApi.DashboardQuery(
                List.of(), false, period, portfolioId, freshFacts));
    var overview = dashboard == null ? null : dashboard.overview();
    BigDecimal profit = overview == null ? null : overview.totalProfit();
    var currency = overview == null ? null : overview.baseCurrency();
    if (profit == null || currency == null) {
      return new InvestoryRagClient.RagAnswer(
          "Portfolio profit or loss for "
              + period.wireValue()
              + " is unavailable from current reporting data.",
          List.of());
    }
    String state =
        profit.signum() > 0 ? "profitable" : profit.signum() < 0 ? "at a loss" : "break-even";
    String sign = profit.signum() > 0 ? "+" : "";
    String answer =
        (period == DashboardPeriod.MAX
                ? "For the selected portfolio, the all-time investment result (MAX reporting period) is "
                : "For the selected portfolio, the investment result ("
                    + period.wireValue()
                    + " reporting period) is ")
            + sign
            + profit.toPlainString()
            + " "
            + currency.name()
            + ". The portfolio is "
            + state
            + " over that period. This is investment profit/loss, not the current portfolio value. "
            + "[live:investment-performance]";
    return new InvestoryRagClient.RagAnswer(answer, List.of("live:investment-performance"));
  }
}
