package com.smartbox.investory.integrations.ai.openai;

import java.time.Clock;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Builds decision-oriented portfolio reviews. Investory remains the source of all portfolio values;
 * OpenAI explains changes, risks and possible decisions from the supplied context.
 */
@Service
public class PortfolioAnalysisService {

  private static final String CONVERSATION_PREFIX = "scheduled-analysis:";

  private final OpenAiChatService openAiChatService;
  private final Clock clock;

  @Autowired
  public PortfolioAnalysisService(OpenAiChatService openAiChatService) {
    this(openAiChatService, Clock.systemDefaultZone());
  }

  public PortfolioAnalysisService(OpenAiChatService openAiChatService, Clock clock) {
    this.openAiChatService = openAiChatService;
    this.clock = clock;
  }

  public String monthlyReport() {
    return analyze("monthly", monthlyPrompt(LocalDate.now(clock)));
  }

  public String quarterlyReport() {
    return analyze("quarterly", quarterlyPrompt(LocalDate.now(clock)));
  }

  public String annualReport() {
    return analyze("annual", annualPrompt(LocalDate.now(clock)));
  }

  private String analyze(String reportType, String prompt) {
    String conversationId = CONVERSATION_PREFIX + reportType;
    openAiChatService.resetConversation(conversationId);
    try {
      return openAiChatService.reply(conversationId, prompt);
    } finally {
      openAiChatService.resetConversation(conversationId);
    }
  }

  static String monthlyPrompt(LocalDate date) {
    return """
                Produce the Investory monthly portfolio health report for %s.

                Analyze only facts available in the supplied portfolio context. Do not invent
                missing history, benchmark returns, target allocations, taxes or risk metrics.

                Structure the report as:
                1. Executive assessment: what materially changed and whether action is needed.
                2. Performance drivers: profit/loss, realized, unrealized, dividends, cash and FX.
                3. Concentration and allocation observations, including unusually large positions.
                4. Risks or data-quality gaps that prevent a reliable conclusion.
                5. Up to three actions to review. Separate observation from recommendation.

                Be concise. Do not recommend trading merely because prices moved.
                """
        .formatted(date);
  }

  static String quarterlyPrompt(LocalDate date) {
    return """
                Produce the Investory quarterly strategy and risk review for %s.

                Use only the supplied portfolio context. Evaluate whether the portfolio appears
                more concentrated, less liquid or more exposed to correlated themes. Discuss
                currency, sector, country, asset-class and single-position exposure when present.

                Include:
                1. Strategy consistency and notable portfolio drift.
                2. Concentration and hidden duplication observations.
                3. A qualitative stress review for a broad equity decline, a technology decline,
                   and adverse currency movement. Clearly state when numerical stress data is absent.
                4. Withdrawal-readiness observations based on available cash and liquid assets.
                5. Decisions to review next quarter, with evidence and uncertainty.

                Do not issue direct buy/sell instructions and do not fabricate target allocations.
                """
        .formatted(date);
  }

  static String annualPrompt(LocalDate date) {
    return """
                Produce the Investory annual investment-policy review for %s.

                Use only supplied portfolio facts. The investor currently plans no additional
                deposits or withdrawals, expects to begin annual withdrawals in roughly 3-5 years,
                and expects those withdrawals to be approximately 1-2%% of the portfolio per year.

                Cover:
                1. Annual outcome: portfolio value, profit components, dividends and cash.
                2. Whether return quality appears dependent on concentration or additional risk.
                3. Diversification, liquidity and currency observations.
                4. Withdrawal readiness and risk of forced selling during a downturn.
                5. Data or policy settings still needed: benchmark, target weights, limits,
                   minimum cash and rebalancing thresholds.
                6. A prioritized policy-review checklist for the coming year.

                Distinguish measured facts, reasonable inferences and missing information.
                """
        .formatted(date);
  }
}
