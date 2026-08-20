package com.smartbox.investory.investment.accounting.command.portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PortfolioCommandRouterTest {

  @Test
  void parsesCommandsAndBotMention() {
    assertEquals(
        PortfolioCommand.BALANCE,
        PortfolioCommandRouter.parse("/balance@Investory_Bot", "investory_bot"));
    assertEquals(
        PortfolioCommand.POSITIONS, PortfolioCommandRouter.parse(" /positions ", "investory_bot"));
    assertEquals(PortfolioCommand.HELP, PortfolioCommandRouter.parse("commands", "investory_bot"));
  }

  @Test
  void parsesSafeNaturalLanguageAliases() {
    assertEquals(
        PortfolioCommand.CASH,
        PortfolioCommandRouter.parse("How much cash do I have?", "investory_bot"));
    assertEquals(
        PortfolioCommand.PERFORMANCE,
        PortfolioCommandRouter.parse("What is my ROI?", "investory_bot"));
  }

  @Test
  void leavesOpenEndedQuestionsForAi() {
    assertNull(
        PortfolioCommandRouter.parse(
            "Why did my portfolio underperform this month?", "investory_bot"));
    assertNull(
        PortfolioCommandRouter.parse("Should I reduce my technology exposure?", "investory_bot"));
  }
}
