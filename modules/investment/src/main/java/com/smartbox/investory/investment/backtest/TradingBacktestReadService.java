package com.smartbox.investory.investment.backtest;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only adapter from the imported position ledger and trusted persisted daily OHLC rows. */
@Service
public class TradingBacktestReadService {
  private static final List<String> TRUSTED_SOURCES =
      List.of("STOOQ", "YAHOO_FINANCE", "TWELVE_DATA", "MANUAL_ACCEPTED");

  private final NamedParameterJdbcTemplate jdbc;

  public TradingBacktestReadService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record Scope(Set<Long> accountIds, String provider, String accountCurrency) {
    public Scope {
      accountIds = Set.copyOf(accountIds);
      if (accountIds.isEmpty()) throw new IllegalArgumentException("at least one account required");
      if (provider == null || provider.isBlank())
        throw new IllegalArgumentException("provider required");
      if (accountCurrency == null || accountCurrency.isBlank())
        throw new IllegalArgumentException("account currency required");
    }
  }

  public record Account(long id, String name, String provider, String currency) {}

  public record Dataset(
      List<Account> accounts,
      List<TradingStrategyBacktester.Execution> executions,
      Map<TradingStrategyBacktester.CampaignKey, List<TradingStrategyBacktester.DailyBar>> bars,
      BigDecimal exactActualProfit,
      BigDecimal passThroughProfit,
      List<String> instrumentsWithoutTrustedBars,
      int currencyExcludedTrades,
      int directionExcludedTrades,
      LocalDate dataThrough) {
    public Dataset {
      accounts = List.copyOf(accounts);
      executions = List.copyOf(executions);
      bars = Map.copyOf(bars);
      instrumentsWithoutTrustedBars = List.copyOf(instrumentsWithoutTrustedBars);
    }
  }

  private record PositionRow(
      long id,
      long accountId,
      long assetId,
      String symbol,
      String operation,
      String priceCurrency,
      String profitCurrency,
      OffsetDateTime openTime,
      BigDecimal quantity,
      BigDecimal openPrice,
      OffsetDateTime closeTime,
      BigDecimal closePrice,
      BigDecimal profit) {}

  private record PriceRow(
      long assetId,
      String symbol,
      LocalDate date,
      BigDecimal open,
      BigDecimal high,
      BigDecimal low,
      BigDecimal close,
      BigDecimal scale) {}

  /**
   * Reads a fixed account/year scope. Strategy execution is limited to horizon; later closes are
   * retained only as winner-destruction control outcomes. No provider calls or writes occur here.
   */
  @Transactional(readOnly = true)
  public Dataset read(Scope scope, LocalDate from, LocalDate horizon) {
    if (from == null || horizon == null || from.isAfter(horizon))
      throw new IllegalArgumentException("invalid backtest dates");
    MapSqlParameterSource args =
        new MapSqlParameterSource()
            .addValue("accountIds", scope.accountIds())
            .addValue("provider", scope.provider())
            .addValue("accountCurrency", scope.accountCurrency())
            .addValue("from", from)
            .addValue("through", horizon);
    List<Account> accounts =
        jdbc.query(
            """
            select id, name, provider, currency
            from investory.accounts
            where id in (:accountIds)
            order by id
            """,
            args,
            (rs, row) ->
                new Account(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("provider"),
                    rs.getString("currency")));
    if (accounts.size() != scope.accountIds().size()
        || accounts.stream()
            .anyMatch(
                a ->
                    !scope.provider().equals(a.provider())
                        || !scope.accountCurrency().equals(a.currency())))
      throw new IllegalArgumentException(
          "account IDs do not match the requested provider/currency scope");

    List<PositionRow> positions =
        jdbc.query(
            """
            select p.id, p.account_id, p.asset_id, a.symbol, p.operation::text as operation,
                   p.price_currency, p.profit_currency, p.open_time, p.volume, p.open_price,
                   p.close_time, p.close_price, p.profit
            from investory.positions p
            join investory.assets a on a.id = p.asset_id
            where p.account_id in (:accountIds)
              and p.open_time::date <= :through
              and (p.close_time is null or p.close_time::date >= :from)
            order by p.open_time, p.account_id, p.asset_id, p.id
            """,
            args,
            POSITION_MAPPER);

    BigDecimal baseline =
        jdbc.queryForObject(
            """
            select coalesce(sum(p.profit), 0)
            from investory.positions p
            where p.account_id in (:accountIds)
              and p.profit_currency = 'USD'
              and p.close_time::date between :from and :through
            """,
            args,
            BigDecimal.class);
    List<TradingStrategyBacktester.Execution> executions = new ArrayList<>();
    BigDecimal passThrough = BigDecimal.ZERO;
    int currencyExcluded = 0;
    int directionExcluded = 0;
    Set<Long> assets = new LinkedHashSet<>();
    for (PositionRow row : positions) {
      boolean inYear =
          row.closeTime() != null
              && !row.closeTime().toLocalDate().isBefore(from)
              && !row.closeTime().toLocalDate().isAfter(horizon);
      if (!"USD".equals(row.priceCurrency()) || !"USD".equals(row.profitCurrency())) {
        currencyExcluded++;
        if (inYear && !"USD".equals(row.profitCurrency()))
          throw new IllegalStateException(
              "cannot aggregate realized P/L across profit currencies: " + row.profitCurrency());
        if (inYear && row.profit() != null) passThrough = passThrough.add(row.profit());
        continue;
      }
      if (!"BUY".equals(row.operation())) {
        directionExcluded++;
        if (inYear && row.profit() != null) passThrough = passThrough.add(row.profit());
        continue;
      }
      assets.add(row.assetId());
      var key = new TradingStrategyBacktester.CampaignKey(row.accountId(), row.assetId(), "USD");
      String lotId = Long.toString(row.id());
      executions.add(
          new TradingStrategyBacktester.Execution(
              key,
              row.symbol(),
              lotId,
              TradingStrategyBacktester.Side.BUY,
              row.openTime().toZonedDateTime(),
              row.quantity(),
              row.openPrice(),
              null));
      if (row.closeTime() != null && row.closePrice() != null) {
        executions.add(
            new TradingStrategyBacktester.Execution(
                key,
                row.symbol(),
                lotId,
                TradingStrategyBacktester.Side.SELL,
                row.closeTime().toZonedDateTime(),
                row.quantity(),
                row.closePrice(),
                row.profit()));
      }
    }

    var barsByAsset = readTrustedBars(assets, from, horizon);
    Map<TradingStrategyBacktester.CampaignKey, List<TradingStrategyBacktester.DailyBar>> byKey =
        new LinkedHashMap<>();
    List<String> missing = new ArrayList<>();
    for (PositionRow row : positions) {
      if (!"USD".equals(row.priceCurrency())
          || !"USD".equals(row.profitCurrency())
          || !"BUY".equals(row.operation())) continue;
      var key = new TradingStrategyBacktester.CampaignKey(row.accountId(), row.assetId(), "USD");
      if (!barsByAsset.containsKey(row.assetId())) missing.add(row.symbol());
      else byKey.putIfAbsent(key, barsByAsset.get(row.assetId()));
    }
    return new Dataset(
        accounts,
        executions,
        byKey,
        baseline,
        passThrough,
        missing.stream().distinct().sorted().toList(),
        currencyExcluded,
        directionExcluded,
        horizon);
  }

