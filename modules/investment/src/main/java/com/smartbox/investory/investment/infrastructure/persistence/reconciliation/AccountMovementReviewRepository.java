package com.smartbox.investory.investment.infrastructure.persistence.reconciliation;

import com.smartbox.investory.investment.api.reporting.model.AccountMovementReview;
import com.smartbox.investory.investment.api.reporting.model.AccountMovementReviewCommand;
import com.smartbox.investory.investment.api.reporting.model.AccountMovementReviewResolution;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountMovementReviewRepository {
  private static final String LIST_ACTIVE_REVIEWS =
      """
      SELECT t.issue_code, t.entity_id AS account_id, t.entity_key AS account_name,
             t.event_date, t.previous_date, t.gap_days, t.previous_value, t.current_value,
             t.change_pct, t.ratio, t.explanation, t.event_fingerprint,
             (latest.resolution IS NULL
              OR latest.event_fingerprint <> t.event_fingerprint
              OR latest.resolution = 'STILL_UNDER_REVIEW') AS active,
             (latest.event_fingerprint = t.event_fingerprint) AS prior_decision_matches_current,
             latest.resolution, latest.rationale, latest.evidence_reference,
             latest.reviewed_by, latest.reviewed_at
      FROM investory.recon_v_account_temporal_anomaly_fingerprinted t
      JOIN investory.accounts a ON a.id = t.entity_id AND a.portfolio_id = ?
      LEFT JOIN LATERAL (
          SELECT r.event_fingerprint, r.resolution, r.rationale, r.evidence_reference,
                 r.reviewed_by, r.reviewed_at
          FROM investory.reconciliation_account_movement_reviews r
          WHERE r.issue_code = t.issue_code AND r.account_id = t.entity_id
            AND r.event_date = t.event_date
          ORDER BY r.id DESC LIMIT 1
      ) latest ON true
      WHERE t.issue_code IN ('ACCOUNT_MARKET_VALUE_SPIKE', 'ACCOUNT_EQUITY_SPIKE')
      ORDER BY active DESC, t.event_date DESC, t.entity_id, t.issue_code
      LIMIT 500
      """;

  private static final String INSERT_REVIEW =
      """
      INSERT INTO investory.reconciliation_account_movement_reviews
          (issue_code, account_id, event_date, event_fingerprint, resolution,
           rationale, evidence_reference, reviewed_by)
      SELECT ?, ?, ?, ?, ?, ?, ?, ?
      FROM investory.recon_v_account_temporal_anomaly_fingerprinted t
      JOIN investory.accounts a ON a.id = t.entity_id AND a.portfolio_id = ?
      WHERE t.issue_code = ? AND t.entity_id = ? AND t.event_date = ?
        AND t.event_fingerprint = ?
      """;

  private final JdbcTemplate jdbcTemplate;

  public AccountMovementReviewRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<AccountMovementReview> findActiveByPortfolioId(Long portfolioId) {
    return jdbcTemplate.query(LIST_ACTIVE_REVIEWS, this::mapRow, portfolioId);
  }

  public boolean appendReview(Long portfolioId, AccountMovementReviewCommand command) {
    return jdbcTemplate.update(
            INSERT_REVIEW,
            command.issueCode(),
            command.accountId(),
            command.eventDate(),
            command.eventFingerprint(),
            command.resolution().name(),
            command.rationale().trim(),
            command.evidenceReference().trim(),
            command.reviewedBy().trim(),
            portfolioId,
            command.issueCode(),
            command.accountId(),
            command.eventDate(),
            command.eventFingerprint())
        == 1;
  }

  private AccountMovementReview mapRow(ResultSet row, int rowNumber) throws SQLException {
    String previousResolution = row.getString("resolution");
    Timestamp reviewedAt = row.getTimestamp("reviewed_at");
    return new AccountMovementReview(
        row.getString("issue_code"),
        row.getLong("account_id"),
        row.getString("account_name"),
        row.getDate("event_date").toLocalDate(),
        row.getDate("previous_date").toLocalDate(),
        row.getInt("gap_days"),
        row.getBigDecimal("previous_value"),
        row.getBigDecimal("current_value"),
        row.getBigDecimal("change_pct"),
        row.getBigDecimal("ratio"),
        row.getString("explanation"),
        row.getString("event_fingerprint"),
        row.getBoolean("active"),
        row.getBoolean("prior_decision_matches_current"),
        previousResolution == null
            ? null
            : AccountMovementReviewResolution.valueOf(previousResolution),
        row.getString("rationale"),
        row.getString("evidence_reference"),
        row.getString("reviewed_by"),
        reviewedAt == null ? null : reviewedAt.toInstant());
  }
}
