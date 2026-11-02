package com.smartbox.investory.integrations.notifications.application;

import com.smartbox.investory.shared.notifications.NotificationCandidate;
import com.smartbox.investory.shared.notifications.NotificationEventPublisher;
import com.smartbox.investory.shared.notifications.NotificationEventType;
import com.smartbox.investory.shared.notifications.NotificationSeverity;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * User-editable, notification-only price thresholds for open equity/ETF positions. One pair per
 * canonical asset across accounts. Never sends orders to a broker.
 */
@Service
@RequiredArgsConstructor
public class PositionPriceAlertService {
  private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
  private static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("10");
  private static final BigDecimal HUNDRED = new BigDecimal("100");
  private static final BigDecimal REARM_PERCENT = new BigDecimal("2");
  private static final String POSITIONS =
      """
      SELECT v.portfolio_id, v.asset_id, a.symbol,
             SUM(v.volume) AS quantity,
             SUM(v.cost_basis_native) / NULLIF(SUM(v.volume), 0) AS average_cost,
             MAX(v.market_price) AS price,
             MIN(v.market_price_currency) AS currency,
             MAX(v.selected_price_date) AS quote_date
      FROM investory.app_v_current_open_position_rows v
      JOIN investory.assets a ON a.id = v.asset_id
      JOIN investory.accounts acc ON acc.id = v.account_id
      WHERE a.asset_type IN ('EQUITY', 'ETF')
        AND NOT acc.cash_only AND v.volume > 0
        AND v.portfolio_id = ?
      GROUP BY v.portfolio_id, v.asset_id, a.symbol
      HAVING SUM(v.volume) > 0
         AND COUNT(DISTINCT v.market_price_currency) <= 1
      ORDER BY a.symbol
      """;

  private final JdbcTemplate jdbc;
  private final NotificationEventPublisher publisher;
  private final Clock clock;

  @Value("${app.notifications.base-url:http://localhost:8080}")
  private String baseUrl;

  public record RuleView(
      long id,
      String side,
      String mode,
      BigDecimal threshold,
      String actionText,
      boolean enabled,
      boolean active) {}

  public record PositionView(
      String symbol,
      BigDecimal quantity,
      BigDecimal averageCost,
      BigDecimal price,
      String currency,
      LocalDate quoteDate,
      List<RuleView> rules) {}

  public record UpdateRule(
      String side, String mode, BigDecimal threshold, String actionText, boolean enabled) {}

  public record UpdateRequest(List<UpdateRule> rules) {}

  public record HistoryItem(
      long eventId,
      String side,
      String message,
      String deliveryState,
      Instant createdAt,
      Instant deliveredAt) {}

  private record Position(
      long portfolioId,
      long assetId,
      String symbol,
      BigDecimal quantity,
      BigDecimal averageCost,
      BigDecimal price,
      String currency,
      LocalDate quoteDate) {}

  private record Rule(
      long id,
      String side,
      String mode,
      BigDecimal threshold,
      String action,
      boolean enabled,
      boolean initialized,
      boolean active,
      long sequence,
      BigDecimal lastReference) {
    RuleView view() {
      return new RuleView(id, side, mode, threshold, action, enabled, active);
    }
  }

  private List<Position> positions(long portfolioId) {
    return jdbc.query(
        POSITIONS,
        (rs, row) ->
            new Position(
                rs.getLong("portfolio_id"),
                rs.getLong("asset_id"),
                rs.getString("symbol"),
                rs.getBigDecimal("quantity"),
                rs.getBigDecimal("average_cost"),
                rs.getBigDecimal("price"),
                rs.getString("currency"),
                rs.getDate("quote_date") == null ? null : rs.getDate("quote_date").toLocalDate()),
        portfolioId);
  }

  private Position position(long portfolioId, String symbol) {
    return positions(portfolioId).stream()
        .filter(p -> p.symbol().equals(symbol))
        .findFirst()
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No open equity/ETF position"));
  }

  private List<Rule> rules(long portfolioId, long assetId) {
    return jdbc.query(
        """
        SELECT id, side, mode, threshold, action_text, enabled, initialized,
               active, incident_sequence, last_reference_price
        FROM investory.position_price_alert
        WHERE portfolio_id = ? AND asset_id = ? ORDER BY side
        """,
        (rs, row) ->
            new Rule(
                rs.getLong("id"),
                rs.getString("side"),
                rs.getString("mode"),
                rs.getBigDecimal("threshold"),
                rs.getString("action_text"),
                rs.getBoolean("enabled"),
                rs.getBoolean("initialized"),
                rs.getBoolean("active"),
                rs.getLong("incident_sequence"),
                rs.getBigDecimal("last_reference_price")),
        portfolioId,
        assetId);
  }

