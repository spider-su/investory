package com.smartbox.investory.integrations.market.radar;

import com.smartbox.investory.marketradar.domain.ThemeSnapshot;
import com.smartbox.investory.marketradar.domain.ThemeState;
import com.smartbox.investory.marketradar.port.ThemeSnapshotStore;
import java.sql.Date;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcThemeSnapshotStore implements ThemeSnapshotStore {
  private final JdbcTemplate jdbc;

  public JdbcThemeSnapshotStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void save(ThemeSnapshot s) {
    jdbc.update(
        """
        insert into market_radar_theme_snapshot
          (theme, proxy_symbol, observed_on, state, member_count, breadth_above_sma50,
           breadth_outperforming_benchmark, proxy_return_20d, benchmark_return_20d,
           relative_strength_20d)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        on conflict (theme, observed_on) do update set
          proxy_symbol = excluded.proxy_symbol,
          state = excluded.state,
          member_count = excluded.member_count,
          breadth_above_sma50 = excluded.breadth_above_sma50,
          breadth_outperforming_benchmark = excluded.breadth_outperforming_benchmark,
          proxy_return_20d = excluded.proxy_return_20d,
          benchmark_return_20d = excluded.benchmark_return_20d,
          relative_strength_20d = excluded.relative_strength_20d
        """,
        s.theme(),
        s.proxySymbol(),
        Date.valueOf(s.date()),
        s.state().name(),
        s.memberCount(),
        s.breadthAboveSma50(),
        s.breadthOutperformingBenchmark(),
        s.proxyReturn20d(),
        s.benchmarkReturn20d(),
        s.relativeStrength20d());
  }

  @Override
  public List<ThemeSnapshot> latest() {
    return jdbc.query(
        """
        select distinct on (theme) theme, proxy_symbol, observed_on, state, member_count,
          breadth_above_sma50, breadth_outperforming_benchmark, proxy_return_20d,
          benchmark_return_20d, relative_strength_20d
        from market_radar_theme_snapshot
        order by theme, observed_on desc
        """,
        (rs, row) -> map(rs));
  }

  @Override
  public List<ThemeSnapshot> history(String theme, int limit) {
    return jdbc.query(
        """
        select theme, proxy_symbol, observed_on, state, member_count,
          breadth_above_sma50, breadth_outperforming_benchmark, proxy_return_20d,
          benchmark_return_20d, relative_strength_20d
        from market_radar_theme_snapshot
        where theme = ?
        order by observed_on desc
        limit ?
        """,
        (rs, row) -> map(rs),
        theme,
        limit);
  }

  private ThemeSnapshot map(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new ThemeSnapshot(
        rs.getString("theme"),
        rs.getString("proxy_symbol"),
        rs.getDate("observed_on").toLocalDate(),
        ThemeState.valueOf(rs.getString("state")),
        rs.getInt("member_count"),
        rs.getDouble("breadth_above_sma50"),
        rs.getDouble("breadth_outperforming_benchmark"),
        rs.getDouble("proxy_return_20d"),
        rs.getDouble("benchmark_return_20d"),
        rs.getDouble("relative_strength_20d"));
  }
}
