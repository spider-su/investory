package com.smartbox.investory.integrations.market.radar;

import com.smartbox.investory.marketradar.domain.StrategistForecast;
import com.smartbox.investory.marketradar.port.StrategistForecastStore;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcStrategistForecastStore implements StrategistForecastStore {
  private final JdbcTemplate jdbc;

  public JdbcStrategistForecastStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<ForecastObservation> observations(LocalDate targetDate) {
    return jdbc.query(
        """
        select o.id, i.institution_key, i.display_name, o.strategist, o.published_at,
          o.target_date, o.target_level, s.source_url, o.source_title, s.source_type,
          o.observed_at, o.quality, o.status
        from market_radar_forecast_observation o
        join market_radar_forecast_institution i on i.id = o.institution_id
        join market_radar_forecast_source s on s.id = o.source_id
        where o.target_date = ?
        order by o.published_at, o.observed_at, o.id
        """,
        (rs, row) -> {
          var forecast =
              new StrategistForecast(
                  rs.getString("institution_key"),
                  rs.getString("display_name"),
                  rs.getString("strategist"),
                  rs.getTimestamp("published_at").toInstant(),
                  rs.getDate("target_date").toLocalDate(),
                  rs.getBigDecimal("target_level"),
                  rs.getString("source_url"),
                  rs.getString("source_title"),
                  StrategistForecast.SourceType.valueOf(rs.getString("source_type")),
                  rs.getTimestamp("observed_at").toInstant());
          return new ForecastObservation(
              rs.getLong("id"), forecast, rs.getString("quality"), rs.getString("status"));
        },
        java.sql.Date.valueOf(targetDate));
  }

  @Override
  @Transactional
  public int insertAll(List<StrategistForecast> forecasts) {
    return StrategistForecastStore.super.insertAll(forecasts);
  }

  @Override
  @Transactional
  public boolean insert(StrategistForecast forecast) {
    Long institutionId =
        jdbc.queryForObject(
            """
        insert into market_radar_forecast_institution (institution_key, display_name)
        values (?, ?)
        on conflict (institution_key) do update set institution_key = excluded.institution_key
        returning id
        """,
            Long.class,
            forecast.institutionKey(),
            forecast.institutionName());
    String existingName =
        jdbc.queryForObject(
            "select display_name from market_radar_forecast_institution where id = ?",
            String.class,
            institutionId);
    if (!forecast.institutionName().equals(existingName))
      throw new IllegalArgumentException(
          "Institution key already registered with different attribution");
    Long sourceId =
        jdbc.queryForObject(
            """
        insert into market_radar_forecast_source (source_url, source_title, source_type)
        values (?, ?, ?)
        on conflict (source_url) do update set source_url = excluded.source_url
        returning id
        """,
            Long.class,
            forecast.sourceUrl(),
            forecast.sourceTitle(),
            forecast.sourceType().name());
    String existingType =
        jdbc.queryForObject(
            "select source_type from market_radar_forecast_source where id = ?",
            String.class,
            sourceId);
    if (!forecast.sourceType().name().equals(existingType)) {
      throw new IllegalArgumentException(
          "Source URL already registered with different provenance metadata");
    }
    Long supersedes =
        jdbc.query(
            """
        select id from market_radar_forecast_observation
        where institution_id = ? and target_date = ? and published_at < ?
        order by published_at desc, observed_at desc, id desc limit 1
        """,
            rs -> rs.next() ? rs.getLong(1) : null,
            institutionId,
            forecast.targetDate(),
            Timestamp.from(forecast.publishedAt()));
    int inserted =
        jdbc.update(
            """
        insert into market_radar_forecast_observation
          (institution_id, source_id, strategist, published_at, target_date, target_level, observed_at, supersedes_id, quality, source_title)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        on conflict (source_id, institution_id, target_date, published_at) do nothing
        """,
            institutionId,
            sourceId,
            forecast.strategist(),
            Timestamp.from(forecast.publishedAt()),
            forecast.targetDate(),
            forecast.targetLevel(),
            Timestamp.from(forecast.observedAt()),
            supersedes,
            forecast.sourceType() == StrategistForecast.SourceType.PRIMARY
                ? "VERIFIED"
                : "SECONDARY",
            forecast.sourceTitle());
    if (inserted == 0) {
      Boolean same =
          jdbc.queryForObject(
              """
        select target_level = ? and strategist is not distinct from ? and source_title = ?
        from market_radar_forecast_observation
        where source_id = ? and institution_id = ? and target_date = ? and published_at = ?
        """,
              Boolean.class,
              forecast.targetLevel(),
              forecast.strategist(),
              forecast.sourceTitle(),
              sourceId,
              institutionId,
              forecast.targetDate(),
              Timestamp.from(forecast.publishedAt()));
      if (!Boolean.TRUE.equals(same))
        throw new IllegalArgumentException(
            "Conflicting target, strategist or title for existing source observation");
    }
    return inserted == 1;
  }
}