  private static String defaultAction(String side) {
    return "LOSS".equals(side) ? "Review downside risk" : "Review taking profits";
  }

  private static RuleView fallbackRule(String side) {
    return new RuleView(0, side, "PERCENT", DEFAULT_THRESHOLD, defaultAction(side), true, false);
  }

  @Transactional(readOnly = true)
  public List<String> eligibleSymbols(long portfolioId) {
    return positions(portfolioId).stream().map(Position::symbol).distinct().toList();
  }

  @Transactional(readOnly = true)
  public PositionView get(long portfolioId, String symbol) {
    Position position = position(portfolioId, symbol);
    Map<String, RuleView> values = new java.util.HashMap<>();
    for (Rule rule : rules(portfolioId, position.assetId())) {
      values.put(rule.side(), rule.view());
    }
    return new PositionView(
        position.symbol(),
        position.quantity(),
        position.averageCost(),
        position.price(),
        position.currency(),
        position.quoteDate(),
        List.of(
            values.getOrDefault("LOSS", fallbackRule("LOSS")),
            values.getOrDefault("PROFIT", fallbackRule("PROFIT"))));
  }

  @Transactional
  public PositionView update(long portfolioId, String symbol, UpdateRequest request) {
    Position position = position(portfolioId, symbol);
    if (request == null || request.rules() == null || request.rules().size() != 2) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Configure exactly two alerts");
    }
    java.util.Set<String> seen = new java.util.HashSet<>();
    for (UpdateRule rule : request.rules()) {
      if (rule == null
          || !List.of("LOSS", "PROFIT").contains(rule.side())
          || !seen.add(rule.side())
          || !List.of("PERCENT", "PRICE").contains(rule.mode())
          || rule.threshold() == null
          || rule.threshold().signum() <= 0
          || ("PERCENT".equals(rule.mode()) && rule.threshold().compareTo(HUNDRED) > 0)
          || rule.threshold().compareTo(new BigDecimal("100000000")) > 0
          || rule.actionText() == null
          || rule.actionText().isBlank()
          || rule.actionText().length() > 240) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid alert configuration");
      }
      jdbc.update(
          """
          INSERT INTO investory.position_price_alert
             (portfolio_id, asset_id, side, mode, threshold, action_text, enabled)
          VALUES (?, ?, ?, ?, ?, ?, ?)
          ON CONFLICT (portfolio_id, asset_id, side) DO UPDATE
          SET mode = EXCLUDED.mode, threshold = EXCLUDED.threshold,
              action_text = EXCLUDED.action_text, enabled = EXCLUDED.enabled,
              initialized = CASE
                WHEN position_price_alert.mode IS DISTINCT FROM EXCLUDED.mode
                  OR position_price_alert.threshold IS DISTINCT FROM EXCLUDED.threshold
                  OR position_price_alert.enabled IS DISTINCT FROM EXCLUDED.enabled
                THEN false ELSE position_price_alert.initialized END,
              active = CASE
                WHEN position_price_alert.mode IS DISTINCT FROM EXCLUDED.mode
                  OR position_price_alert.threshold IS DISTINCT FROM EXCLUDED.threshold
                  OR position_price_alert.enabled IS DISTINCT FROM EXCLUDED.enabled
                THEN false ELSE position_price_alert.active END,
              updated_at = now()
          """,
          portfolioId,
          position.assetId(),
          rule.side(),
          rule.mode(),
          rule.threshold(),
          rule.actionText().trim(),
          rule.enabled());
    }
    return get(portfolioId, symbol);
  }

  @Transactional(readOnly = true)
  public List<HistoryItem> history(long portfolioId, String symbol) {
    Position position = position(portfolioId, symbol);
    return jdbc.query(
        """
        SELECT e.id, p.side, e.payload->>'message' AS message,
               e.delivery_state, e.created_at, e.delivered_at
        FROM investory.notification_event e
        JOIN investory.position_price_alert p
          ON e.source_entity_type = 'POSITION_PRICE_ALERT'
         AND e.source_entity_id = p.id::text
        WHERE p.portfolio_id = ? AND p.asset_id = ?
        ORDER BY e.created_at DESC, e.id DESC LIMIT 30
        """,
        (rs, row) ->
            new HistoryItem(
                rs.getLong("id"),
                rs.getString("side"),
                rs.getString("message"),
                rs.getString("delivery_state"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("delivered_at") == null
                    ? null
                    : rs.getTimestamp("delivered_at").toInstant()),
        portfolioId,
        position.assetId());
  }

  /** Runs at 15:00 Warsaw on weekdays. Uses the last selected close, not an intraday quote. */
  @Transactional
  public void evaluate(long portfolioId) {
    List<Position> positions = positions(portfolioId);
    for (Position position : positions) {
      // Initialize only open equities and ETFs. Existing user overrides are never overwritten.
      for (String side : List.of("LOSS", "PROFIT")) {
        jdbc.update(
            """
            INSERT INTO investory.position_price_alert
              (portfolio_id, asset_id, side, action_text)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (portfolio_id, asset_id, side) DO NOTHING
            """,
            portfolioId,
            position.assetId(),
            side,
            defaultAction(side));
      }
      if (position.price() == null
          || position.price().signum() <= 0
          || position.averageCost() == null
          || position.averageCost().signum() <= 0
          || position.quoteDate() == null
          || position.quoteDate().isAfter(LocalDate.now(clock.withZone(WARSAW)))
          || position.quoteDate().isBefore(LocalDate.now(clock.withZone(WARSAW)).minusDays(5))) {
        continue; // Missing/stale source data never triggers or re-arms a rule.
      }
      for (Rule rule : rules(portfolioId, position.assetId())) {
        if (rule.enabled()) evaluateRule(position, rule);
      }
    }
  }

  private void evaluateRule(Position position, Rule rule) {
    BigDecimal reference = position.averageCost();
    BigDecimal target = target(reference, rule.side(), rule.mode(), rule.threshold());
    BigDecimal price = position.price();
    boolean breached =
        "PROFIT".equals(rule.side()) ? price.compareTo(target) >= 0 : price.compareTo(target) <= 0;
    boolean referenceChanged =
        rule.lastReference() == null || rule.lastReference().compareTo(reference) != 0;
    boolean active = rule.active();
    long sequence = rule.sequence();
    boolean fire = false;
    if (!rule.initialized() || referenceChanged) {
      // Baseline existing positions on the first evaluation; never send initial spam.
      active = breached;
    } else if (!active && breached) {
      active = true;
      fire = true;
      sequence++;
    } else if (active && isRearmed(price, target, reference, rule.side())) {
      active = false;
    }
    jdbc.update(
        """
        UPDATE investory.position_price_alert
        SET initialized = true, active = ?, incident_sequence = ?,
            last_reference_price = ?, last_observed_price = ?,
            last_observed_at = ?, last_alert_at = CASE WHEN ? THEN ? ELSE last_alert_at END
        WHERE id = ?
        """,
        active,
        sequence,
        reference,
        price,
        clock.instant(),
        fire,
        clock.instant(),
        rule.id());
    if (fire) {
      String move = "PROFIT".equals(rule.side()) ? "take-profit" : "downside";
      BigDecimal percent =
          price.subtract(reference).multiply(HUNDRED).divide(reference, MathContext.DECIMAL64);
      String message =
          String.format(
              Locale.US,
              "%s %s alert (%s %s)\nAverage cost: %s %s\nLatest price: %s %s (%+.1f%%)\nAction: %s\nQuote date: %s\nNo orders placed.\n%s/portfolios/%d/dashboard",
              position.symbol(),
              move,
              rule.mode(),
              rule.threshold().stripTrailingZeros().toPlainString(),
              reference.stripTrailingZeros().toPlainString(),
              position.currency(),
              price.stripTrailingZeros().toPlainString(),
              position.currency(),
              percent.doubleValue(),
              rule.action(),
              position.quoteDate(),
              baseUrl.replaceAll("/+$", ""),
              position.portfolioId());
      publisher.publish(
          new NotificationCandidate(
              NotificationEventType.THRESHOLD_ALERT,
              "LOSS".equals(rule.side()) ? NotificationSeverity.WARNING : NotificationSeverity.INFO,
              position.portfolioId(),
              "POSITION_PRICE_ALERT",
              Long.toString(rule.id()),
              "POSITION_PRICE_ALERT:" + rule.id() + ":" + sequence,
              position.symbol() + " price alert",
              Map.of(
                  "message",
                  message,
                  "rule",
                  "POSITION_PRICE_ALERT",
                  "symbol",
                  position.symbol(),
                  "side",
                  rule.side()),
              clock.instant()));
    }
  }

  static BigDecimal target(BigDecimal cost, String side, String mode, BigDecimal threshold) {
    if ("PRICE".equals(mode)) return threshold;
    BigDecimal factor =
        "PROFIT".equals(side) ? HUNDRED.add(threshold) : HUNDRED.subtract(threshold);
    return cost.multiply(factor).divide(HUNDRED, MathContext.DECIMAL64);
  }

  static boolean isRearmed(BigDecimal price, BigDecimal target, BigDecimal cost, String side) {
    BigDecimal margin = cost.multiply(REARM_PERCENT).divide(HUNDRED, MathContext.DECIMAL64);
    return "PROFIT".equals(side)
        ? price.compareTo(target.subtract(margin)) <= 0
        : price.compareTo(target.add(margin)) >= 0;
  }
}
