package com.smartbox.investory.integrations.openai;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Supplies the AI assistant with the same current values shown by the Investory dashboard.
 *
 * <p>The service reads the rendered dashboard instead of reimplementing portfolio calculations,
 * keeping Telegram answers aligned with the application's existing valuation logic.
 */
@Slf4j
@Service
public class PortfolioContextService {

  private static final Pattern SCRIPT = Pattern.compile("(?is)<script\\b[^>]*>.*?</script>");
  private static final Pattern STYLE = Pattern.compile("(?is)<style\\b[^>]*>.*?</style>");
  private static final Pattern TAG = Pattern.compile("(?s)<[^>]+>");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

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

  private final RestClient restClient;
  private final boolean enabled;
  private final String dashboardUrl;
  private final int maxCharacters;

  public PortfolioContextService(
      @Value("${app.openai.portfolio-context.enabled:true}") boolean enabled,
      @Value("${app.openai.portfolio-context.dashboard-url:http://localhost:8080/dashboard}")
          String dashboardUrl,
      @Value("${app.openai.portfolio-context.max-characters:16000}") int maxCharacters) {
    this.restClient = RestClient.builder().build();
    this.enabled = enabled;
    this.dashboardUrl = dashboardUrl;
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
      String html =
          restClient
              .get()
              .uri(dashboardUrl)
              .accept(MediaType.TEXT_HTML)
              .retrieve()
              .body(String.class);

      if (html == null || html.isBlank()) {
        log.debug("Investory dashboard returned no content for AI context");
        return "";
      }

      String context = htmlToText(html);
      return context.length() <= maxCharacters ? context : context.substring(0, maxCharacters);
    } catch (RuntimeException e) {
      // A failed context lookup must not prevent general AI replies.
      log.warn("Could not load Investory dashboard context from {}", dashboardUrl, e);
      return "";
    }
  }

  static boolean isPortfolioQuestion(String message) {
    if (message == null || message.isBlank()) {
      return false;
    }
    String normalized = message.toLowerCase(Locale.ROOT);
    return PORTFOLIO_TERMS.stream().anyMatch(normalized::contains);
  }

  static String htmlToText(String html) {
    if (html == null || html.isBlank()) {
      return "";
    }

    String text = SCRIPT.matcher(html).replaceAll(" ");
    text = STYLE.matcher(text).replaceAll(" ");
    text = text.replaceAll("(?i)<br\\s*/?>", "\n");
    text = text.replaceAll("(?i)</(p|div|section|article|header|footer|tr|li|h[1-6])>", "\n");
    text = TAG.matcher(text).replaceAll(" ");
    text = decodeBasicEntities(text);
    return WHITESPACE.matcher(text).replaceAll(" ").trim();
  }

  private static String decodeBasicEntities(String value) {
    return value
        .replace("&nbsp;", " ")
        .replace("&#160;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'");
  }
}
