package com.smartbox.investory.integrations.market.radar;

import com.smartbox.investory.marketradar.domain.RadarSnapshot;
import com.smartbox.investory.marketradar.domain.RadarState;
import com.smartbox.investory.marketradar.port.RadarSnapshotStore;
import java.sql.Date;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRadarSnapshotStore implements RadarSnapshotStore {
  private final JdbcTemplate jdbc;

  public JdbcRadarSnapshotStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void save(RadarSnapshot s) {
    jdbc.update(
        """
        insert into market_radar_snapshot
          (symbol, observed_on, state, close_price, return_20d, return_60d, relative_volume_20d,
           distance_sma50, rsi14, reasons)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        on conflict (symbol, observed_on) do update set
          state=excluded.state, close_price=excluded.close_price, return_20d=excluded.return_20d,
          return_60d=excluded.return_60d, relative_volume_20d=excluded.relative_volume_20d,
          distance_sma50=excluded.distance_sma50, rsi14=excluded.rsi14, reasons=excluded.reasons
        """,
        s.symbol(), Date.valueOf(s.date()), s.state().name(), s.close(), s.return20d(), s.return60d(),
        s.relativeVolume20d(), s.distanceSma50(), s.rsi14(), String.join("\n", s.reasons()));
  }

  @Override
  public List<RadarSnapshot> latest() {
    return jdbc.query(
        """
        select distinct on (symbol) symbol, observed_on, state, close_price, return_20d, return_60d,
          relative_volume_20d, distance_sma50, rsi14, reasons
        from market_radar_snapshot
        order by symbol, observed_on desc
        """,
        (rs, row) -> mapSnapshot(rs));
  }

  @Override
  public List<RadarSnapshot> history(String symbol, int limit) {
    return jdbc.query(
        """
        select symbol, observed_on, state, close_price, return_20d, return_60d,
          relative_volume_20d, distance_sma50, rsi14, reasons
        from market_radar_snapshot
        where symbol = ?
        order by observed_on desc
        limit ?
        """,
        (rs, row) -> mapSnapshot(rs),
        symbol,
        limit);
  }

  @Override
  public java.util.Optional<RadarSnapshot> previous(String symbol, java.time.LocalDate before) {
    List<RadarSnapshot> rows =
        jdbc.query(
            """
            select symbol, observed_on, state, close_price, return_20d, return_60d,
              relative_volume_20d, distance_sma50, rsi14, reasons
            from market_radar_snapshot
            where symbol = ? and observed_on < ?
            order by observed_on desc
            limit 1
            """,
            (rs, row) -> mapSnapshot(rs),
            symbol,
            Date.valueOf(before));
    return rows.stream().findFirst();
  }

  private RadarSnapshot mapSnapshot(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new RadarSnapshot(
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
            : Arrays.asList(rs.getString("reasons").split("\\n")));
  }

  private Double nullableDouble(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
    double value = rs.getDouble(column);
    return rs.wasNull() ? null : value;
  }
}
