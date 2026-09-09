package com.smartbox.investory.investment.valuation.price.persistence;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** PostgreSQL batch writer for observed import prices. */
@Service
@RequiredArgsConstructor
public class AssetPriceHistoryBatchWriter {
  private static final int BATCH_SIZE = 50;
  private final JdbcTemplate jdbcTemplate;

  public void upsertObserved(List<ObservedPrice> prices) {
    batch(OBSERVED_SQL, prices, this::setObserved);
  }

  public void upsertIbkr(List<IbkrPrice> prices) {
    batch(IBKR_SQL, prices, this::setIbkr);
  }

  private <T> void batch(String sql, List<T> rows, BatchSetter<T> setter) {
    for (int from = 0; from < rows.size(); from += BATCH_SIZE) {
      List<T> chunk = rows.subList(from, Math.min(from + BATCH_SIZE, rows.size()));
      jdbcTemplate.batchUpdate(
          sql,
          new BatchPreparedStatementSetter() {
            public void setValues(PreparedStatement ps, int i) throws SQLException {
              setter.set(ps, chunk.get(i));
            }

            public int getBatchSize() {
              return chunk.size();
            }
          });
    }
  }

  private void setObserved(PreparedStatement ps, ObservedPrice p) throws SQLException {
    ps.setLong(1, p.assetId());
    ps.setObject(2, p.priceDate());
    ps.setString(3, p.source());
    ps.setString(4, p.sourceSymbol());
    ps.setString(5, p.priceOrigin());
    ps.setString(6, p.priceCurrency());
    ps.setBigDecimal(7, p.priceValue());
    ps.setBigDecimal(8, p.priceValue());
    ps.setBigDecimal(9, p.priceValue());
    ps.setBigDecimal(10, p.priceValue());
    ps.setObject(11, p.priceDate());
    ps.setInt(12, p.qualityScore());
    ps.setString(13, p.qualityClass());
    ps.setString(14, p.originalSourceSymbol());
  }

  private void setIbkr(PreparedStatement ps, IbkrPrice p) throws SQLException {
    ps.setLong(1, p.assetId());
    ps.setObject(2, p.priceDate());
    ps.setString(3, p.sourceSymbol());
    ps.setString(4, p.priceCurrency());
    ps.setBigDecimal(5, p.priceValue());
    ps.setBigDecimal(6, p.priceValue());
    ps.setBigDecimal(7, p.priceValue());
    ps.setBigDecimal(8, p.priceValue());
    ps.setObject(9, p.priceDate());
    ps.setLong(10, p.assetId());
    ps.setString(11, p.originalSourceSymbol());
  }

  private static final String OBSERVED_SQL =
      """
      insert into investory.asset_price_history as aph
        (asset_id, price_date, source, source_symbol, price_origin, price_currency,
         open_price, high_price, low_price, close_price, source_date, quality_score,
         quality_class, is_observed, is_proxy, price_scale_factor, original_source_symbol)
      values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true, false, 1, ?)
      on conflict (asset_id, price_date, source) do update set
        source_symbol = excluded.source_symbol, price_origin = excluded.price_origin,
        price_currency = excluded.price_currency, open_price = excluded.open_price,
        high_price = excluded.high_price, low_price = excluded.low_price,
        close_price = excluded.close_price, source_date = excluded.source_date,
        quality_score = excluded.quality_score, quality_class = excluded.quality_class,
        is_observed = excluded.is_observed, is_proxy = excluded.is_proxy,
        price_scale_factor = excluded.price_scale_factor,
        original_source_symbol = excluded.original_source_symbol
      where excluded.quality_score >= aph.quality_score
      """;

  private static final String IBKR_SQL =
      """
      insert into investory.asset_price_history as aph
        (asset_id, price_date, source, source_symbol, price_origin, price_currency,
         open_price, high_price, low_price, close_price, source_date, quality_score,
         quality_class, is_observed, is_proxy, price_scale_factor, original_source_symbol)
      values (?, ?, 'IBKR', ?, 'IBKR_TRADE', ?, ?, ?, ?, ?, ?, 90,
         case when exists (select 1 from investory.assets where id = ? and asset_type = 'BOND')
              then 'IBKR_TRADE_OBSERVATION_PERCENT_OF_PAR'
              else 'IBKR_TRADE_OBSERVATION' end, true, false, 1, ?)
      on conflict (asset_id, price_date, source) do update set
        source_symbol = excluded.source_symbol, price_origin = excluded.price_origin,
        price_currency = excluded.price_currency, open_price = excluded.open_price,
        high_price = excluded.high_price, low_price = excluded.low_price,
        close_price = excluded.close_price, source_date = excluded.source_date,
        quality_score = excluded.quality_score, quality_class = excluded.quality_class,
        is_observed = excluded.is_observed, is_proxy = excluded.is_proxy,
        price_scale_factor = excluded.price_scale_factor,
        original_source_symbol = excluded.original_source_symbol
      where excluded.quality_score >= aph.quality_score
      """;

  private interface BatchSetter<T> {
    void set(PreparedStatement ps, T value) throws SQLException;
  }

  public record ObservedPrice(
      Long assetId,
      java.time.LocalDate priceDate,
      String source,
      String sourceSymbol,
      String originalSourceSymbol,
      String priceOrigin,
      String priceCurrency,
      java.math.BigDecimal priceValue,
      Integer qualityScore,
      String qualityClass) {}

  public record IbkrPrice(
      Long assetId,
      java.time.LocalDate priceDate,
      String sourceSymbol,
      String originalSourceSymbol,
      String priceCurrency,
      java.math.BigDecimal priceValue) {}
}
