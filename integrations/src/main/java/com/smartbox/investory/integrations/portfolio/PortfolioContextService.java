package com.smartbox.investory.integrations.portfolio;

import com.smartbox.investory.investment.api.portfolio.BrokeragePortfolioReader;
import com.smartbox.investory.investment.api.portfolio.SharedBrokeragePortfolioSnapshot;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Supplies the AI assistant with typed current portfolio data from Investment's public API. */
@Slf4j
@Service
public class PortfolioContextService {

  private static final List<String> PORTFOLIO_TERMS =
      List.of(
          "portfolio",
          "balance",
          "cash",
          "account",
          "position",
          "positions",
          "holding",
          "holdings",
          "profit",
          "loss",
          "p/l",
          "roi",
          "return",
          "performance",
          "dividend",
          "allocation",
          "exposure",
          "currency",
          "broker",
          "worth",
          "invested",
          "realized",
          "unrealized",
          "analysis",
          "analyze",
          "review",
          "monthly",
          "quarterly",
          "annual",
          "yearly",
          "risk",
          "concentration",
          "diversification",
          "rebalance",
          "rebalancing",
          "strategy",
          "withdrawal",
          "drawdown",
          "benchmark",
          "stress");

  private final BrokeragePortfolioReader portfolios;
  private final boolean enabled;
  private final int maxCharacters;

  public PortfolioContextService(
      BrokeragePortfolioReader portfolios,
      @Value("${app.openai.portfolio-context.enabled:true}") boolean enabled,
      @Value("${app.openai.portfolio-context.max-characters:16000}") int maxCharacters) {
    this.portfolios = portfolios;
    this.enabled = enabled;
    this.maxCharacters = Math.max(1000, maxCharacters);
  }

  public String loadIfRelevant(String userMessage) {
    if (!isPortfolioQuestion(userMessage)) {
      return "";
    }
    return loadCurrentContext();
  }

  public String loadCurrentContext() {
    if (!enabled) {
      return "";
    }

    try {
      String context = format(portfolios.currentSharedSnapshot());
      return context.length() <= maxCharacters ? context : context.substring(0, maxCharacters);
    } catch (RuntimeException e) {
      // A failed context lookup must not prevent general AI replies.
      log.warn("Could not load typed Investment context", e);
      return "";
    }
  }

  static String format(SharedBrokeragePortfolioSnapshot snapshot) {
    StringBuilder value = new StringBuilder("Current brokerage portfolio\n");
    value.append("Base currency: ").append(snapshot.baseCurrency()).append('\n');
    value.append("Balance: ").append(snapshot.balance()).append('\n');
    value.append("Cash: ").append(snapshot.cash()).append('\n');
    value.append("Dividends: ").append(snapshot.dividends()).append('\n');
    value.append("Interest: ").append(snapshot.interest()).append('\n');
    value.append("Positions:\n");
    snapshot
        .openPositions()
        .forEach(
            p -> value.append("- ").append(p.symbol()).append(": ").append(p.value()).append('\n'));
    return value.toString().trim();
  }

  public static boolean isPortfolioQuestion(String message) {
    if (message == null || message.isBlank()) {
      return false;
    }
    String normalized = message.toLowerCase(Locale.ROOT);
    return PORTFOLIO_TERMS.stream().anyMatch(normalized::contains);
  }
}
