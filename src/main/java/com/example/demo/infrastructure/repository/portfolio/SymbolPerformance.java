package com.example.demo.infrastructure.repository.portfolio;

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
public class SymbolPerformance {

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

  public SymbolPerformance(
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

  public Double getClosedProfit() {
    return asDouble(closedProfit);
  }

  public void setClosedProfit(BigDecimal closedProfit) {
    this.closedProfit = scaleDecimal(closedProfit);
  }

  public void setClosedProfit(Double closedProfit) {
    this.closedProfit = scaleDecimal(closedProfit);
  }

  public void setClosedProfit(double closedProfit) {
    this.closedProfit = scaleDecimal(closedProfit);
  }

  public Double getUnrealizedProfit() {
    return asDouble(unrealizedProfit);
  }

  public void setUnrealizedProfit(BigDecimal unrealizedProfit) {
    this.unrealizedProfit = scaleDecimal(unrealizedProfit);
  }

  public void setUnrealizedProfit(Double unrealizedProfit) {
    this.unrealizedProfit = scaleDecimal(unrealizedProfit);
  }

  public void setUnrealizedProfit(double unrealizedProfit) {
    this.unrealizedProfit = scaleDecimal(unrealizedProfit);
  }

  public Double getTotalProfit() {
    return asDouble(totalProfit);
  }

  public void setTotalProfit(BigDecimal totalProfit) {
    this.totalProfit = scaleDecimal(totalProfit);
  }

  public void setTotalProfit(Double totalProfit) {
    this.totalProfit = scaleDecimal(totalProfit);
  }

  public void setTotalProfit(double totalProfit) {
    this.totalProfit = scaleDecimal(totalProfit);
  }

  public Double getDividends() {
    return asDouble(dividends);
  }

  public void setDividends(BigDecimal dividends) {
    this.dividends = scaleDecimal(dividends);
  }

  public void setDividends(Double dividends) {
    this.dividends = scaleDecimal(dividends);
  }

  public void setDividends(double dividends) {
    this.dividends = scaleDecimal(dividends);
  }

  public Double getWithholdingTax() {
    return asDouble(withholdingTax);
  }

  public void setWithholdingTax(BigDecimal withholdingTax) {
    this.withholdingTax = scaleDecimal(withholdingTax);
  }

  public void setWithholdingTax(Double withholdingTax) {
    this.withholdingTax = scaleDecimal(withholdingTax);
  }

  public void setWithholdingTax(double withholdingTax) {
    this.withholdingTax = scaleDecimal(withholdingTax);
  }

  public Double getTotalVolume() {
    return asDouble(totalVolume);
  }

  public void setTotalVolume(BigDecimal totalVolume) {
    this.totalVolume = scaleDecimal(totalVolume);
  }

  public void setTotalVolume(Double totalVolume) {
    this.totalVolume = scaleDecimal(totalVolume);
  }

  public void setTotalVolume(double totalVolume) {
    this.totalVolume = scaleDecimal(totalVolume);
  }

  public Double getCostBasis() {
    return asDouble(costBasis);
  }

  public void setCostBasis(BigDecimal costBasis) {
    this.costBasis = scaleDecimal(costBasis);
  }

  public void setCostBasis(Double costBasis) {
    this.costBasis = scaleDecimal(costBasis);
  }

  public void setCostBasis(double costBasis) {
    this.costBasis = scaleDecimal(costBasis);
  }

  public Double getMarketValue() {
    return asDouble(marketValue);
  }

  public void setMarketValue(BigDecimal marketValue) {
    this.marketValue = scaleDecimal(marketValue);
  }

  public void setMarketValue(Double marketValue) {
    this.marketValue = scaleDecimal(marketValue);
  }

  public void setMarketValue(double marketValue) {
    this.marketValue = scaleDecimal(marketValue);
  }

  private static BigDecimal scaleDecimal(BigDecimal value) {
    return value == null ? null : value.setScale(8, RoundingMode.HALF_UP);
  }

  private static BigDecimal scaleDecimal(Double value) {
    return value == null ? null : scaleDecimal(BigDecimal.valueOf(value));
  }

  private static BigDecimal scaleDecimal(double value) {
    return scaleDecimal(BigDecimal.valueOf(value));
  }

  private static Double asDouble(BigDecimal value) {
    return value == null ? null : value.doubleValue();
  }
}
