package com.smartbox.investory.integrations.telegram;

import com.smartbox.investory.integrations.notifications.formatting.TelegramText;
import com.smartbox.investory.integrations.portfolio.PortfolioContextService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class PortfolioCommandService {

  private static final String UNAVAILABLE =
      "Current portfolio data is unavailable. Open the dashboard and verify that it loads correctly.";

  private final PortfolioContextService contextService;

  public PortfolioCommandService(PortfolioContextService contextService) {
    this.contextService = contextService;
  }

  String execute(PortfolioCommand command) {
    if (command == PortfolioCommand.HELP) {
      return help();
    }

    String context = contextService.loadCurrentContext();
    if (context == null || context.isBlank()) {
      return UNAVAILABLE;
    }

    return switch (command) {
      case BALANCE -> balance(context);
      case PERFORMANCE -> performance(context);
      case PNL -> pnl(context);
      case CASH -> singleMetric(context, "Cash", "Cash");
      case POSITIONS ->
          section(
              context, "Current positions", List.of("Realized P/L", "Realized by Currency"), 1800);
      case DIVIDENDS -> singleMetric(context, "Dividends", "Net dividends", "Dividends");
      case ALLOCATION -> allocation(context);
      case RISK -> risk(context);
      case ALERTS -> alerts(context);
      case REPORT ->
          "No stored deterministic report is available yet. Scheduled reports are delivered through Telegram; historical report storage should be added before enabling /report.";
      case HELP -> help();
    };
  }

  private String balance(String context) {
    List<String> lines = new ArrayList<>();
    addMetric(lines, context, "Portfolio value", "Balance");
    addMetric(lines, context, "Cash", "Cash");

    String balance = findValue(context, "Balance");
    String cash = findValue(context, "Cash");
    if (balance != null && cash != null) {
      lines.add(TelegramText.metric("Invested", subtractMoney(balance, cash)));
    }
    return result("Portfolio", lines);
  }

  private String performance(String context) {
    List<String> lines = new ArrayList<>();
    addMetric(lines, context, "ROI", "ROI");
    addMetric(lines, context, "Profit", "Profit");
    addMetric(lines, context, "After tax", "After tax");
    return result("Performance", lines);
  }

  private String pnl(String context) {
    List<String> lines = new ArrayList<>();
    addMetric(lines, context, "Unrealized P/L", "Unrealized P/L");
    addMetric(lines, context, "Realized P/L", "Realized P/L");
    addMetric(lines, context, "Dividends", "Dividends");
    return result("Profit and loss", lines);
  }

  private String allocation(String context) {
    List<String> lines = new ArrayList<>();
    String unrealized =
        sectionValue(context, "Unrealized by Currency", List.of("Realized by Currency"), 900);
    String realized =
        sectionValue(context, "Realized by Currency", List.of("Current positions"), 900);
    if (unrealized != null) {
      lines.add(TelegramText.metric("Unrealized by currency", unrealized));
    }
    if (realized != null) {
      lines.add(TelegramText.metric("Realized by currency", realized));
    }
    if (lines.isEmpty()) {
      lines.add(
          "AssetEntity-class and position-weight allocation is not exposed by the current dashboard context.");
    }
    return result("Allocation", lines);
  }

  private String risk(String context) {
    List<String> lines = new ArrayList<>();
    String positions =
        sectionValue(
            context, "Current positions", List.of("Realized P/L", "Realized by Currency"), 1200);
    if (positions != null) {
      lines.add(TelegramText.metric("Current positions", positions));
    }
    lines.add(
        "Deterministic concentration, drawdown, volatility and allocation-limit metrics are not yet calculated by Investory.");
    return result("Risk", lines);
  }

  private String alerts(String context) {
    List<String> lines = new ArrayList<>();
    String alerts = sectionValue(context, "Alerts", List.of("Current positions", "Balance"), 1200);
    if (alerts != null) {
      lines.add(TelegramText.escape(alerts));
    } else {
      lines.add("No alert section was found in the current dashboard data.");
    }
    return result("Active alerts", lines);
  }

  private String singleMetric(String context, String title, String... labels) {
    List<String> lines = new ArrayList<>();
    for (String label : labels) {
      String value = findValue(context, label);
      if (value != null) {
        lines.add(TelegramText.metric(title, value));
        break;
      }
    }
    return result(title, lines);
  }

  private String section(String context, String start, List<String> ends, int maxLength) {
    String value = sectionValue(context, start, ends, maxLength);
    return value == null
        ? TelegramText.heading("ℹ️", start)
            + "\n\nNo "
            + start.toLowerCase(Locale.ROOT)
            + " data was found."
        : TelegramText.heading("📋", start) + "\n\n" + TelegramText.escape(value);
  }

  private static void addMetric(
      List<String> lines, String context, String outputLabel, String sourceLabel) {
    String value = findValue(context, sourceLabel);
    if (value != null) {
      lines.add(TelegramText.metric(outputLabel, value));
    }
  }

  static String findValue(String context, String label) {
    Pattern pattern =
        Pattern.compile(
            "(?i)(?:^|\\s)"
                + Pattern.quote(label)
                + "\\s*:?\\s*(.{1,80}?)(?=\\s+(?:Balance|Cash|ROI|Profit|After tax|Unrealized P/L|Realized P/L|Dividends|Current positions|Unrealized by Currency|Realized by Currency)\\b|$)");
    Matcher matcher = pattern.matcher(context);
    return matcher.find() ? clean(matcher.group(1), 100) : null;
  }

  static String sectionValue(
      String context, String startLabel, List<String> endLabels, int maxLength) {
    int start = indexOfIgnoreCase(context, startLabel);
    if (start < 0) {
      return null;
    }
    start += startLabel.length();
    int end = context.length();
    for (String endLabel : endLabels) {
      int candidate = indexOfIgnoreCase(context.substring(start), endLabel);
      if (candidate >= 0) {
        end = Math.min(end, start + candidate);
      }
    }
    return clean(context.substring(start, end), maxLength);
  }

  private static int indexOfIgnoreCase(String value, String search) {
    return value.toLowerCase(Locale.ROOT).indexOf(search.toLowerCase(Locale.ROOT));
  }

  private static String clean(String value, int maxLength) {
    String cleaned = value.replaceAll("\\s+", " ").trim();
    if (cleaned.isBlank()) {
      return null;
    }
    return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength) + "...";
  }

  private static String subtractMoney(String balance, String cash) {
    Matcher balanceMatcher = Pattern.compile("[-\\d.,]+\\s*(.*)").matcher(balance);
    Matcher cashMatcher = Pattern.compile("[-\\d.,]+\\s*(.*)").matcher(cash);
    if (!balanceMatcher.matches() || !cashMatcher.matches()) {
      return balance + " minus " + cash;
    }
    try {
      double value =
          Double.parseDouble(balance.replaceAll("[^\\d.-]", ""))
              - Double.parseDouble(cash.replaceAll("[^\\d.-]", ""));
      return String.format(Locale.US, "%,.0f %s", value, balanceMatcher.group(1).trim());
    } catch (NumberFormatException ignored) {
      return balance + " minus " + cash;
    }
  }

  private static String result(String title, List<String> lines) {
    if (lines.isEmpty()) {
      return TelegramText.heading("ℹ️", title) + "\n\nData not found in the current dashboard.";
    }
    return TelegramText.heading("📊", title) + "\n\n" + String.join("\n", lines);
  }

  private String help() {
    return """
                <b>💼 Investory</b>

                Portfolio
                /balance — value and cash
                /performance — ROI and profit
                /pnl — realized, unrealized and dividend results
                /cash — current cash
                /positions — current holdings
                /dividends — net dividends
                /allocation — currency allocation data

                Monitoring
                /risk — available risk information
                /alerts — active alerts
                /report — latest analysis report

                AI
                Ask any portfolio or investing question
                /reset — clear AI conversation context
                """
        .trim();
  }
}
