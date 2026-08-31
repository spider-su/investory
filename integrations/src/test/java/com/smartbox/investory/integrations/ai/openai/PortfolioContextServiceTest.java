package com.smartbox.investory.integrations.ai.openai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbox.investory.integrations.portfolio.PortfolioContextService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Portfolio Context Service")
class PortfolioContextServiceTest {

  @DisplayName("detects Portfolio Questions")
  @Test
  void detectsPortfolioQuestions() {
    assertTrue(PortfolioContextService.isPortfolioQuestion("What is my portfolio balance?"));
    assertTrue(PortfolioContextService.isPortfolioQuestion("Show unrealized P/L"));
    assertTrue(PortfolioContextService.isPortfolioQuestion("How much cash do I have?"));
    assertFalse(PortfolioContextService.isPortfolioQuestion("What is an ETF?"));
  }
}
