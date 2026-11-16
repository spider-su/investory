package com.smartbox.investory.rag;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable public cache and atomic rolling budget, shared across process restarts. */
@Component
@ConditionalOnProperty(prefix = "investory.rag", name = "enabled", havingValue = "true")
public class MarketDataStore {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;

  public MarketDataStore(
      JdbcTemplate jdbc, org.springframework.transaction.PlatformTransactionManager manager) {
    this.jdbc = jdbc;
    this.transactions = new TransactionTemplate(manager);
  }

  public record Entry(
      String data, Instant fetchedAt, Instant expiresAt, Instant blockedUntil, String reason) {}

  private static Instant instant(java.sql.ResultSet rs, String column)
      throws java.sql.SQLException {
    var timestamp = rs.getTimestamp(column);
    return timestamp == null ? null : timestamp.toInstant();
  }

  public Entry find(String profile, String tool, String symbol) {
    var rows =
        jdbc.query(
            "SELECT * FROM investory.external_market_cache WHERE provider_profile=? AND tool=? AND symbol=?",
            (rs, n) ->
                new Entry(
                    rs.getString("data"),
                    instant(rs, "fetched_at"),
                    instant(rs, "expires_at"),
                    instant(rs, "blocked_until"),
                    rs.getString("blocked_reason")),
            profile,
            tool,
            symbol);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  public int remaining(String profile, int limit, Instant now) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM investory.external_market_attempt WHERE provider_profile=? AND reserved_at>?",
            Integer.class,
            profile,
            Timestamp.from(now.minusSeconds(86400)));
    return Math.max(0, limit - count);
  }

  /** Reservation commits before I/O: failed attempts consume budget too. */
  public boolean reserve(String profile, int limit, Instant now) {
    return Boolean.TRUE.equals(
        transactions.execute(
            status -> {
              jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", rs -> {}, profile);
              Boolean blocked =
                  jdbc.queryForObject(
                      "SELECT EXISTS(SELECT 1 FROM investory.external_market_provider_state WHERE provider_profile=? AND blocked_until>?)",
                      Boolean.class,
                      profile,
                      Timestamp.from(now));
              if (Boolean.TRUE.equals(blocked) || remaining(profile, limit, now) == 0) return false;
              jdbc.update(
                  "DELETE FROM investory.external_market_attempt WHERE provider_profile=? AND reserved_at<?",
                  profile,
                  Timestamp.from(now.minusSeconds(7 * 86400)));
              jdbc.update(
                  "INSERT INTO investory.external_market_attempt(provider_profile,reserved_at) VALUES (?,?)",
                  profile,
                  Timestamp.from(now));
              return true;
            }));
  }

  public void save(
      String profile, String tool, String symbol, String data, Instant now, Instant expires) {
    jdbc.update(
        """
        INSERT INTO investory.external_market_cache(provider_profile,tool,symbol,data,fetched_at,expires_at)
        VALUES (?,?,?,?,?,?) ON CONFLICT(provider_profile,tool,symbol) DO UPDATE SET
        data=excluded.data,fetched_at=excluded.fetched_at,expires_at=excluded.expires_at,blocked_until=NULL,blocked_reason=NULL
        """,
        profile,
        tool,
        symbol,
        data,
        Timestamp.from(now),
        Timestamp.from(expires));
  }

  public void block(String profile, String tool, String symbol, Instant until, String reason) {
    jdbc.update(
        """
        INSERT INTO investory.external_market_cache(provider_profile,tool,symbol,blocked_until,blocked_reason)
        VALUES (?,?,?,?,?) ON CONFLICT(provider_profile,tool,symbol) DO UPDATE SET
        blocked_until=excluded.blocked_until,blocked_reason=excluded.blocked_reason
        """,
        profile,
        tool,
        symbol,
        Timestamp.from(until),
        reason);
  }

  public void blockProvider(String profile, Instant until) {
    jdbc.update(
        """
        INSERT INTO investory.external_market_provider_state(provider_profile,blocked_until) VALUES (?,?)
        ON CONFLICT(provider_profile) DO UPDATE SET blocked_until=greatest(external_market_provider_state.blocked_until,excluded.blocked_until)
        """,
        profile,
        Timestamp.from(until));
  }
}
