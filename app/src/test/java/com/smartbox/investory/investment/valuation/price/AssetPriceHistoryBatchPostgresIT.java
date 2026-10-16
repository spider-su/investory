package com.smartbox.investory.investment.valuation.price;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.investment.valuation.price.persistence.AssetPriceChartRepository;
import com.smartbox.investory.investment.valuation.price.persistence.AssetPriceHistoryRepository;
import com.smartbox.investory.testsupport.FastDatabaseTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AssetPriceHistoryBatchPostgresIT extends FastDatabaseTest {

  private static final Logger LOG = LoggerFactory.getLogger(AssetPriceHistoryBatchPostgresIT.class);

  @Autowired private AssetPriceHistoryRepository history;
  @Autowired private AssetPriceChartRepository chart;
  @Autowired private JdbcTemplate jdbc;

  @Test
  @Transactional
  void chartOrmReadKeepsCanonicalPerDateWinnerAndInclusiveDateBounds() {
    jdbc.update(
        """
        insert into investory.asset_price_history (
            asset_id, price_date, source, source_symbol, price_origin, price_currency,
            open_price, high_price, low_price, close_price, source_date, quality_score,
            quality_class, is_observed, is_proxy, price_scale_factor, original_source_symbol, imported_at
        ) values
            (1001, date '2025-02-01', 'CHART_BEFORE', 'TSLA.US', 'STOOQ', 'USD', 9, 9, 9, 9,
             date '2025-02-01', 80, 'VERIFIED_ALTERNATE_LISTING', true, false, 1, 'TSLA.US', timestamptz '2025-02-02 10:00:00+00'),
            (1001, date '2025-02-02', 'CHART_LOWER_BOUND', 'TSLA.US', 'STOOQ', 'USD', 8, 8, 8, 8,
             date '2025-02-02', 80, 'VERIFIED_ALTERNATE_LISTING', true, false, 1, 'TSLA.US', timestamptz '2025-02-03 10:00:00+00'),
            (1001, date '2025-02-03', 'CHART_A', 'TSLA.US', 'STOOQ', 'USD', 10, 10, 10, 10,
             date '2025-02-03', 80, 'VERIFIED_ALTERNATE_LISTING', true, false, 1, 'TSLA.US', timestamptz '2025-02-04 10:00:00+00'),
            (1001, date '2025-02-03', 'CHART_B', 'TSLA.US', 'STOOQ', 'USD', 11, 11, 11, 11,
             date '2025-02-03', 80, 'VERIFIED_ALTERNATE_LISTING', true, false, 1, 'TSLA.US', timestamptz '2025-02-04 10:00:00+00'),
            (1001, date '2025-02-04', 'CHART_C', 'TSLA.US', 'STOOQ', 'USD', 12, 12, 12, 12,
             date '2025-02-04', 80, 'VERIFIED_ALTERNATE_LISTING', true, false, 1, 'TSLA.US', timestamptz '2025-02-05 10:00:00+00')
        on conflict (asset_id, price_date, source) do nothing
        """);
    jdbc.execute("refresh materialized view investory.app_v_canonical_asset_daily_price_mv");

    var rows = chart.findBestPrices(1001L, LocalDate.of(2025, 2, 2), LocalDate.of(2025, 2, 3));

    assertThat(rows).hasSize(2);
    assertThat(rows.getFirst().getPriceDate()).isEqualTo(LocalDate.of(2025, 2, 2));
    var selected = rows.get(1);
    assertThat(selected.getPriceDate()).isEqualTo(LocalDate.of(2025, 2, 3));
    assertThat(selected.getClosePrice()).isEqualByComparingTo("10");
    assertThat(selected.getSource()).isEqualTo("CHART_A");
    assertThat(selected.getQualityScore()).isEqualTo(80);
    assertThat(selected.getQualityClass()).isEqualTo("VERIFIED_ALTERNATE_LISTING");
    assertThat(selected.getPriceOrigin()).isEqualTo("STOOQ");
    assertThat(selected.getPriceCurrency()).isEqualTo("USD");
  }

  @Test
  @Transactional
  void boundedRangeReturnsOnlyWindowRowsAndOnePriorObservationAnchor() {
    jdbc.update(
        """
        insert into investory.asset_price_history (
            asset_id, price_date, source, source_symbol, price_origin, price_currency,
            open_price, high_price, low_price, close_price, source_date, quality_score,
            quality_class, is_observed, is_proxy, price_scale_factor, original_source_symbol
        )
        select 1001, series.day::date, 'FIXTURE', 'TSLA.US', 'STOOQ', 'USD',
               100, 100, 100, 100, series.day::date, 80,
               'VERIFIED_ALTERNATE_LISTING', true, false, 1, 'TSLA.US'
        from generate_series(date '2015-01-01', date '2024-12-31', interval '1 day') series(day)
        where extract(isodow from series.day) between 1 and 5
        on conflict (asset_id, price_date, source) do nothing
        """);
    jdbc.execute("refresh materialized view investory.app_v_canonical_asset_daily_price_mv");
    LocalDate from = LocalDate.of(2024, 12, 1);
    LocalDate to = LocalDate.of(2025, 1, 5);

    long baselineStarted = System.nanoTime();
    List<List<Object>> baselineRows =
        jdbc.query(
            """
            select aph.price_date, aph.source_date, aph.source, aph.source_symbol,
                   aph.original_source_symbol, aph.close_price, aph.price_currency,
                   aph.price_scale_factor, aph.quality_score, aph.quality_class,
                   aph.price_origin, aph.estimated, aph.interpolation_left_date,
                   aph.interpolation_right_date
            from investory.app_v_canonical_asset_daily_price aph
            join investory.assets a on a.id = aph.asset_id
            where a.exclude_from_import = false and a.symbol = 'TSLA.US' and aph.price_date <= ?
            order by a.symbol, aph.price_date, aph.quality_score desc, aph.imported_at desc, aph.source
            """,
            (rs, rowNum) ->
                Arrays.asList(
                    rs.getDate("price_date").toLocalDate(),
                    rs.getDate("source_date") == null
                        ? null
                        : rs.getDate("source_date").toLocalDate(),
                    rs.getString("source"),
                    rs.getString("source_symbol"),
                    rs.getString("original_source_symbol"),
                    rs.getBigDecimal("close_price"),
                    rs.getString("price_currency"),
                    rs.getBigDecimal("price_scale_factor"),
                    rs.getObject("quality_score"),
                    rs.getString("quality_class"),
                    rs.getString("price_origin"),
                    rs.getObject("estimated"),
                    rs.getDate("interpolation_left_date") == null
                        ? null
                        : rs.getDate("interpolation_left_date").toLocalDate(),
                    rs.getDate("interpolation_right_date") == null
                        ? null
                        : rs.getDate("interpolation_right_date").toLocalDate()),
            to);
    long baselineMillis = (System.nanoTime() - baselineStarted) / 1_000_000;

    long boundedStarted = System.nanoTime();
    var rows = history.findHistoricalPricesBySymbolInRange(List.of("TSLA.US"), from, to);
    long boundedMillis = (System.nanoTime() - boundedStarted) / 1_000_000;

    LocalDate anchorDate =
        baselineRows.stream()
            .map(row -> (LocalDate) row.get(0))
            .filter(date -> date.isBefore(from))
            .max(LocalDate::compareTo)
            .orElseThrow();
    List<List<Object>> expectedRows =
        baselineRows.stream()
            .filter(
                row -> !((LocalDate) row.get(0)).isBefore(from) || row.get(0).equals(anchorDate))
            .toList();
    assertThat(baselineRows).hasSizeGreaterThan(2_000);
    assertThat(rows).hasSizeLessThan(32);
    assertThat(rows.stream().map(AssetPriceHistoryBatchPostgresIT::fingerprint).toList())
        .containsExactlyElementsOf(expectedRows);
    LOG.info(
        "Price range fixture comparison: baselineRows={}, boundedRows={}, baselineMs={}, boundedMs={}",
        baselineRows.size(),
        rows.size(),
        baselineMillis,
        boundedMillis);
  }

  private static List<Object> fingerprint(AssetPriceHistoryRepository.HistoricalAssetPriceRow row) {
    List<Object> result = new ArrayList<>();
    result.add(row.getPriceDate());
    result.add(row.getSourceDate());
    result.add(row.getSource());
    result.add(row.getSourceSymbol());
    result.add(row.getOriginalSourceSymbol());
    result.add(row.getClosePrice());
    result.add(row.getPriceCurrency());
    result.add(row.getPriceScaleFactor());
    result.add(row.getQualityScore());
    result.add(row.getQualityClass());
    result.add(row.getPriceOrigin());
    result.add(row.getEstimated());
    result.add(row.getInterpolationLeftDate());
    result.add(row.getInterpolationRightDate());
    return result;
  }

  @Test
  @Transactional
  void setBasedCarryForwardUpsertPreservesMetadataAndIsIdempotent() {
    String payload =
        """
        [{"asset_id":1001,"price_date":"2025-01-06","source_date":"2025-01-03",
          "source_symbol":"TSLA.US","original_source_symbol":"TSLA.US","price_currency":"USD",
          "price_value":403.84,"price_scale_factor":1,"scale_reason":"VERIFIED_ALTERNATE_LISTING"}]
        """;

    history.upsertCarryForwardPrices(payload);
    history.upsertCarryForwardPrices(payload);

    assertThat(
            jdbc.queryForObject(
                "select count(*) from investory.asset_price_history where asset_id = 1001 and price_date = date '2025-01-06' and source = 'CARRY_FORWARD'",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select close_price from investory.asset_price_history where asset_id = 1001 and price_date = date '2025-01-06' and source = 'CARRY_FORWARD'",
                BigDecimal.class))
        .isEqualByComparingTo("403.84");
    assertThat(
            jdbc.queryForObject(
                    "select source_date from investory.asset_price_history where asset_id = 1001 and price_date = date '2025-01-06' and source = 'CARRY_FORWARD'",
                    java.sql.Date.class)
                .toLocalDate())
        .isEqualTo(LocalDate.of(2025, 1, 3));
    assertThat(
            jdbc.queryForObject(
                "select quality_class from investory.asset_price_history where asset_id = 1001 and price_date = date '2025-01-06' and source = 'CARRY_FORWARD'",
                String.class))
        .isEqualTo("STALE_CARRY_FORWARD");
    assertThat(
            jdbc.queryForObject(
                "select original_source_symbol from investory.asset_price_history where asset_id = 1001 and price_date = date '2025-01-06' and source = 'CARRY_FORWARD'",
                String.class))
        .isEqualTo("TSLA.US");
  }
}
