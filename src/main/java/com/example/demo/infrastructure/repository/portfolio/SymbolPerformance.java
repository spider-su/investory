package com.example.demo.infrastructure.repository.portfolio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@IdClass(SymbolPerformanceId.class)
@Table(name = "symbol_performance")
public class SymbolPerformance {

  @Id
  @Column(name = "portfolio_id", nullable = false)
  private Long portfolioId;

  @Id
  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  @Column(name = "symbol", nullable = false)
  private String symbol;

  @Column(name = "closed_profit", nullable = false)
  private Double closedProfit;

  @Column(name = "unrealized_profit", nullable = false)
  private Double unrealizedProfit;

  @Column(name = "total_profit", nullable = false)
  private Double totalProfit;

  @Column(name = "dividends", nullable = false)
  private Double dividends;

  @Column(name = "withholding_tax", nullable = false)
  private Double withholdingTax;

  @Column(name = "total_volume", nullable = false)
  private Double totalVolume;

  @Column(name = "cost_basis", nullable = false)
  private Double costBasis;

  @Column(name = "market_value", nullable = false)
  private Double marketValue;

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
    this.closedProfit = closedProfit;
    this.unrealizedProfit = unrealizedProfit;
    this.totalProfit = totalProfit;
    this.dividends = dividends;
    this.withholdingTax = withholdingTax;
    this.totalVolume = totalVolume;
    this.costBasis = costBasis;
    this.marketValue = marketValue;
    this.updatedAt = updatedAt;
  }
}
