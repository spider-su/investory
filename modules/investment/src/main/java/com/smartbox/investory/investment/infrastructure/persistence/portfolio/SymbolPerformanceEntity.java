package com.smartbox.investory.investment.infrastructure.persistence.portfolio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Immutable
@IdClass(SymbolPerformanceId.class)
@Table(name = "app_v_symbol_performance")
public class SymbolPerformanceEntity {

  @Id
  @Column(name = "portfolio_id", nullable = false)
  private Long portfolioId;

  @Id
  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  @Column(name = "symbol", nullable = false)
  private String symbol;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "closed_profit", nullable = false, precision = 20, scale = 8)
  private BigDecimal closedProfit;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "unrealized_profit", nullable = false, precision = 20, scale = 8)
  private BigDecimal unrealizedProfit;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "total_profit", nullable = false, precision = 20, scale = 8)
  private BigDecimal totalProfit;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "dividends", nullable = false, precision = 20, scale = 8)
  private BigDecimal dividends;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "withholding_tax", nullable = false, precision = 20, scale = 8)
  private BigDecimal withholdingTax;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "total_volume", nullable = false, precision = 20, scale = 8)
  private BigDecimal totalVolume;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "cost_basis", nullable = false, precision = 20, scale = 8)
  private BigDecimal costBasis;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "market_value", nullable = false, precision = 20, scale = 8)
  private BigDecimal marketValue;

  @Column(name = "updated_at", nullable = false)
  private ZonedDateTime updatedAt;

  public SymbolPerformanceEntity(
      String symbol,
      double closedProfit,
      double unrealizedProfit,
      double totalProfit,
      double dividends,
      double withholdingTax,
      double totalVolume,
      double costBasis,
      double marketValue,
      ZonedDateTime updatedAt) {
    this.portfolioId = null;
    this.assetId = null;
    this.symbol = symbol;
    this.closedProfit = scaleDecimal(closedProfit);
    this.unrealizedProfit = scaleDecimal(unrealizedProfit);
    this.totalProfit = scaleDecimal(totalProfit);
    this.dividends = scaleDecimal(dividends);
    this.withholdingTax = scaleDecimal(withholdingTax);
    this.totalVolume = scaleDecimal(totalVolume);
    this.costBasis = scaleDecimal(costBasis);
    this.marketValue = scaleDecimal(marketValue);
    this.updatedAt = updatedAt;
  }

  public BigDecimal getClosedProfit() {
    return closedProfit;
  }

  public void setClosedProfit(BigDecimal closedProfit) {
    this.closedProfit = scaleDecimal(closedProfit);
  }

  public BigDecimal getUnrealizedProfit() {
    return unrealizedProfit;
  }

  public void setUnrealizedProfit(BigDecimal unrealizedProfit) {
    this.unrealizedProfit = scaleDecimal(unrealizedProfit);
  }

  public BigDecimal getTotalProfit() {
    return totalProfit;
  }

  public void setTotalProfit(BigDecimal totalProfit) {
    this.totalProfit = scaleDecimal(totalProfit);
  }

  public BigDecimal getDividends() {
    return dividends;
  }

  public void setDividends(BigDecimal dividends) {
    this.dividends = scaleDecimal(dividends);
  }

  public BigDecimal getWithholdingTax() {
    return withholdingTax;
  }

  public void setWithholdingTax(BigDecimal withholdingTax) {
    this.withholdingTax = scaleDecimal(withholdingTax);
  }

  public BigDecimal getTotalVolume() {
    return totalVolume;
  }

  public void setTotalVolume(BigDecimal totalVolume) {
    this.totalVolume = scaleDecimal(totalVolume);
  }

  public BigDecimal getCostBasis() {
    return costBasis;
  }

  public void setCostBasis(BigDecimal costBasis) {
    this.costBasis = scaleDecimal(costBasis);
  }

  public BigDecimal getMarketValue() {
    return marketValue;
  }

  public void setMarketValue(BigDecimal marketValue) {
    this.marketValue = scaleDecimal(marketValue);
  }

  private static BigDecimal scaleDecimal(BigDecimal value) {
    return value == null ? null : value.setScale(8, RoundingMode.HALF_UP);
  }

  private static BigDecimal scaleDecimal(double value) {
    return scaleDecimal(BigDecimal.valueOf(value));
  }

  private static Double asDouble(BigDecimal value) {
    return value == null ? null : value.doubleValue();
  }
}
