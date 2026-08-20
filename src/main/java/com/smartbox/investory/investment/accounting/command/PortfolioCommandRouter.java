package com.smartbox.investory.investment.accounting.command;

import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class PortfolioCommandRouter {

  private final PortfolioCommandService commandService;

  public PortfolioCommandRouter(PortfolioCommandService commandService) {
    this.commandService = commandService;
  }

  public Optional<String> route(String message, String botUsername) {
    PortfolioCommand command = parse(message, botUsername);
    return command == null ? Optional.empty() : Optional.of(commandService.execute(command));
  }

  public String help() {
    return commandService.execute(PortfolioCommand.HELP);
  }

  static PortfolioCommand parse(String message, String botUsername) {
    if (message == null || message.isBlank()) {
      return null;
    }

    String normalized = normalizeCommand(message, botUsername);
    return switch (normalized) {
      case "/balance", "what is my balance", "show my balance", "portfolio balance" ->
          PortfolioCommand.BALANCE;
      case "/performance", "what is my roi", "show my roi", "show performance" ->
          PortfolioCommand.PERFORMANCE;
      case "/pnl", "/profit", "show profit", "show pnl", "show p/l" -> PortfolioCommand.PNL;
      case "/cash", "how much cash do i have", "show cash" -> PortfolioCommand.CASH;
      case "/positions", "show my positions", "show positions", "top positions" ->
          PortfolioCommand.POSITIONS;
      case "/dividends", "show dividends", "how much dividends" -> PortfolioCommand.DIVIDENDS;
      case "/allocation", "show allocation", "portfolio allocation" -> PortfolioCommand.ALLOCATION;
      case "/risk", "show risk", "portfolio risk" -> PortfolioCommand.RISK;
      case "/alerts", "show alerts", "active alerts" -> PortfolioCommand.ALERTS;
      case "/report", "/report monthly", "/report quarterly", "/report annual" ->
          PortfolioCommand.REPORT;
      case "/help", "help", "commands" -> PortfolioCommand.HELP;
      default -> null;
    };
  }

  private static String normalizeCommand(String message, String botUsername) {
    String normalized =
        message.trim().toLowerCase(Locale.ROOT).replaceAll("[?!.,]+$", "").replaceAll("\\s+", " ");

    if (normalized.startsWith("/") && botUsername != null && !botUsername.isBlank()) {
      normalized = normalized.replace("@" + botUsername.trim().toLowerCase(Locale.ROOT), "");
    }
    return normalized;
  }
}
