package com.smartbox.investory.investment.infrastructure.persistence.portfolio;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PortfolioDataQualityRepository {
  private final NamedParameterJdbcTemplate jdbcTemplate;

  public List<PortfolioDataQualitySnapshotRow> findSnapshot(Long portfolioId) {
    return jdbcTemplate.query(
        "WITH p AS (SELECT COUNT(*) total, "
            + "COUNT(*) FILTER (WHERE a.market_price IS NOT NULL) priced, "
            + "COUNT(*) FILTER (WHERE a.market_price IS NULL) missing "
            + "FROM investory.positions x JOIN investory.accounts xa ON xa.id=x.account_id LEFT JOIN investory.assets a ON a.id=x.asset_id WHERE xa.portfolio_id=:portfolioId AND x.close_time IS NULL AND COALESCE(x.volume,0)<>0), "
            + "s AS (SELECT COUNT(*) total_accounts, COUNT(*) FILTER (WHERE ast.account_id IS NOT NULL) reconciled FROM investory.app_v_account_statistics ast JOIN investory.accounts aa ON aa.id=ast.account_id WHERE aa.portfolio_id=:portfolioId), "
            + "d AS (SELECT MAX(price_updated_at) latest_price FROM investory.assets), "
            + "f AS (SELECT MAX(rate_date) latest_fx FROM investory.exchange_rates WHERE purpose = 'VALUATION'), "
            + "i AS (SELECT MAX(finished_at) latest_import FROM investory.import_history WHERE status='COMPLETED' AND portfolio_id=:portfolioId), "
            + "r AS (SELECT MAX(ad.updated_at) latest_refresh FROM investory.account_daily ad JOIN investory.accounts aa ON aa.id=ad.account_id WHERE aa.portfolio_id=:portfolioId) "
            + "SELECT CASE WHEN p.missing>0 OR s.reconciled<s.total_accounts THEN 'CRITICAL' WHEN p.total>0 AND p.priced<p.total THEN 'REVIEW' ELSE 'HEALTHY' END state, "
            + "s.reconciled reconciled_accounts,s.total_accounts,p.priced priced_open_positions,p.total total_open_positions,p.missing missing_price_open_positions,"
            + "0 stale_price_open_positions,0 reconstructed_price_open_positions,0 missing_fx_open_positions,0 stale_fx_open_positions,0 missing_cost_basis_positions,0 unreconciled_positions,"
            + "i.latest_import latest_import_at,i.latest_import latest_completed_import_at,d.latest_price latest_price_date,f.latest_fx latest_fx_date,r.latest_refresh latest_account_refresh_at "
            + "FROM p CROSS JOIN s CROSS JOIN d CROSS JOIN f CROSS JOIN i CROSS JOIN r",
        new MapSqlParameterSource("portfolioId", portfolioId),
        (rs, rowNum) ->
            new PortfolioDataQualitySnapshotRow(
                rs.getString("state"),
                rs.getLong("reconciled_accounts"),
                rs.getLong("total_accounts"),
                rs.getLong("priced_open_positions"),
                rs.getLong("total_open_positions"),
                rs.getLong("missing_price_open_positions"),
                rs.getLong("stale_price_open_positions"),
                rs.getLong("reconstructed_price_open_positions"),
                rs.getLong("missing_fx_open_positions"),
                rs.getLong("stale_fx_open_positions"),
                rs.getLong("missing_cost_basis_positions"),
                rs.getLong("unreconciled_positions"),
                offsetDateTime(rs, "latest_import_at"),
                offsetDateTime(rs, "latest_completed_import_at"),
                localDate(rs, "latest_price_date"),
                localDate(rs, "latest_fx_date"),
                offsetDateTime(rs, "latest_account_refresh_at")));
  }

  public List<PortfolioDataQualityIssueRow> findIssues(Long portfolioId) {
    return jdbcTemplate.query(
        "SELECT issue_type, account_id, asset_id, issue_code, price_age_days, "
            + "selected_price_date, price_currency, price_quality, price_origin, reconstruction_status, selected_price_history_id "
            + "FROM investory.recon_v_portfolio_data_quality_issue WHERE portfolio_id=:portfolioId ORDER BY issue_code, account_id, asset_id LIMIT 100",
        new MapSqlParameterSource("portfolioId", portfolioId),
        (rs, rowNum) ->
            new PortfolioDataQualityIssueRow(
                rs.getString("issue_type"),
                stringValue(rs, "account_id"),
                stringValue(rs, "asset_id"),
                rs.getString("issue_code"),
                (Integer) rs.getObject("price_age_days"),
                localDate(rs, "selected_price_date"),
                rs.getString("price_currency"),
                rs.getString("price_quality"),
                rs.getString("price_origin"),
                rs.getString("reconstruction_status"),
                (Long) rs.getObject("selected_price_history_id")));
  }

  private static String stringValue(ResultSet rs, String column) throws SQLException {
    Object value = rs.getObject(column);
    return value == null ? null : value.toString();
  }

  private static LocalDate localDate(ResultSet rs, String column) throws SQLException {
    Object value = rs.getObject(column);
    return switch (value) {
      case null -> null;
      case LocalDate date -> date;
      case java.sql.Date date -> date.toLocalDate();
      case java.sql.Timestamp timestamp -> timestamp.toLocalDateTime().toLocalDate();
      default ->
          throw new SQLException("Unexpected date type for " + column + ": " + value.getClass());
    };
  }

  private static OffsetDateTime offsetDateTime(ResultSet rs, String column) throws SQLException {
    Object value = rs.getObject(column);
    return switch (value) {
      case null -> null;
      case OffsetDateTime dateTime -> dateTime;
      case java.sql.Timestamp timestamp -> timestamp.toInstant().atOffset(ZoneOffset.UTC);
      default ->
          throw new SQLException(
              "Unexpected timestamp type for " + column + ": " + value.getClass());
    };
  }
}
