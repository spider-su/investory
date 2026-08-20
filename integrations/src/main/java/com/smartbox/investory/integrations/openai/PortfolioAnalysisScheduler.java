package com.smartbox.investory.integrations.openai;

import com.smartbox.investory.integrations.bot.PortfolioBot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Generates scheduled AI reviews and sends them through the configured Telegram chat. */
@Slf4j
@Component
@ConditionalOnProperty(
    name = {"app.openai.analysis.enabled", "app.telegram.enabled"},
    havingValue = "true")
public class PortfolioAnalysisScheduler {

  private final PortfolioAnalysisService portfolioAnalysisService;
  private final PortfolioBot portfolioBot;

  public PortfolioAnalysisScheduler(
      PortfolioAnalysisService portfolioAnalysisService, PortfolioBot portfolioBot) {
    this.portfolioAnalysisService = portfolioAnalysisService;
    this.portfolioBot = portfolioBot;
  }

  @Scheduled(
      cron = "${app.openai.analysis.monthly-cron:0 0 9 1 * *}",
      zone = "${app.openai.analysis.zone:Europe/Warsaw}")
  public void sendMonthlyReport() {
    send("Monthly portfolio health report", portfolioAnalysisService.monthlyReport());
  }

  @Scheduled(
      cron = "${app.openai.analysis.quarterly-cron:0 15 9 2 1,4,7,10 *}",
      zone = "${app.openai.analysis.zone:Europe/Warsaw}")
  public void sendQuarterlyReport() {
    send("Quarterly strategy and risk review", portfolioAnalysisService.quarterlyReport());
  }

  @Scheduled(
      cron = "${app.openai.analysis.annual-cron:0 30 9 3 1 *}",
      zone = "${app.openai.analysis.zone:Europe/Warsaw}")
  public void sendAnnualReport() {
    send("Annual investment-policy review", portfolioAnalysisService.annualReport());
  }

  private void send(String title, String body) {
    try {
      portfolioBot.sendMessage(title + "\n\n" + body);
    } catch (RuntimeException e) {
      log.warn("Could not send scheduled report: {}", title, e);
    }
  }
}
