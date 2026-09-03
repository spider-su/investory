package com.smartbox.investory.investment.ledger.position.persistence;

import com.smartbox.investory.investment.ledger.position.PositionQuantities;
import com.smartbox.investory.investment.ledger.position.PositionSettlementModel;
import com.smartbox.investory.investment.ledger.position.PositionType;
import com.smartbox.investory.shared.currency.CurrencyType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import lombok.*;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EqualsAndHashCode(of = "id")
@Table(name = "positions")
public class PositionEntity {
  @Id private Long id;

  @Column(name = "account_id", nullable = false)
  private Long account;

  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  @Formula(
      "coalesce((select asset.symbol from investory.assets asset where asset.id = asset_id), source_asset_symbol)")
  private String canonicalSymbol;

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

  public BigDecimal getVolume() {
    return volume;
  }

  public void setVolume(BigDecimal volume) {
    this.volume = scaleDecimal(volume);
  }

  public BigDecimal getOpenPrice() {
    return openPrice;
  }

  public void setOpenPrice(BigDecimal openPrice) {
    this.openPrice = scaleDecimal(openPrice);
  }

  public BigDecimal getSourceOpenPrice() {
    return sourceOpenPrice;
  }

  public void setSourceOpenPrice(BigDecimal sourceOpenPrice) {
    this.sourceOpenPrice = scaleDecimal(sourceOpenPrice);
  }

  public BigDecimal getOpenConversionRate() {
    return openConversionRate;
  }

  public BigDecimal getMarketPrice() {
    return marketPrice;
  }

  public void setMarketPrice(BigDecimal marketPrice) {
    this.marketPrice = scaleDecimal(marketPrice);
  }

  public void setOpenConversionRate(BigDecimal openConversionRate) {
    this.openConversionRate = scaleDecimal(openConversionRate);
  }

  public BigDecimal getClosePrice() {
    return closePrice;
  }

  public void setClosePrice(BigDecimal closePrice) {
    this.closePrice = scaleDecimal(closePrice);
  }

  public BigDecimal getSourceClosePrice() {
    return sourceClosePrice;
  }

  public void setSourceClosePrice(BigDecimal sourceClosePrice) {
    this.sourceClosePrice = scaleDecimal(sourceClosePrice);
  }

  public BigDecimal getCloseConversionRate() {
    return closeConversionRate;
  }

  public void setCloseConversionRate(BigDecimal closeConversionRate) {
    this.closeConversionRate = scaleDecimal(closeConversionRate);
  }

  public BigDecimal getPurchaseValue() {
    return purchaseValue;
  }

  public void setPurchaseValue(BigDecimal purchaseValue) {
    this.purchaseValue = scaleDecimal(purchaseValue);
  }

  public BigDecimal getSaleValue() {
    return saleValue;
  }

  public void setSaleValue(BigDecimal saleValue) {
    this.saleValue = scaleDecimal(saleValue);
  }

  public BigDecimal getMargin() {
    return margin;
  }

  public void setMargin(BigDecimal margin) {
    this.margin = scaleDecimal(margin);
  }

  public BigDecimal getCommission() {
    return commission;
  }

  public void setCommission(BigDecimal commission) {
    this.commission = scaleDecimal(commission);
  }

  public BigDecimal getSwap() {
    return swap;
  }

  public void setSwap(BigDecimal swap) {
    this.swap = scaleDecimal(swap);
  }

  public BigDecimal getProfit() {
    return profit;
  }

  public void setProfit(BigDecimal profit) {
    this.profit = scaleDecimal(profit);
  }

  public void setSymbol(String symbol) {
    this.symbol = symbol;
    if (this.sourceAssetSymbol == null) {
      this.sourceAssetSymbol = symbol;
    }
  }

  @PostLoad
  void loadCanonicalSymbol() {
    this.symbol = canonicalSymbol != null ? canonicalSymbol : sourceAssetSymbol;
  }

  public double signedQuantity() {
    return PositionQuantities.signed(type, getVolume().doubleValue());
  }

  private static Double asDouble(BigDecimal value) {
    return value == null ? null : value.doubleValue();
  }

  private static BigDecimal scaleDecimal(BigDecimal value) {
    return value == null ? null : value.setScale(8, RoundingMode.HALF_UP);
  }
}