  private Map<Long, List<TradingStrategyBacktester.DailyBar>> readTrustedBars(
      Set<Long> assetIds, LocalDate from, LocalDate horizon) {
    if (assetIds.isEmpty()) return Map.of();
    var args =
        new MapSqlParameterSource()
            .addValue("assetIds", assetIds)
            .addValue("from", from)
            .addValue("through", horizon)
            .addValue("sources", TRUSTED_SOURCES);
    List<PriceRow> rows =
        jdbc.query(
            """
            select distinct on (asset_id, price_date)
                   asset_id, a.symbol, price_date, open_price, high_price, low_price,
                   close_price, price_scale_factor
            from investory.asset_price_history h
            join investory.assets a on a.id = h.asset_id
            where asset_id in (:assetIds)
              and price_date between :from and :through
              and price_currency = 'USD'
              and source in (:sources)
              and quality_class in ('EXACT_LISTING_MARKET_CLOSE', 'EXACT_LISTING_SCALED', 'MANUAL_ACCEPTED')
              and is_observed and not estimated and not is_proxy
              and open_price is not null and high_price is not null
              and low_price is not null and close_price is not null
            order by asset_id, price_date, quality_score desc,
                     case source when 'YAHOO_FINANCE' then 0 when 'STOOQ' then 1
                       when 'TWELVE_DATA' then 2 else 3 end, imported_at desc
            """,
            args,
            (rs, row) ->
                new PriceRow(
                    rs.getLong("asset_id"),
                    rs.getString("symbol"),
                    rs.getDate("price_date").toLocalDate(),
                    scaled(rs, "open_price"),
                    scaled(rs, "high_price"),
                    scaled(rs, "low_price"),
                    scaled(rs, "close_price"),
                    rs.getBigDecimal("price_scale_factor")));
    Map<Long, List<TradingStrategyBacktester.DailyBar>> result = new LinkedHashMap<>();
    rows.forEach(
        row ->
            result
                .computeIfAbsent(row.assetId(), ignored -> new ArrayList<>())
                .add(
                    new TradingStrategyBacktester.DailyBar(
                        row.date(), row.open(), row.high(), row.low(), row.close())));
    return result;
  }

  private static BigDecimal scaled(ResultSet rs, String column) throws SQLException {
    return rs.getBigDecimal(column).multiply(rs.getBigDecimal("price_scale_factor"));
  }

  private static final RowMapper<PositionRow> POSITION_MAPPER =
      (rs, row) ->
          new PositionRow(
              rs.getLong("id"),
              rs.getLong("account_id"),
              rs.getLong("asset_id"),
              rs.getString("symbol"),
              rs.getString("operation"),
              rs.getString("price_currency"),
              rs.getString("profit_currency"),
              rs.getObject("open_time", OffsetDateTime.class),
              rs.getBigDecimal("volume"),
              rs.getBigDecimal("open_price"),
              rs.getObject("close_time", OffsetDateTime.class),
              rs.getBigDecimal("close_price"),
              rs.getBigDecimal("profit"));
}
