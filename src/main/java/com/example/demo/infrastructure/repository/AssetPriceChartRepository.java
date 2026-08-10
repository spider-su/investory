package com.example.demo.infrastructure.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface AssetPriceChartRepository extends Repository<Asset, Long> {

  @Query(
      value =
          """
          select distinct on (aph.price_date)
                 aph.price_date as priceDate,
                 aph.close_price as closePrice,
                 aph.price_currency as priceCurrency,
                 aph.source as source,
                 aph.quality_score as qualityScore,
                 aph.quality_class as qualityClass,
                 aph.price_origin as priceOrigin
          from investory.v_canonical_asset_daily_price aph
          where aph.asset_id = :assetId
            and (cast(:dateFrom as date) is null or aph.price_date >= cast(:dateFrom as date))
            and aph.price_date <= :dateTo
            and aph.close_price is not null
            and aph.close_price > 0
          order by aph.price_date,
                   aph.quality_score desc,
                   aph.imported_at desc,
                   aph.source
          """,
      nativeQuery = true)
  List<AssetPriceChartRow> findBestPrices(
      @Param("assetId") Long assetId,
      @Param("dateFrom") LocalDate dateFrom,
      @Param("dateTo") LocalDate dateTo);

  interface AssetPriceChartRow {
    LocalDate getPriceDate();

    BigDecimal getClosePrice();

    String getPriceCurrency();

    String getSource();

    Integer getQualityScore();

    String getQualityClass();

    String getPriceOrigin();
  }
}
