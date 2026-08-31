package com.smartbox.investory.integrations.ai.openai;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Portfolio Analysis Service")
class PortfolioAnalysisServiceTest {

  private static final LocalDate REPORT_DATE = LocalDate.of(2026, 7, 23);

  @DisplayName("monthly Prompt Requires Evidence And Separates Recommendations")
  @Test
  void monthlyPromptRequiresEvidenceAndSeparatesRecommendations() {
    String prompt = PortfolioAnalysisService.monthlyPrompt(REPORT_DATE);
    assertTrue(prompt.contains("monthly portfolio health report"));
    assertTrue(prompt.contains("Do not invent"));
    assertTrue(prompt.contains("Separate observation from recommendation"));
  }

  @DisplayName("quarterly Prompt Includes Strategy Risk And Stress Review")
  @Test
  void quarterlyPromptIncludesStrategyRiskAndStressReview() {
    String prompt = PortfolioAnalysisService.quarterlyPrompt(REPORT_DATE);
    assertTrue(prompt.contains("strategy and risk review"));
    assertTrue(prompt.contains("stress review"));
    assertTrue(prompt.contains("Withdrawal-readiness"));
  }

  @DisplayName("annual Prompt Uses Investor Withdrawal Plan")
  @Test
  void annualPromptUsesInvestorWithdrawalPlan() {
    String prompt = PortfolioAnalysisService.annualPrompt(REPORT_DATE);
    assertTrue(prompt.contains("investment-policy review"));
    assertTrue(prompt.contains("3-5 years"));
    assertTrue(prompt.contains("1-2%"));
    assertTrue(prompt.contains("benchmark"));
  }
}
