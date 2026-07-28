package com.example.demo.infrastructure.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface AssetPriceHistoryRepository extends Repository<Asset, Long> {

  @Query(
      value =
          """
          select a.symbol as symbol,
                 aph.price_date as priceDate,
                 aph.source_date as sourceDate,
                 aph.source as source,
                 aph.source_symbol as sourceSymbol,
                 aph.original_source_symbol as originalSourceSymbol,
                 aph.close_price as closePrice,
                 aph.price_currency as priceCurrency,
                 aph.price_scale_factor as priceScaleFactor,
                 aph.quality_score as qualityScore,
                 aph.quality_class as qualityClass,
                 aph.price_origin as priceOrigin
          from asset_price_history aph
          join assets a on a.id = aph.asset_id
          where a.symbol in (:symbols)
            and aph.price_date <= :dateTo
          order by a.symbol, aph.price_date, aph.quality_score desc, aph.imported_at desc, aph.source
          """,
      nativeQuery = true)
  List<HistoricalAssetPriceRow> findHistoricalPricesBySymbolInBefore(
      @Param("symbols") Collection<String> symbols, @Param("dateTo") LocalDate dateTo);

  @Modifying
  @Query(
      value =
          """
          insert into asset_price_history (
              asset_id,
              price_date,
              source,
              source_symbol,
              price_origin,
              price_currency,
              open_price,
              high_price,
              low_price,
              close_price,
              source_date,
              quality_score,
              quality_class,
              is_observed,
              is_proxy,
              price_scale_factor,
              original_source_symbol
          ) values (
              :assetId,
              :priceDate,
              'IBKR',
              :sourceSymbol,
              'IBKR_TRADE',
              :priceCurrency,
              :priceValue,
              :priceValue,
              :priceValue,
              :priceValue,
              :priceDate,
              90,
              'IBKR_TRADE_OBSERVATION',
              true,
              false,
              1,
              :originalSourceSymbol
          )
          on conflict (asset_id, price_date, source) do update
          set source_symbol = excluded.source_symbol,
              price_origin = excluded.price_origin,
              price_currency = excluded.price_currency,
              open_price = excluded.open_price,
              high_price = excluded.high_price,
              low_price = excluded.low_price,
              close_price = excluded.close_price,
              source_date = excluded.source_date,
              quality_score = excluded.quality_score,
              quality_class = excluded.quality_class,
              is_observed = excluded.is_observed,
              is_proxy = excluded.is_proxy,
              price_scale_factor = excluded.price_scale_factor,
              original_source_symbol = excluded.original_source_symbol
          where excluded.quality_score >= asset_price_history.quality_score
          """,
      nativeQuery = true)
  void upsertIbkrTradeObservation(
      @Param("assetId") Long assetId,
      @Param("priceDate") LocalDate priceDate,
      @Param("sourceSymbol") String sourceSymbol,
      @Param("originalSourceSymbol") String originalSourceSymbol,
      @Param("priceCurrency") String priceCurrency,
      @Param("priceValue") Double priceValue);

  @Modifying
  @Query(
      value =
          """
          insert into asset_price_history (
              asset_id,
              price_date,
              source,
              source_symbol,
              price_origin,
              price_currency,
              open_price,
              high_price,
              low_price,
              close_price,
              source_date,
              quality_score,
              quality_class,
              is_observed,
              is_proxy,
              price_scale_factor,
              original_source_symbol
          ) values (
              :assetId,
              :priceDate,
              :source,
              :sourceSymbol,
              :priceOrigin,
              :priceCurrency,
              :priceValue,
              :priceValue,
              :priceValue,
              :priceValue,
              :priceDate,
              :qualityScore,
              :qualityClass,
              true,
              false,
              1,
              :originalSourceSymbol
          )
          on conflict (asset_id, price_date, source) do update
          set source_symbol = excluded.source_symbol,
              price_origin = excluded.price_origin,
              price_currency = excluded.price_currency,
              open_price = excluded.open_price,
              high_price = excluded.high_price,
              low_price = excluded.low_price,
              close_price = excluded.close_price,
              source_date = excluded.source_date,
              quality_score = excluded.quality_score,
              quality_class = excluded.quality_class,
              is_observed = excluded.is_observed,
              is_proxy = excluded.is_proxy,
              price_scale_factor = excluded.price_scale_factor,
              original_source_symbol = excluded.original_source_symbol
          where excluded.quality_score >= asset_price_history.quality_score
          """,
      nativeQuery = true)
  void upsertObservedPrice(
      @Param("assetId") Long assetId,
      @Param("priceDate") LocalDate priceDate,
      @Param("source") String source,
      @Param("sourceSymbol") String sourceSymbol,
      @Param("originalSourceSymbol") String originalSourceSymbol,
      @Param("priceOrigin") String priceOrigin,
      @Param("priceCurrency") String priceCurrency,
      @Param("priceValue") Double priceValue,
      @Param("qualityScore") Integer qualityScore,
      @Param("qualityClass") String qualityClass);

  @Modifying
  @Query(
      value =
          """
          insert into asset_price_history (
              asset_id,
              price_date,
              source,
              source_symbol,
              price_origin,
              price_currency,
              open_price,
              high_price,
              low_price,
              close_price,
              source_date,
              quality_score,
              quality_class,
              is_observed,
              is_proxy,
              price_scale_factor,
              scale_reason,
              original_source_symbol
          ) values (
              :assetId,
              :priceDate,
              'CARRY_FORWARD',
              :sourceSymbol,
              'STALE_CARRY_FORWARD',
              :priceCurrency,
              :priceValue,
              :priceValue,
              :priceValue,
              :priceValue,
              :sourceDate,
              40,
              'STALE_CARRY_FORWARD',
              true,
              false,
              1,
              'Filled missing business-day gap from previous known price',
              :originalSourceSymbol
          )
          on conflict (asset_id, price_date, source) do update
          set source_symbol = excluded.source_symbol,
              price_origin = excluded.price_origin,
              price_currency = excluded.price_currency,
              open_price = excluded.open_price,
              high_price = excluded.high_price,
              low_price = excluded.low_price,
              close_price = excluded.close_price,
              source_date = excluded.source_date,
              quality_score = excluded.quality_score,
              quality_class = excluded.quality_class,
              is_observed = excluded.is_observed,
              is_proxy = excluded.is_proxy,
              price_scale_factor = excluded.price_scale_factor,
              scale_reason = excluded.scale_reason,
              original_source_symbol = excluded.original_source_symbol
          """,
      nativeQuery = true)
  void upsertCarryForwardPrice(
      @Param("assetId") Long assetId,
      @Param("priceDate") LocalDate priceDate,
      @Param("sourceDate") LocalDate sourceDate,
      @Param("sourceSymbol") String sourceSymbol,
      @Param("originalSourceSymbol") String originalSourceSymbol,
      @Param("priceCurrency") String priceCurrency,
      @Param("priceValue") Double priceValue);

  interface HistoricalAssetPriceRow {
    String getSymbol();

    LocalDate getPriceDate();

    LocalDate getSourceDate();

    String getSource();

    String getSourceSymbol();

    String getOriginalSourceSymbol();

    Double getClosePrice();

    String getPriceCurrency();

    Double getPriceScaleFactor();

    Integer getQualityScore();

    String getQualityClass();

    String getPriceOrigin();
  }
}
