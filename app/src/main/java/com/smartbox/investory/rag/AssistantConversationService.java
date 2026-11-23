package com.smartbox.investory.rag;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Durable, principal-and-portfolio-scoped state for the financial assistant.
 * Stores only explicit tool context and scenario assumptions, never chat messages or secrets.
 */
@Service
@ConditionalOnProperty(prefix = "investory.rag", name = "enabled", havingValue = "true")
public class AssistantConversationService {
  private final JdbcTemplate jdbc;

  public AssistantConversationService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record Conversation(
      UUID id,
      long portfolioId,
      String principal,
      String topic,
      String asset,
      String reportingPeriod,
      String selectedPlan,
      long version,
      Instant expiresAt) {}

  public record ScenarioRevision(
      UUID conversationId,
      int revision,
      String baselinePlan,
      BigDecimal annualLivingExpenses,
      BigDecimal annualDiscretionaryExpenses) {}

  public Conversation create(long portfolioId, String principal) {
    validateScope(portfolioId, principal);
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO investory.assistant_conversation(id, portfolio_id, principal) VALUES (?,?,?)",
        id, portfolioId, principal);
    return get(id, portfolioId, principal);
  }

  public Conversation get(UUID id, long portfolioId, String principal) {
    validateScope(portfolioId, principal);
    if (id == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid conversation");
    List<Conversation> rows = jdbc.query(
        """
        SELECT id, portfolio_id, principal, topic, asset, reporting_period, selected_plan,
               state_version, expires_at
        FROM investory.assistant_conversation
        WHERE id=? AND portfolio_id=? AND principal=? AND expires_at>now()
        """,
        (rs, row) -> new Conversation(
            rs.getObject("id", UUID.class),
            rs.getLong("portfolio_id"),
            rs.getString("principal"),
            rs.getString("topic"),
            rs.getString("asset"),
            rs.getString("reporting_period"),
            rs.getString("selected_plan"),
            rs.getLong("state_version"),
            rs.getTimestamp("expires_at").toInstant()),
        id, portfolioId, principal);
    // Do not reveal whether another principal owns the conversation.
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
    return rows.getFirst();
  }

  /** Optimistic concurrency prevents two clients from silently overwriting follow-up context. */
  public Conversation updateContext(
      Conversation current, String topic, String asset, String period, String selectedPlan) {
    int updated = jdbc.update(
        """
        UPDATE investory.assistant_conversation
        SET topic=?, asset=?, reporting_period=?, selected_plan=?,
            state_version=state_version+1, updated_at=now()
        WHERE id=? AND portfolio_id=? AND principal=? AND state_version=? AND expires_at>now()
        """,
        topic, asset, period, selectedPlan, current.id(), current.portfolioId(),
        current.principal(), current.version());
    if (updated != 1)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Conversation was updated concurrently");
    return get(current.id(), current.portfolioId(), current.principal());
  }

  /** Save a what-if revision without modifying the canonical saved retirement plan. */
  @Transactional
  public ScenarioRevision saveScenario(
      Conversation conversation,
      String plan,
      BigDecimal annualLivingExpenses,
      BigDecimal annualDiscretionaryExpenses) {
    if (plan == null || plan.isBlank() || plan.length() > 255
        || annualLivingExpenses == null || annualLivingExpenses.signum() < 0
        || annualDiscretionaryExpenses == null || annualDiscretionaryExpenses.signum() < 0)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid scenario assumptions");
    // Serialize revision allocation per conversation, across application instances.
    jdbc.query(
        "SELECT id FROM investory.assistant_conversation WHERE id=? AND portfolio_id=? AND principal=? AND expires_at>now() FOR UPDATE",
        rs -> {}, conversation.id(), conversation.portfolioId(), conversation.principal());
    int revision = jdbc.queryForObject(
        "SELECT COALESCE(MAX(revision),0)+1 FROM investory.assistant_scenario_revision WHERE conversation_id=?",
        Integer.class, conversation.id());
    jdbc.update(
        """
        INSERT INTO investory.assistant_scenario_revision
          (conversation_id, revision, baseline_plan, annual_living_expenses, annual_discretionary_expenses)
        VALUES (?,?,?,?,?)
        """,
        conversation.id(), revision, plan, annualLivingExpenses, annualDiscretionaryExpenses);
    return new ScenarioRevision(
        conversation.id(), revision, plan, annualLivingExpenses, annualDiscretionaryExpenses);
  }

  public List<ScenarioRevision> revisions(Conversation conversation) {
    return jdbc.query(
        """
        SELECT r.conversation_id, r.revision, r.baseline_plan,
               r.annual_living_expenses, r.annual_discretionary_expenses
        FROM investory.assistant_scenario_revision r
        JOIN investory.assistant_conversation c ON c.id=r.conversation_id
        WHERE c.id=? AND c.portfolio_id=? AND c.principal=? AND c.expires_at>now()
        ORDER BY r.revision
        """,
        (rs, row) -> new ScenarioRevision(
            rs.getObject("conversation_id", UUID.class), rs.getInt("revision"),
            rs.getString("baseline_plan"), rs.getBigDecimal("annual_living_expenses"),
            rs.getBigDecimal("annual_discretionary_expenses")),
        conversation.id(), conversation.portfolioId(), conversation.principal());
  }

  private static void validateScope(long portfolioId, String principal) {
    if (portfolioId <= 0 || principal == null || principal.isBlank())
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authentication required");
  }
}
