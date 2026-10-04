package com.smartbox.investory.integrations.market.radar;

import com.smartbox.investory.marketradar.domain.MediaAttentionSignal;
import com.smartbox.investory.marketradar.domain.MediaObservation;
import com.smartbox.investory.marketradar.domain.MediaSourceType;
import com.smartbox.investory.marketradar.domain.OpinionStance;
import com.smartbox.investory.marketradar.port.MediaObservationStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcMediaObservationStore implements MediaObservationStore {
  private final JdbcTemplate jdbc;

  public JdbcMediaObservationStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void save(MediaObservation observation) {
    Long id =
        jdbc.queryForObject(
            """
            insert into market_radar_media_observation
              (external_id, source_type, source, author, headline, url, published_at, stance)
            values (?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (external_id) do update set
              source_type = excluded.source_type,
              source = excluded.source,
              author = excluded.author,
              headline = excluded.headline,
              url = excluded.url,
              published_at = excluded.published_at,
              stance = excluded.stance
            returning id
            """,
            Long.class,
            observation.externalId(),
            observation.sourceType().name(),
            observation.source(),
            observation.author(),
            observation.headline(),
            observation.url(),
            Timestamp.from(observation.publishedAt()),
            observation.stance().name());

    for (String symbol : observation.symbols()) {
      jdbc.update(
          """
          insert into market_radar_media_symbol (observation_id, symbol)
          values (?, ?)
          on conflict do nothing
          """,
          id,
          symbol);
    }
  }

  @Override
  public List<MediaAttentionSignal> attentionSignals(Instant now, int limit) {
    Instant currentStart = now.minus(java.time.Duration.ofDays(7));
    Instant previousStart = now.minus(java.time.Duration.ofDays(14));
    return jdbc.query(
        """
        select ms.symbol,
          count(*) filter (where mo.published_at >= ?) as mentions_7d,
          count(*) filter (where mo.published_at >= ? and mo.published_at < ?) as previous_mentions_7d,
          count(distinct mo.source) filter (where mo.published_at >= ?) as independent_sources_7d,
          count(*) filter (where mo.published_at >= ? and mo.stance = 'BULLISH') as bullish_7d,
          count(*) filter (where mo.published_at >= ? and mo.stance = 'NEUTRAL') as neutral_7d,
          count(*) filter (where mo.published_at >= ? and mo.stance = 'BEARISH') as bearish_7d
        from market_radar_media_symbol ms
        join market_radar_media_observation mo on mo.id = ms.observation_id
        where mo.published_at >= ?
        group by ms.symbol
        order by mentions_7d desc, independent_sources_7d desc, ms.symbol
        limit ?
        """,
        (rs, row) -> {
          long current = rs.getLong("mentions_7d");
          long previous = rs.getLong("previous_mentions_7d");
          double ratio =
              previous == 0 ? (current > 0 ? current : 0.0) : (double) current / previous;
          return new MediaAttentionSignal(
              rs.getString("symbol"),
              current,
              previous,
              rs.getLong("independent_sources_7d"),
              rs.getLong("bullish_7d"),
              rs.getLong("neutral_7d"),
              rs.getLong("bearish_7d"),
              ratio);
        },
        Timestamp.from(currentStart),
        Timestamp.from(previousStart),
        Timestamp.from(currentStart),
        Timestamp.from(currentStart),
        Timestamp.from(currentStart),
        Timestamp.from(currentStart),
        Timestamp.from(currentStart),
        Timestamp.from(previousStart),
        limit);
  }

  @Override
  public List<MediaObservation> recent(String symbol, int limit) {
    return jdbc.query(
        """
        select mo.external_id, mo.source_type, mo.source, mo.author, mo.headline, mo.url,
          mo.published_at, mo.stance
        from market_radar_media_observation mo
        join market_radar_media_symbol ms on ms.observation_id = mo.id
        where ms.symbol = ?
        order by mo.published_at desc
        limit ?
        """,
        (rs, row) ->
            new MediaObservation(
                rs.getString("external_id"),
                MediaSourceType.valueOf(rs.getString("source_type")),
                rs.getString("source"),
                rs.getString("author"),
                rs.getString("headline"),
                rs.getString("url"),
                rs.getTimestamp("published_at").toInstant(),
                List.of(symbol),
                OpinionStance.valueOf(rs.getString("stance"))),
        symbol,
        limit);
  }
}
