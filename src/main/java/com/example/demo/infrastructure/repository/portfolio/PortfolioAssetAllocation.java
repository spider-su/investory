package com.example.demo.infrastructure.repository.portfolio;

import com.example.demo.infrastructure.CurrencyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@IdClass(PortfolioAssetAllocationId.class)
@Table(name = "app_v_portfolio_asset_allocation")
public class PortfolioAssetAllocation {

  @Id
  @Column(name = "portfolio_id", nullable = false)
  private Long portfolioId;

  @Enumerated(EnumType.STRING)
  @Column(name = "base_currency", nullable = false)
  private CurrencyType baseCurrency;

  @Id
  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  @Column(name = "asset_symbol")
  private String assetSymbol;

  @Column(name = "total_volume")
  @JdbcTypeCode(SqlTypes.NUMERIC)
  private Double totalVolume;

  @Column(name = "market_price")
  @JdbcTypeCode(SqlTypes.NUMERIC)
  private Double marketPrice;

  @Enumerated(EnumType.STRING)
  @Column(name = "market_price_currency")
  private CurrencyType marketPriceCurrency;

  @Column(name = "cost_basis_in_base_currency")
  @JdbcTypeCode(SqlTypes.NUMERIC)
  private Double costBasisInBaseCurrency;

  @Column(name = "total_value_in_base_currency")
  @JdbcTypeCode(SqlTypes.NUMERIC)
  private Double totalValueInBaseCurrency;

  @Column(name = "unrealized_pl_in_base_currency")
  @JdbcTypeCode(SqlTypes.NUMERIC)
  private Double unrealizedPlInBaseCurrency;

  @Column(name = "updated_at", nullable = false)
  private java.time.ZonedDateTime updatedAt;

  public PortfolioAssetAllocation(
      Long portfolioId,
      CurrencyType baseCurrency,
      String assetSymbol,
      double totalVolume,
      double costBasisInBaseCurrency,
      double totalValueInBaseCurrency,
      double unrealizedPlInBaseCurrency,
      java.time.ZonedDateTime updatedAt) {
    this.portfolioId = portfolioId;
    this.baseCurrency = baseCurrency;
    this.assetId = null;
    this.assetSymbol = assetSymbol;
    this.totalVolume = totalVolume;
    this.costBasisInBaseCurrency = costBasisInBaseCurrency;
    this.totalValueInBaseCurrency = totalValueInBaseCurrency;
    this.unrealizedPlInBaseCurrency = unrealizedPlInBaseCurrency;
    this.updatedAt = updatedAt;
  }
}
