package com.example.demo.infrastructure.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface AssetPriceHistoryRepository extends Repository<Asset, Long> {

  @Query(
      value =
          """
          select a.symbol as symbol,
                 aph.price_date as priceDate,
                 aph.close_price as closePrice,
                 aph.price_currency as priceCurrency,
                 aph.quality_score as qualityScore
          from asset_price_history aph
          join assets a on a.id = aph.asset_id
          where a.symbol in (:symbols)
            and aph.price_date <= :dateTo
          order by a.symbol, aph.price_date, aph.quality_score desc
          """,
      nativeQuery = true)
  List<HistoricalAssetPriceRow> findHistoricalPricesBySymbolInBefore(
      @Param("symbols") Collection<String> symbols, @Param("dateTo") LocalDate dateTo);

  interface HistoricalAssetPriceRow {
    String getSymbol();

    LocalDate getPriceDate();

    Double getClosePrice();

    String getPriceCurrency();

    Integer getQualityScore();
  }
}
