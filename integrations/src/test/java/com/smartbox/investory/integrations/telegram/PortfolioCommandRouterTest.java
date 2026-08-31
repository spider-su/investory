package com.smartbox.investory.integrations.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Portfolio Command Router")
class PortfolioCommandRouterTest {

  @DisplayName("parses Commands And Bot Mention")
  @Test
  void parsesCommandsAndBotMention() {
    assertEquals(
        PortfolioCommand.BALANCE,
        PortfolioCommandRouter.parse("/balance@Investory_Bot", "investory_bot"));
    assertEquals(
        PortfolioCommand.POSITIONS, PortfolioCommandRouter.parse(" /positions ", "investory_bot"));
    assertEquals(PortfolioCommand.HELP, PortfolioCommandRouter.parse("commands", "investory_bot"));
  }

  @DisplayName("parses Safe Natural Language Aliases")
  @Test
  void parsesSafeNaturalLanguageAliases() {
    assertEquals(
        PortfolioCommand.CASH,
        PortfolioCommandRouter.parse("How much cash do I have?", "investory_bot"));
    assertEquals(
        PortfolioCommand.PERFORMANCE,
        PortfolioCommandRouter.parse("What is my ROI?", "investory_bot"));
  }

  @DisplayName("leaves Open Ended Questions For Ai")
  @Test
  void leavesOpenEndedQuestionsForAi() {
    assertNull(
        PortfolioCommandRouter.parse(
            "Why did my portfolio underperform this month?", "investory_bot"));
    assertNull(
        PortfolioCommandRouter.parse("Should I reduce my technology exposure?", "investory_bot"));
  }
}
