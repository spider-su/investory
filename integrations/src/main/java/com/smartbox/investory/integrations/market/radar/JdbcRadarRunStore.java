package com.smartbox.investory.integrations.market.radar;

import com.smartbox.investory.marketradar.domain.RadarRunStatus;
import com.smartbox.investory.marketradar.domain.RadarRunSummary;
import com.smartbox.investory.marketradar.port.RadarRunStore;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRadarRunStore implements RadarRunStore {
  private final JdbcTemplate jdbc;

  public JdbcRadarRunStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void save(RadarRunSummary run) {
    jdbc.update(
        """
        insert into market_radar_run
          (id, started_at, completed_at, status, universe_size, attempted, stored, no_data, failed,
           interesting, normal_count, emerging_count, trending_count, hot_count, extended_count,
           cooling_count, outcomes_evaluated, duration_ms, error)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        on conflict (id) do update set
          completed_at=excluded.completed_at, status=excluded.status, attempted=excluded.attempted,
          stored=excluded.stored, no_data=excluded.no_data, failed=excluded.failed,
          interesting=excluded.interesting, normal_count=excluded.normal_count,
          emerging_count=excluded.emerging_count, trending_count=excluded.trending_count,
          hot_count=excluded.hot_count, extended_count=excluded.extended_count,
          cooling_count=excluded.cooling_count, outcomes_evaluated=excluded.outcomes_evaluated,
          duration_ms=excluded.duration_ms, error=excluded.error
        """,
        run.id(),
        Timestamp.from(run.startedAt()),
        run.completedAt() == null ? null : Timestamp.from(run.completedAt()),
        run.status().name(),
        run.universeSize(),
        run.attempted(),
        run.stored(),
        run.noData(),
        run.failed(),
        run.interesting(),
        run.normal(),
        run.emerging(),
        run.trending(),
        run.hot(),
        run.extended(),
        run.cooling(),
        run.outcomesEvaluated(),
        run.durationMs(),
        run.error());
  }

  @Override
  public List<RadarRunSummary> recent(int limit) {
    return jdbc.query(
        """
        select id, started_at, completed_at, status, universe_size, attempted, stored, no_data,
          failed, interesting, normal_count, emerging_count, trending_count, hot_count,
          extended_count, cooling_count, outcomes_evaluated, duration_ms, error
        from market_radar_run
        order by started_at desc
        limit ?
        """,
        (rs, row) ->
            new RadarRunSummary(
                rs.getObject("id", java.util.UUID.class),
                rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("completed_at") == null
                    ? null
                    : rs.getTimestamp("completed_at").toInstant(),
                RadarRunStatus.valueOf(rs.getString("status")),
                rs.getInt("universe_size"),
                rs.getInt("attempted"),
                rs.getInt("stored"),
                rs.getInt("no_data"),
                rs.getInt("failed"),
                rs.getInt("interesting"),
                rs.getInt("normal_count"),
                rs.getInt("emerging_count"),
                rs.getInt("trending_count"),
                rs.getInt("hot_count"),
                rs.getInt("extended_count"),
                rs.getInt("cooling_count"),
                rs.getInt("outcomes_evaluated"),
                rs.getLong("duration_ms"),
                rs.getString("error")),
        limit);
  }
}
