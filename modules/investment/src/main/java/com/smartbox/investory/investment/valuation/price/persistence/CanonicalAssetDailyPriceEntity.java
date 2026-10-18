package com.smartbox.investory.investment.valuation.price.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.annotations.Immutable;

/** Read-only mapping for the canonical view; its key is unique by asset and price date. */
@Entity
@Immutable
@IdClass(CanonicalAssetDailyPriceId.class)
@Table(name = "app_v_canonical_asset_daily_price")
public class CanonicalAssetDailyPriceEntity {
  @Id
  @Column(name = "asset_id")
  private Long assetId;

  @Id
  @Column(name = "price_date")
  private LocalDate priceDate;

  @Column(name = "source")
  private String source;

  @Column(name = "source_symbol")
  private String sourceSymbol;

  @Column(name = "price_origin")
  private String priceOrigin;

  @Column(name = "price_currency")
  private String priceCurrency;

  @Column(name = "close_price")
  private BigDecimal closePrice;

  @Column(name = "quality_score")
  private Integer qualityScore;

  @Column(name = "quality_class")
  private String qualityClass;

  @Column(name = "imported_at")
  private java.time.OffsetDateTime importedAt;
}
