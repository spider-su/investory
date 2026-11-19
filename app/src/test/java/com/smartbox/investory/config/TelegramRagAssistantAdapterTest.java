package com.smartbox.investory.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.smartbox.investory.rag.FinancialAiOrchestratorController;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

class TelegramRagAssistantAdapterTest {
  private final FinancialAiOrchestratorController orchestrator =
      mock(FinancialAiOrchestratorController.class);

  @Test
  void refusesUnboundChatUsernameAndInvalidPortfolioWithoutReadingFacts() {
    var assistant = new TelegramRagAssistantAdapter(orchestrator, "123", "investor");
    assertThrows(AccessDeniedException.class, () -> assistant.reply(1L, "456", "net worth"));
    assertThrows(AccessDeniedException.class, () -> assistant.reply(0L, "123", "net worth"));
    assertThrows(
        AccessDeniedException.class,
        () ->
            new TelegramRagAssistantAdapter(orchestrator, "123", "").reply(1L, "123", "net worth"));
    verifyNoInteractions(orchestrator);
  }

  @Test
  void forwardsQuestionAsNamedNonAdminUserAndPreservesFollowUpContext() {
    var assistant = new TelegramRagAssistantAdapter(orchestrator, "123", "investor");
    var context =
        new FinancialAiOrchestratorController.Context(
            12L,
            FinancialAiOrchestratorController.Topic.PORTFOLIO_PROFIT,
            null,
            com.smartbox.investory.investment.api.reporting.DashboardPeriod.YEAR_TO_DATE);
    when(orchestrator.ask(eq(12L), any(), any()))
        .thenReturn(
            new FinancialAiOrchestratorController.Answer(
                "verified",
                List.of("live:investment"),
                List.of("JAVA_PROFITABILITY"),
                null,
                null,
                context));

    assertEquals("verified", assistant.reply(12L, "123", "profit this year"));
    assertEquals("verified", assistant.reply(12L, "123", "compare that with YTD"));

    var question = ArgumentCaptor.forClass(FinancialAiOrchestratorController.Question.class);
    var identity = ArgumentCaptor.forClass(Authentication.class);
    verify(orchestrator, times(2)).ask(eq(12L), question.capture(), identity.capture());
    assertEquals(null, question.getAllValues().get(0).context());
    assertEquals(context, question.getAllValues().get(1).context());
    assertEquals("investor", identity.getValue().getName());
    assertEquals(
        List.of("ROLE_PROFILE_USER"),
        identity.getValue().getAuthorities().stream().map(a -> a.getAuthority()).toList());

    assistant.reset("123");
    assistant.reply(12L, "123", "profit");
    var afterReset = ArgumentCaptor.forClass(FinancialAiOrchestratorController.Question.class);
    verify(orchestrator, times(3)).ask(eq(12L), afterReset.capture(), any());
    assertEquals(null, afterReset.getAllValues().get(2).context());
  }
}
