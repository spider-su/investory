package com.smartbox.investory.notifications;

import com.smartbox.investory.integrations.notifications.application.NotificationLinkBuilder;
import com.smartbox.investory.shared.notifications.NotificationCandidate;
import com.smartbox.investory.shared.notifications.NotificationEventPublisher;
import com.smartbox.investory.shared.notifications.NotificationEventType;
import com.smartbox.investory.shared.notifications.NotificationSeverity;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class RadarNotificationState {
  private static final String DIGEST = "DIGEST";
  private static final String SCAN = "SCAN";
  private static final String RESERVE = "RESERVE";

  private final JdbcTemplate jdbc;
  private final NotificationEventPublisher publisher;
  private final NotificationLinkBuilder links;

  boolean digestProcessed(long portfolio, LocalDate session) {
    var last = read(portfolio, DIGEST).date();
    return last != null && !session.isAfter(last);
  }

  @Transactional
  public void scan(
      long portfolio, LocalDate session, boolean unhealthy, String message, Instant now) {
    lock(portfolio);
    var before = read(portfolio, SCAN);
    if ((before.date() != null && session.isBefore(before.date())) || before.observedAfter(now))
      return;
    var changed = before.active() != unhealthy;
    long sequence = before.sequence() + (changed && unhealthy ? 1 : 0);
    save(portfolio, SCAN, new State(unhealthy, sequence, session, 0, null, null, now));
    if (changed) {
      publish(
          portfolio,
          SCAN,
          sequence + (unhealthy ? ":ACTIVE" : ":RECOVERED"),
          unhealthy ? "Market Radar needs attention" : "Market Radar recovered",
          message,
          "/market-radar/operations",
          now,
          Map.of(),
          !unhealthy);
    }
  }

  @Transactional
  public void reserve(
      long portfolio,
      BigDecimal shortCash,
      BigDecimal longCash,
      RadarNotificationSettings.Reserve config,
      Instant now) {
    lock(portfolio);
    var before = read(portfolio, RESERVE);
    if (before.observedAfter(now)) return;
    var cash = shortCash.add(longCash);
    var committed = config.commitments();
    var available = committed == null ? cash : cash.subtract(committed);
    var difference = available.subtract(config.floor());
    boolean breached = difference.signum() < 0;
    long sequence = before.sequence() + (breached && !before.active() ? 1 : 0);
    if (breached && !before.active()) {
      String currency = config.currency().name();
      String detail =
          "Shortfall "
              + money(difference.negate(), currency)
              + " against floor "
              + money(config.floor(), currency)
              + "\nBrokerage cash "
              + cashLine(shortCash, before.primary(), currency)
              + "\nLong-term cash "
              + cashLine(longCash, before.secondary(), currency)
              + (committed == null
                  ? "\nReported reserve "
                      + money(cash, currency)
                      + ". Other commitments are unverified."
                  : "\nCash after configured commitments " + money(available, currency));
      publish(
          portfolio,
          RESERVE,
          sequence + ":ACTIVE",
          "Retirement reserve below floor",
          detail,
          "/portfolios/" + portfolio + "/investment-profile",
          now,
          Map.of(),
          false);
    }
    save(portfolio, RESERVE, new State(breached, sequence, null, 0, shortCash, longCash, now));
  }

  @Transactional
  public void digest(
      long portfolio,
      LocalDate session,
      LocalDate previous,
      int confirmations,
      List<Observation> observations,
      Set<String> heldSymbols,
      Instant now) {
    lock(portfolio);
    if (digestProcessed(portfolio, session)) return;
    // A confirmed portfolio no longer holding an asset can re-arm that incident.
    var holdingRules =
        jdbc.queryForList(
            """
        SELECT rule_key FROM investory.radar_notification_state
        WHERE portfolio_id=? AND active AND starts_with(rule_key, 'HOLD:')
        """,
            String.class,
            portfolio);
    for (String key : holdingRules) {
      if (!heldSymbols.contains(key.substring("HOLD:".length()))) {
        var before = read(portfolio, key);
        save(portfolio, key, new State(false, before.sequence(), session, 0, null, null, now));
      }
    }
    var hits = new ArrayList<Observation>();
    for (var observation : observations) {
      var before = read(portfolio, observation.rule());
      int streak =
          !observation.qualifies()
              ? 0
              : observation.alreadyConfirmed()
                  ? confirmations
                  : previous.equals(before.date())
                      ? Math.min(confirmations, before.streak() + 1)
                      : 1;
      boolean active = observation.qualifies() && (before.active() || streak >= confirmations);
      boolean newIncident = active && !before.active();
      if (newIncident) hits.add(observation);
      save(
          portfolio,
          observation.rule(),
          new State(
              active, before.sequence() + (newIncident ? 1 : 0), session, streak, null, null, now));
    }
    save(portfolio, DIGEST, new State(false, 0, session, 0, null, null, now));
    if (hits.isEmpty()) return;
    // Bound each message to Telegram's limit while preserving every triggered incident.
    for (int start = 0; start < hits.size(); start += 6) {
      var page = hits.subList(start, Math.min(start + 6, hits.size()));
      var lines = new StringBuilder("Market close " + session);
      var payload = new LinkedHashMap<String, String>();
      for (int i = 0; i < page.size(); i++) {
        var hit = page.get(i);
        lines.append("\n\n").append(i + 1).append(". ").append(hit.message());
        payload.put("link" + (i + 1), links.link(hit.path()));
      }
      String part =
          hits.size() > 6 ? " (" + (start / 6 + 1) + "/" + ((hits.size() + 5) / 6) + ")" : "";
      publish(
          portfolio,
          DIGEST,
          session + ":" + start,
          "Radar and portfolio review" + part,
          lines.toString(),
          "/market-radar",
          now,
          payload,
          true);
    }
  }

  private void lock(long portfolio) {
    // Serialize state and outbox writes across replicas and manual/retried invocations.
    jdbc.query(
        "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
        rs -> {},
        "radar-notifications:" + portfolio);
  }

  private State read(long portfolio, String key) {
    return jdbc
        .query(
            """
        SELECT active, sequence, last_session, streak, primary_amount, secondary_amount, observed_at
        FROM investory.radar_notification_state WHERE portfolio_id=? AND rule_key=?
        """,
            (rs, row) ->
                new State(
                    rs.getBoolean(1),
                    rs.getLong(2),
                    rs.getObject(3, LocalDate.class),
                    rs.getInt(4),
                    rs.getBigDecimal(5),
                    rs.getBigDecimal(6),
                    rs.getObject(7, OffsetDateTime.class).toInstant()),
            portfolio,
            key)
        .stream()
        .findFirst()
        .orElse(new State(false, 0, null, 0, null, null, null));
  }

  private void save(long portfolio, String key, State value) {
    jdbc.update(
        """
        INSERT INTO investory.radar_notification_state
            (portfolio_id,rule_key,active,sequence,last_session,streak,primary_amount,secondary_amount,observed_at)
        VALUES (?,?,?,?,?,?,?,?,?)
        ON CONFLICT (portfolio_id,rule_key) DO UPDATE SET active=excluded.active,
            sequence=excluded.sequence,last_session=excluded.last_session,streak=excluded.streak,
            primary_amount=excluded.primary_amount,secondary_amount=excluded.secondary_amount, observed_at=excluded.observed_at
        """,
        portfolio,
        key,
        value.active(),
        value.sequence(),
        value.date(),
        value.streak(),
        value.primary(),
        value.secondary(),
        Timestamp.from(value.observedAt()));
  }

  private void publish(
      long portfolio,
      String rule,
      String event,
      String title,
      String message,
      String path,
      Instant now,
      Map<String, String> extras,
      boolean informational) {
    var payload = new LinkedHashMap<>(extras);
    payload.put("message", message);
    payload.put("link", links.link(path));
    publisher.publish(
        new NotificationCandidate(
            NotificationEventType.THRESHOLD_ALERT,
            informational ? NotificationSeverity.INFO : NotificationSeverity.WARNING,
            portfolio,
            "RADAR_NOTIFICATION",
            rule,
            "RADAR:" + portfolio + ":" + rule + ":" + event,
            title,
            payload,
            now));
  }

  private static String cashLine(BigDecimal value, BigDecimal previous, String currency) {
    return money(value, currency)
        + (previous == null ? "" : " (change " + money(value.subtract(previous), currency) + ")");
  }

  static String money(BigDecimal amount, String currency) {
    return String.format(java.util.Locale.ROOT, "%,.2f %s", amount, currency);
  }

  record Observation(
      String rule, boolean qualifies, boolean alreadyConfirmed, String message, String path) {}

  private record State(
      boolean active,
      long sequence,
      LocalDate date,
      int streak,
      BigDecimal primary,
      BigDecimal secondary,
      Instant observedAt) {
    boolean observedAfter(Instant candidate) {
      return observedAt != null && observedAt.isAfter(candidate);
    }
  }
}
