package com.smartbox.investory.rag;

import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;
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
  private final Pattern INTENT;
  private final Pattern HISTORY;
  private final Pattern OTHER_PERIOD;
  private final Pattern YES_NO;
  private final Pattern MADE_MONEY;

  private final InvestmentDashboardApi investment;

  public PortfolioProfitabilityAnswer(
      InvestmentDashboardApi investment, AssistantRoutingPatterns patterns) {
    this.investment = investment;
    INTENT = patterns.pattern("profit-intent");
    HISTORY = patterns.pattern("profit-history");
    OTHER_PERIOD = patterns.pattern("profit-other-period");
    YES_NO = patterns.pattern("profit-yes-no");
    MADE_MONEY = patterns.pattern("profit-made-money");
  }

  /**
   * A yes/no profitability question is implicitly about the currently authorized portfolio. More
   * general profit questions must explicitly specify all time.
   */
  public boolean supports(String question) {
    if (question == null
        || !INTENT.matcher(question).find()
        || OTHER_PERIOD.matcher(question).find()) return false;
    return HISTORY.matcher(question).find()
        || YES_NO.matcher(question).find()
        || MADE_MONEY.matcher(question).find();
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
