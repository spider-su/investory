package com.smartbox.investory.integrations.market.radar;

import com.smartbox.investory.marketradar.domain.RadarOutcome;
import com.smartbox.investory.marketradar.domain.RadarSnapshot;
import com.smartbox.investory.marketradar.domain.RadarState;
import com.smartbox.investory.marketradar.domain.RadarValidationObservation;
import com.smartbox.investory.marketradar.domain.RadarValidationStats;
import com.smartbox.investory.marketradar.port.RadarOutcomeStore;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRadarOutcomeStore implements RadarOutcomeStore {
  private final JdbcTemplate jdbc;

  public JdbcRadarOutcomeStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<RadarSnapshot> unevaluated(int horizonDays, LocalDate cutoff) {
    return jdbc.query(
        """
        select s.symbol, s.observed_on, s.state, s.close_price, s.return_20d, s.return_60d,
          s.relative_volume_20d, s.distance_sma50, s.rsi14, s.reasons
        from market_radar_snapshot s
        where s.observed_on <= ?
          and not exists (
            select 1 from market_radar_outcome o
            where o.symbol = s.symbol and o.signal_date = s.observed_on and o.horizon_days = ?
          )
        order by s.observed_on
        """,
        (rs, row) ->
            new RadarSnapshot(
                rs.getString("symbol"),
                rs.getDate("observed_on").toLocalDate(),
                RadarState.valueOf(rs.getString("state")),
                rs.getDouble("close_price"),
                nullableDouble(rs, "return_20d"),
                nullableDouble(rs, "return_60d"),
                nullableDouble(rs, "relative_volume_20d"),
                nullableDouble(rs, "distance_sma50"),
                nullableDouble(rs, "rsi14"),
                rs.getString("reasons") == null || rs.getString("reasons").isBlank()
                    ? List.of()
                    : Arrays.asList(rs.getString("reasons").split("\n"))),
        Date.valueOf(cutoff),
        horizonDays);
  }

  @Override
  public void save(RadarOutcome outcome) {
    jdbc.update(
        """
        insert into market_radar_outcome
          (symbol, signal_date, horizon_days, evaluated_on, symbol_return, benchmark,
           benchmark_return, excess_return)
        values (?, ?, ?, ?, ?, ?, ?, ?)
        on conflict (symbol, signal_date, horizon_days) do nothing
        """,
        outcome.symbol(),
        Date.valueOf(outcome.signalDate()),
        outcome.horizonDays(),
        Date.valueOf(outcome.evaluatedOn()),
        outcome.symbolReturn(),
        outcome.benchmark(),
        outcome.benchmarkReturn(),
        outcome.excessReturn());
  }

  @Override
  public List<RadarOutcome> outcomes(String symbol, int limit) {
    return jdbc.query(
        """
        select symbol, signal_date, horizon_days, evaluated_on, symbol_return, benchmark,
          benchmark_return, excess_return
        from market_radar_outcome
        where symbol = ?
        order by signal_date desc, horizon_days
        limit ?
        """,
        (rs, row) ->
            new RadarOutcome(
                rs.getString("symbol"),
                rs.getDate("signal_date").toLocalDate(),
                rs.getInt("horizon_days"),
                rs.getDate("evaluated_on").toLocalDate(),
                rs.getDouble("symbol_return"),
                rs.getString("benchmark"),
                nullableDouble(rs, "benchmark_return"),
                nullableDouble(rs, "excess_return")),
        symbol,
        limit);
  }

  @Override
  public List<RadarValidationStats> validationStats() {
    return jdbc.query(
        """
        select s.state, o.horizon_days, count(*) as observations,
          avg(o.symbol_return) as average_return,
          percentile_cont(0.5) within group (order by o.symbol_return) as median_return,
          avg(o.excess_return) as average_excess_return,
          percentile_cont(0.5) within group (order by o.excess_return) as median_excess_return,
          avg(case when o.symbol_return > 0 then 1.0 else 0.0 end) as positive_rate,
          avg(case when o.excess_return > 0 then 1.0 else 0.0 end) as outperform_rate
        from market_radar_outcome o
        join market_radar_snapshot s
          on s.symbol = o.symbol and s.observed_on = o.signal_date
        group by s.state, o.horizon_days
        order by o.horizon_days, s.state
        """,
        (rs, row) ->
            new RadarValidationStats(
                RadarState.valueOf(rs.getString("state")),
                rs.getInt("horizon_days"),
                rs.getLong("observations"),
                nullableDouble(rs, "average_return"),
                nullableDouble(rs, "median_return"),
                nullableDouble(rs, "average_excess_return"),
                nullableDouble(rs, "median_excess_return"),
                nullableDouble(rs, "positive_rate"),
                nullableDouble(rs, "outperform_rate")));
  }

  @Override
  public List<RadarValidationObservation> validationObservations(
      RadarState state, int horizonDays, int limit) {
    return jdbc.query(
        """
        select o.symbol, o.signal_date, s.state, o.horizon_days, o.symbol_return,
          o.benchmark_return, o.excess_return
        from market_radar_outcome o
        join market_radar_snapshot s
          on s.symbol = o.symbol and s.observed_on = o.signal_date
        where s.state = ? and o.horizon_days = ?
        order by o.signal_date desc, o.symbol
        limit ?
        """,
        (rs, row) ->
            new RadarValidationObservation(
                rs.getString("symbol"),
                rs.getDate("signal_date").toLocalDate(),
                RadarState.valueOf(rs.getString("state")),
                rs.getInt("horizon_days"),
                rs.getDouble("symbol_return"),
                nullableDouble(rs, "benchmark_return"),
                nullableDouble(rs, "excess_return")),
        state.name(),
        horizonDays,
        limit);
  }

  private Double nullableDouble(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
    double value = rs.getDouble(column);
    return rs.wasNull() ? null : value;
  }
}
