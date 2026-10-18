package com.smartbox.investory.investment.valuation.price.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Chart reads use JPQL over the canonical view. That view is unique by asset/date and already
 * applies canonical price provenance ranking, so the old chart-level DISTINCT ON did no selection
 * work; PostgreSQL-specific selection is not needed for this read.
 */
public interface AssetPriceChartRepository
    extends Repository<CanonicalAssetDailyPriceEntity, CanonicalAssetDailyPriceId> {

  @Query(
      """
      select aph.priceDate as priceDate,
             aph.closePrice as closePrice,
             aph.priceCurrency as priceCurrency,
             aph.source as source,
             aph.qualityScore as qualityScore,
             aph.qualityClass as qualityClass,
             aph.priceOrigin as priceOrigin
      from CanonicalAssetDailyPriceEntity aph
      where aph.assetId = :assetId
        and aph.priceDate >= coalesce(:dateFrom, aph.priceDate)
        and aph.priceDate <= :dateTo
        and aph.closePrice is not null
        and aph.closePrice > 0
      order by aph.priceDate
      """)
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
