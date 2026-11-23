package com.smartbox.investory.rag;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.smartbox.investory.config.AuthorizationService;
import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.mcp.PortfolioResearchClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

class AssistantConversationControllerTest {
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final AssistantConversationService conversations =
      mock(AssistantConversationService.class);
  private final FinancialAiOrchestratorController assistant =
      mock(FinancialAiOrchestratorController.class);
  private final PortfolioResearchClient research = mock(PortfolioResearchClient.class);
  private final Authentication alice = new UsernamePasswordAuthenticationToken("alice", "n/a");
  private AssistantConversationController controller;
  private AssistantConversationService.Conversation current;
  private final UUID id = UUID.randomUUID();

  @BeforeEach
  void setup() {
    controller =
        new AssistantConversationController(authorization, conversations, assistant, research);
    current =
        new AssistantConversationService.Conversation(
            id,
            21,
            "alice",
            "PORTFOLIO_PROFIT",
            null,
            "MAX",
            null,
            0,
            Instant.now().plusSeconds(3600));
    when(authorization.canReadScoped(21, alice)).thenReturn(true);
    when(conversations.get(id, 21, "alice")).thenReturn(current);
  }

  private PortfolioResearchClient.RetirementSimulation simulation(String spending) {
    return new PortfolioResearchClient.RetirementSimulation(
        true,
        "Base",
        "PLN",
        41,
        50,
        90,
        spending,
        "12000",
        "0.025",
        "0.06",
        "0.04",
        List.of(
            new PortfolioResearchClient.SimulationScenarioSummary(
                "BASE", false, null, null, "500000", "100000", "400000", "100000", "300000",
                "300000", "0", "0.8")),
        Instant.now(),
        "live:retirement-simulation",
        "Model estimate");
  }

  @Test
  void reusesVerifiedContextForPeriodFollowUp() {
    var nextContext =
        new FinancialAiOrchestratorController.Context(
            21,
            FinancialAiOrchestratorController.Topic.PORTFOLIO_PROFIT,
            null,
            DashboardPeriod.YEAR_TO_DATE);
    when(assistant.ask(eq(21L), any(), eq(alice)))
        .thenReturn(
            new FinancialAiOrchestratorController.Answer(
                "YTD",
                List.of("live:investment-performance"),
                List.of("JAVA_PROFITABILITY"),
                null,
                null,
                nextContext));
    var updated =
        new AssistantConversationService.Conversation(
            id,
            21,
            "alice",
            "PORTFOLIO_PROFIT",
            null,
            "YTD",
            null,
            1,
            Instant.now().plusSeconds(3600));
    when(conversations.updateContext(
            eq(current), eq("PORTFOLIO_PROFIT"), isNull(), eq("YTD"), isNull()))
        .thenReturn(updated);
    var answer =
        controller.ask(
            21, id, new AssistantConversationController.AskRequest("Compare that with YTD"), alice);
    assertEquals(1, answer.version());
    verify(assistant)
        .ask(
            eq(21L),
            argThat(q -> q.context() != null && q.context().period() == DashboardPeriod.MAX),
            eq(alice));
    verify(conversations)
        .updateContext(eq(current), eq("PORTFOLIO_PROFIT"), isNull(), eq("YTD"), isNull());
  }

  @Test
  void abstentionDoesNotReplaceStoredReportContext() {
    when(assistant.ask(eq(21L), any(), eq(alice)))
        .thenReturn(
            new FinancialAiOrchestratorController.Answer(
                "Unsupported", List.of(), List.of("ABSTAINED")));
    var response =
        controller.ask(
            21, id, new AssistantConversationController.AskRequest("Unsupported"), alice);
    assertEquals(0, response.version());
    verify(conversations, never()).updateContext(any(), any(), any(), any(), any());
  }

  @Test
  void whatIfUsesCanonicalCalculationBeforeSavingRevision() {
    when(research.retirementSimulation(21)).thenReturn(simulation("180000"));
    when(research.retirementWhatIf(21, "Base", new BigDecimal("216000"), new BigDecimal("12000")))
        .thenReturn(simulation("216000"));
    var revision =
        new AssistantConversationService.ScenarioRevision(
            id, 1, "Base", new BigDecimal("216000"), new BigDecimal("12000"));
    when(conversations.saveScenario(eq(current), eq("Base"), any(), any())).thenReturn(revision);
    var result =
        controller.draftScenario(
            21,
            id,
            new AssistantConversationController.ScenarioRequest(
                "Base", new BigDecimal("216000"), new BigDecimal("12000")),
            alice);
    assertEquals(HttpStatus.OK, result.getStatusCode());
    assertEquals("CALCULATED", result.getBody().status());
    assertEquals("180000", result.getBody().baseline().annualLivingExpenses());
    assertEquals("216000", result.getBody().whatIf().annualLivingExpenses());
    verify(research)
        .retirementWhatIf(21, "Base", new BigDecimal("216000"), new BigDecimal("12000"));
    verify(conversations).saveScenario(eq(current), eq("Base"), any(), any());
  }

  @Test
  void unavailableCalculationNeverCreatesARevision() {
    when(research.retirementSimulation(21)).thenReturn(simulation("180000"));
    when(research.retirementWhatIf(eq(21L), eq("Base"), any(), any()))
        .thenThrow(new IllegalArgumentException("invalid assumptions"));
    var error =
        assertThrows(
            ResponseStatusException.class,
            () ->
                controller.draftScenario(
                    21,
                    id,
                    new AssistantConversationController.ScenarioRequest(
                        "Base", new BigDecimal("-1"), BigDecimal.ZERO),
                    alice));
    assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    verify(conversations, never()).saveScenario(any(), any(), any(), any());
  }

  @Test
  void unauthorizedPrincipalCannotReadOrExecuteTools() {
    var bob = new UsernamePasswordAuthenticationToken("bob", "n/a");
    assertEquals(
        HttpStatus.FORBIDDEN,
        assertThrows(ResponseStatusException.class, () -> controller.get(21, id, bob))
            .getStatusCode());
    verifyNoInteractions(assistant, research);
    verify(conversations, never()).get(any(), anyLong(), anyString());
  }
}
