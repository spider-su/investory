package com.smartbox.investory.infrastructure.repository.portfolio;

import com.smartbox.investory.infrastructure.CurrencyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Immutable
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
  private BigDecimal totalVolume;

  @Column(name = "market_price")
  @JdbcTypeCode(SqlTypes.NUMERIC)
  private BigDecimal marketPrice;

  @Enumerated(EnumType.STRING)
  @Column(name = "market_price_currency")
  private CurrencyType marketPriceCurrency;

  @Column(name = "cost_basis_in_base_currency")
  @JdbcTypeCode(SqlTypes.NUMERIC)
  private BigDecimal costBasisInBaseCurrency;

  @Column(name = "total_value_in_base_currency")
  @JdbcTypeCode(SqlTypes.NUMERIC)
  private BigDecimal totalValueInBaseCurrency;

  @Column(name = "unrealized_pl_in_base_currency")
  @JdbcTypeCode(SqlTypes.NUMERIC)
  private BigDecimal unrealizedPlInBaseCurrency;

  @Column(name = "updated_at", nullable = false)
  private java.time.ZonedDateTime updatedAt;

  public Double getTotalVolume() {
    return totalVolume == null ? null : totalVolume.doubleValue();
  }

  public Double getMarketPrice() {
    return marketPrice == null ? null : marketPrice.doubleValue();
  }

  public Double getCostBasisInBaseCurrency() {
    return costBasisInBaseCurrency == null ? null : costBasisInBaseCurrency.doubleValue();
  }

  public Double getTotalValueInBaseCurrency() {
    return totalValueInBaseCurrency == null ? null : totalValueInBaseCurrency.doubleValue();
  }

  public Double getUnrealizedPlInBaseCurrency() {
    return unrealizedPlInBaseCurrency == null ? null : unrealizedPlInBaseCurrency.doubleValue();
  }

  public void setTotalVolume(Double value) {
    totalVolume = value == null ? null : BigDecimal.valueOf(value);
  }

  public void setCostBasisInBaseCurrency(Double value) {
    costBasisInBaseCurrency = value == null ? null : BigDecimal.valueOf(value);
  }

  public void setTotalValueInBaseCurrency(Double value) {
    totalValueInBaseCurrency = value == null ? null : BigDecimal.valueOf(value);
  }

  public void setUnrealizedPlInBaseCurrency(Double value) {
    unrealizedPlInBaseCurrency = value == null ? null : BigDecimal.valueOf(value);
  }

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
    this.totalVolume = BigDecimal.valueOf(totalVolume);
    this.costBasisInBaseCurrency = BigDecimal.valueOf(costBasisInBaseCurrency);
    this.totalValueInBaseCurrency = BigDecimal.valueOf(totalValueInBaseCurrency);
    this.unrealizedPlInBaseCurrency = BigDecimal.valueOf(unrealizedPlInBaseCurrency);
    this.updatedAt = updatedAt;
  }
}
