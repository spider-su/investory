package com.smartbox.investory.infrastructure.repository;

import com.smartbox.investory.infrastructure.CurrencyType;
import com.smartbox.investory.infrastructure.PositionQuantities;
import com.smartbox.investory.infrastructure.PositionSettlementModel;
import com.smartbox.investory.infrastructure.PositionType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EqualsAndHashCode(of = "id")
@Table(name = "positions")
public class Position {
  @Id private Long id;

  @Column(name = "account_id", nullable = false)
  private Long account;

  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "asset_id", insertable = false, updatable = false)
  private Asset asset;

  @Column(name = "source_asset_symbol", nullable = false, length = 128)
  private String sourceAssetSymbol;

  @Column(name = "broker_symbol", length = 128)
  private String brokerSymbol;

  @Column(name = "source_position_id", length = 128)
  private String sourcePositionId;

  @Builder.Default
  @Column(name = "source_row_occurrence", nullable = false)
  private Integer sourceRowOccurrence = 1;

  /** Nullable for legacy/manual rows; mandatory for rows created by broker import orchestration. */
  @Column(name = "import_history_id")
  private Long importHistoryId;

  @Column(name = "import_source_row_id")
  private Long importSourceRowId;

  @Transient private String symbol;

  @Enumerated(value = EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "operation", nullable = false, columnDefinition = "positions_operation_type")
  private PositionType type;

  @Builder.Default
  @Enumerated(value = EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(
      name = "settlement_model",
      nullable = false,
      columnDefinition = "position_settlement_model")
  private PositionSettlementModel settlementModel = PositionSettlementModel.CASH_SETTLED;

  @Enumerated(value = EnumType.STRING)
  @Column(name = "profit_currency", nullable = false, length = 3)
  private CurrencyType profitCurrency;

  @Enumerated(value = EnumType.STRING)
  @Column(name = "price_currency", nullable = false, length = 3)
  private CurrencyType priceCurrency;

  @Enumerated(value = EnumType.STRING)
  @Column(name = "cost_currency", nullable = false, length = 3)
  private CurrencyType costCurrency;

  @Enumerated(value = EnumType.STRING)
  @Column(name = "commission_currency", nullable = false, length = 3)
  private CurrencyType commissionCurrency;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(nullable = false, precision = 20, scale = 8)
  private BigDecimal volume;

  @Column(name = "open_time", nullable = false)
  private ZonedDateTime openTime;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(nullable = false, precision = 20, scale = 8)
  private BigDecimal openPrice;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "source_open_price", precision = 20, scale = 8)
  private BigDecimal sourceOpenPrice;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "open_conversion_rate", precision = 20, scale = 8)
  private BigDecimal openConversionRate;

  @Transient
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private BigDecimal marketPrice;

  @Column(name = "close_time")
  private ZonedDateTime closeTime;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "close_price", precision = 20, scale = 8)
  private BigDecimal closePrice;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "source_close_price", precision = 20, scale = 8)
  private BigDecimal sourceClosePrice;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "close_conversion_rate", precision = 20, scale = 8)
  private BigDecimal closeConversionRate;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "purchase_value", precision = 20, scale = 8)
  private BigDecimal purchaseValue;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "sale_value", precision = 20, scale = 8)
  private BigDecimal saleValue;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(precision = 20, scale = 8)
  private BigDecimal margin;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(precision = 20, scale = 8)
  private BigDecimal commission;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(precision = 20, scale = 8)
  private BigDecimal swap;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(precision = 20, scale = 8)
  private BigDecimal profit;

  @Column(name = "broker_product", length = 255)
  private String brokerProduct;

  @Transient private String comment;

  @Column(name = "base_value", precision = 20, scale = 8)
  private BigDecimal baseValue;

  public String getComment() {
    return brokerProduct;
  }

  public void setComment(String comment) {
    this.comment = comment;
    this.brokerProduct = comment;
  }

  public Double getVolume() {
    return asDouble(volume);
  }

  public BigDecimal getVolumeValue() {
    return volume;
  }

  public void setVolume(BigDecimal volume) {
    this.volume = scaleDecimal(volume);
  }

  public void setVolume(Double volume) {
    this.volume = scaleDecimal(volume);
  }

  public void setVolume(double volume) {
    setVolume(Double.valueOf(volume));
  }

  public Double getOpenPrice() {
    return asDouble(openPrice);
  }

  public BigDecimal getOpenPriceValue() {
    return openPrice;
  }

  public void setOpenPrice(BigDecimal openPrice) {
    this.openPrice = scaleDecimal(openPrice);
  }

  public void setOpenPrice(Double openPrice) {
    this.openPrice = scaleDecimal(openPrice);
  }

  public void setOpenPrice(double openPrice) {
    setOpenPrice(Double.valueOf(openPrice));
  }

  public Double getSourceOpenPrice() {
    return asDouble(sourceOpenPrice);
  }

  public BigDecimal getSourceOpenPriceValue() {
    return sourceOpenPrice;
  }

  public void setSourceOpenPrice(BigDecimal sourceOpenPrice) {
    this.sourceOpenPrice = scaleDecimal(sourceOpenPrice);
  }

  public void setSourceOpenPrice(Double sourceOpenPrice) {
    this.sourceOpenPrice = scaleDecimal(sourceOpenPrice);
  }

  public void setSourceOpenPrice(double sourceOpenPrice) {
    setSourceOpenPrice(Double.valueOf(sourceOpenPrice));
  }

  public Double getOpenConversionRate() {
    return asDouble(openConversionRate);
  }

  public BigDecimal getOpenConversionRateValue() {
    return openConversionRate;
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

  public void setOpenConversionRate(BigDecimal openConversionRate) {
    this.openConversionRate = scaleDecimal(openConversionRate);
  }

  public void setOpenConversionRate(Double openConversionRate) {
    this.openConversionRate = scaleDecimal(openConversionRate);
  }

  public void setOpenConversionRate(double openConversionRate) {
    setOpenConversionRate(Double.valueOf(openConversionRate));
  }

  public Double getClosePrice() {
    return asDouble(closePrice);
  }

  public BigDecimal getClosePriceValue() {
    return closePrice;
  }

  public void setClosePrice(BigDecimal closePrice) {
    this.closePrice = scaleDecimal(closePrice);
  }

  public void setClosePrice(Double closePrice) {
    this.closePrice = scaleDecimal(closePrice);
  }

  public void setClosePrice(double closePrice) {
    setClosePrice(Double.valueOf(closePrice));
  }

  public Double getSourceClosePrice() {
    return asDouble(sourceClosePrice);
  }

  public BigDecimal getSourceClosePriceValue() {
    return sourceClosePrice;
  }

  public void setSourceClosePrice(BigDecimal sourceClosePrice) {
    this.sourceClosePrice = scaleDecimal(sourceClosePrice);
  }

  public void setSourceClosePrice(Double sourceClosePrice) {
    this.sourceClosePrice = scaleDecimal(sourceClosePrice);
  }

  public void setSourceClosePrice(double sourceClosePrice) {
    setSourceClosePrice(Double.valueOf(sourceClosePrice));
  }

  public Double getCloseConversionRate() {
    return asDouble(closeConversionRate);
  }

  public BigDecimal getCloseConversionRateValue() {
    return closeConversionRate;
  }

  public void setCloseConversionRate(BigDecimal closeConversionRate) {
    this.closeConversionRate = scaleDecimal(closeConversionRate);
  }

  public void setCloseConversionRate(Double closeConversionRate) {
    this.closeConversionRate = scaleDecimal(closeConversionRate);
  }

  public void setCloseConversionRate(double closeConversionRate) {
    setCloseConversionRate(Double.valueOf(closeConversionRate));
  }

  public Double getPurchaseValue() {
    return asDouble(purchaseValue);
  }

  public BigDecimal getPurchaseValueValue() {
    return purchaseValue;
  }

  public void setPurchaseValue(BigDecimal purchaseValue) {
    this.purchaseValue = scaleDecimal(purchaseValue);
  }

  public void setPurchaseValue(Double purchaseValue) {
    this.purchaseValue = scaleDecimal(purchaseValue);
  }

  public void setPurchaseValue(double purchaseValue) {
    setPurchaseValue(Double.valueOf(purchaseValue));
  }

  public Double getSaleValue() {
    return asDouble(saleValue);
  }

  public BigDecimal getSaleValueValue() {
    return saleValue;
  }

  public void setSaleValue(BigDecimal saleValue) {
    this.saleValue = scaleDecimal(saleValue);
  }

  public void setSaleValue(Double saleValue) {
    this.saleValue = scaleDecimal(saleValue);
  }

  public void setSaleValue(double saleValue) {
    setSaleValue(Double.valueOf(saleValue));
  }

  public Double getMargin() {
    return asDouble(margin);
  }

  public BigDecimal getMarginValue() {
    return margin;
  }

  public void setMargin(BigDecimal margin) {
    this.margin = scaleDecimal(margin);
  }

  public void setMargin(Double margin) {
    this.margin = scaleDecimal(margin);
  }

  public void setMargin(double margin) {
    setMargin(Double.valueOf(margin));
  }

  public Double getCommission() {
    return asDouble(commission);
  }

  public BigDecimal getCommissionValue() {
    return commission;
  }

  public void setCommission(BigDecimal commission) {
    this.commission = scaleDecimal(commission);
  }

  public void setCommission(Double commission) {
    this.commission = scaleDecimal(commission);
  }

  public void setCommission(double commission) {
    setCommission(Double.valueOf(commission));
  }

  public Double getSwap() {
    return asDouble(swap);
  }

  public BigDecimal getSwapValue() {
    return swap;
  }

  public void setSwap(BigDecimal swap) {
    this.swap = scaleDecimal(swap);
  }

  public void setSwap(Double swap) {
    this.swap = scaleDecimal(swap);
  }

  public void setSwap(double swap) {
    setSwap(Double.valueOf(swap));
  }

  public Double getProfit() {
    return asDouble(profit);
  }

  public BigDecimal getProfitValue() {
    return profit;
  }

  public void setProfit(BigDecimal profit) {
    this.profit = scaleDecimal(profit);
  }

  public void setProfit(Double profit) {
    this.profit = scaleDecimal(profit);
  }

  public void setProfit(double profit) {
    setProfit(Double.valueOf(profit));
  }

  public void setSymbol(String symbol) {
    this.symbol = symbol;
    if (this.sourceAssetSymbol == null) {
      this.sourceAssetSymbol = symbol;
    }
  }

  @PostLoad
  void loadCanonicalSymbol() {
    this.symbol = asset != null ? asset.getSymbol() : sourceAssetSymbol;
  }

  public double signedQuantity() {
    return PositionQuantities.signed(type, getVolume());
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
