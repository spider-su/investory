package com.smartbox.investory.infrastructure.repository;

import com.smartbox.investory.infrastructure.CurrencyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "assets")
public class Asset {

  @Id
  @SequenceGenerator(name = "assets_id_seq", sequenceName = "assets_id_seq", allocationSize = 50)
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "assets_id_seq")
  @Column(name = "id", updatable = false, nullable = false)
  private Long id;

  @Column(name = "name", nullable = false, length = 511)
  private String name;

  @Column(name = "symbol", nullable = false, length = 64)
  private String symbol;

  @Column(name = "ticker", nullable = false, length = 64)
  private String ticker;

  @Column(name = "ibkr", nullable = false, length = 64)
  private String ibkr;

  @jakarta.persistence.Transient private String ibrk;

  @Column(name = "yahoo", length = 64)
  private String yahoo;

  @Column(name = "country", nullable = false, length = 15)
  private String country;

  @Enumerated(EnumType.STRING)
  @Column(name = "currency", nullable = false, length = 3)
  private CurrencyType currency;

  @Column(name = "asset_type", nullable = false, length = 32)
  private String assetType;

  @Column(name = "isin", length = 12)
  private String isin;

  @Column(name = "figi", length = 16)
  private String figi;

  @Column(name = "exchange_mic", length = 4)
  private String exchangeMic;

  @Column(name = "active", nullable = false)
  @Builder.Default
  private boolean active = true;

  @Column(name = "exclude_from_import", nullable = false)
  @Builder.Default
  private Boolean excludeFromImport = false;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "market_price", precision = 20, scale = 8)
  private BigDecimal marketPrice;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "market_price_usd", precision = 20, scale = 8)
  private BigDecimal marketPriceUsd;

  @Column(name = "price_source")
  private String priceSource;

  @Column(name = "price_updated_at")
  private ZonedDateTime priceUpdatedAt;

  public void setIbrk(String value) {
    this.ibkr = value;
    this.ibrk = value;
  }

  public String getIbrk() {
    return ibkr;
  }

  public boolean getActive() {
    return active;
  }

  public Double getMarketPrice() {
    return asDouble(marketPrice);
  }

  public BigDecimal getMarketPriceValue() {
    return marketPrice;
  }

  public void setMarketPrice(BigDecimal marketPrice) {
    this.marketPrice = scaleDecimal(marketPrice);
  }

  public void setMarketPrice(Double marketPrice) {
    this.marketPrice = scaleDecimal(marketPrice);
  }

  public void setMarketPrice(double marketPrice) {
    setMarketPrice(Double.valueOf(marketPrice));
  }

  public Double getMarketPriceUsd() {
    return asDouble(marketPriceUsd);
  }

  public BigDecimal getMarketPriceUsdValue() {
    return marketPriceUsd;
  }

  public void setMarketPriceUsd(BigDecimal marketPriceUsd) {
    this.marketPriceUsd = scaleDecimal(marketPriceUsd);
  }

  public void setMarketPriceUsd(Double marketPriceUsd) {
    this.marketPriceUsd = scaleDecimal(marketPriceUsd);
  }

  public void setMarketPriceUsd(double marketPriceUsd) {
    setMarketPriceUsd(Double.valueOf(marketPriceUsd));
  }

  private static Double asDouble(BigDecimal value) {
    return value == null ? null : value.doubleValue();
  }

  private static BigDecimal scaleDecimal(BigDecimal value) {
    return value == null ? null : value.setScale(8, RoundingMode.HALF_UP);
  }

  private static BigDecimal scaleDecimal(Double value) {
    return value == null ? null : scaleDecimal(BigDecimal.valueOf(value));
  }
}
