package com.smartbox.investory.rag;

import com.smartbox.investory.config.AuthorizationService;
import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.mcp.PortfolioResearchClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Opt-in conversation API. The existing orchestrator remains the only execution/authorization
 * boundary for assistant tools. A generated plan never directly invokes privileged MCP endpoints.
 */
@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/assistant/conversations")
@ConditionalOnProperty(prefix = "investory.rag", name = "enabled", havingValue = "true")
public class AssistantConversationController {
  private final AuthorizationService authorization;
  private final AssistantConversationService conversations;
  private final FinancialAiOrchestratorController assistant;
  private final PortfolioResearchClient research;

  public AssistantConversationController(
      AuthorizationService authorization,
      AssistantConversationService conversations,
      FinancialAiOrchestratorController assistant,
      PortfolioResearchClient research) {
    this.authorization = authorization;
    this.conversations = conversations;
    this.assistant = assistant;
    this.research = research;
  }

  @PostMapping
  public AssistantConversationService.Conversation create(
      @PathVariable long portfolioId, Authentication authentication) {
    authorize(portfolioId, authentication);
    return conversations.create(portfolioId, authentication.getName());
  }

  @GetMapping("/{conversationId}")
  public AssistantConversationService.Conversation get(
      @PathVariable long portfolioId,
      @PathVariable UUID conversationId,
      Authentication authentication) {
    authorize(portfolioId, authentication);
    return conversations.get(conversationId, portfolioId, authentication.getName());
  }

  @PostMapping("/{conversationId}/ask")
  public ConversationAnswer ask(
      @PathVariable long portfolioId,
      @PathVariable UUID conversationId,
      @RequestBody AskRequest request,
      Authentication authentication) {
    authorize(portfolioId, authentication);
    var current = conversations.get(conversationId, portfolioId, authentication.getName());
    if (request == null || request.question() == null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question required");

    FinancialAiOrchestratorController.Context context = null;
    if (current.topic() != null && current.reportingPeriod() != null) {
      try {
        context =
            new FinancialAiOrchestratorController.Context(
                portfolioId,
                FinancialAiOrchestratorController.Topic.valueOf(current.topic()),
                current.asset(),
                DashboardPeriod.fromUrlValue(current.reportingPeriod()));
      } catch (IllegalArgumentException ignored) {
        // Invalid persisted data is never supplied as a caller-controlled tool context.
      }
    }

    var answer =
        assistant.ask(
            portfolioId,
            new FinancialAiOrchestratorController.Question(request.question(), context),
            authentication);
    // Only verified, unambiguous report contexts may establish a new follow-up reference.
    if (answer.context() != null && !answer.sources().isEmpty()) {
      current =
          conversations.updateContext(
              current,
              answer.context().topic().name(),
              answer.context().asset(),
              answer.context().period().wireValue(),
              current.selectedPlan());
    }
    return new ConversationAnswer(current.id(), current.version(), answer);
  }

  /**
   * Stage a scenario revision only. Never claim a projection was calculated from changed inputs:
   * that needs a canonical retirement simulation API accepting explicit assumptions.
   */
  @PostMapping("/{conversationId}/scenario-revisions")
  public ResponseEntity<ScenarioDraft> draftScenario(
      @PathVariable long portfolioId,
      @PathVariable UUID conversationId,
      @RequestBody ScenarioRequest request,
      Authentication authentication) {
    authorize(portfolioId, authentication);
    var conversation = conversations.get(conversationId, portfolioId, authentication.getName());
    if (request == null || request.plan() == null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Scenario plan required");

    var canonical = research.retirementSimulation(portfolioId);
    if (!canonical.available() || !request.plan().equals(canonical.plan()))
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Select an available retirement plan");
    // Recalculate with the canonical retirement projection before persisting the revision.
    // No saved plan or source financial facts are modified.
    PortfolioResearchClient.RetirementSimulation computed;
    try {
      computed =
          research.retirementWhatIf(
              portfolioId,
              request.plan(),
              request.annualLivingExpenses(),
              request.annualDiscretionaryExpenses());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid what-if scenario");
    }
    if (!computed.available() || computed.scenarios().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Scenario unavailable");
    }
    var revision =
        conversations.saveScenario(
            conversation,
            request.plan(),
            request.annualLivingExpenses(),
            request.annualDiscretionaryExpenses());
    return ResponseEntity.ok(new ScenarioDraft(revision, "CALCULATED", canonical, computed));
  }

  @GetMapping("/{conversationId}/scenario-revisions")
  public List<AssistantConversationService.ScenarioRevision> revisions(
      @PathVariable long portfolioId,
      @PathVariable UUID conversationId,
      Authentication authentication) {
    authorize(portfolioId, authentication);
    return conversations.revisions(
        conversations.get(conversationId, portfolioId, authentication.getName()));
  }

  private void authorize(long portfolioId, Authentication authentication) {
    if (portfolioId <= 0 || !authorization.canReadScoped(portfolioId, authentication))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  }

  public record AskRequest(String question) {}

  public record ConversationAnswer(
      UUID conversationId, long version, FinancialAiOrchestratorController.Answer answer) {}

  public record ScenarioRequest(
      String plan, BigDecimal annualLivingExpenses, BigDecimal annualDiscretionaryExpenses) {}

  public record ScenarioDraft(
      AssistantConversationService.ScenarioRevision assumptions,
      String status,
      PortfolioResearchClient.RetirementSimulation baseline,
      PortfolioResearchClient.RetirementSimulation whatIf) {}
}
